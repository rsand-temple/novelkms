import { useEffect, useRef, useCallback, useState, useMemo } from 'react';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useEditor, EditorContent } from '@tiptap/react';
import { DOMSerializer } from '@tiptap/pm/model';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import { ResizableImage } from '../../extensions/ResizableImage.jsx';
import { Box, Typography, CircularProgress, Snackbar, Alert } from '@mui/material';

import { StyledParagraph } from '../../extensions/StyledParagraph';
import { FontSize } from '../../extensions/FontSize';
import { SceneBreak } from '../../extensions/SceneBreak';
import { DraftHeading } from '../../extensions/DraftHeading';
import { TemplateToken } from '../../extensions/TemplateToken';
import { SearchHighlight, searchHighlightKey } from '../../extensions/SearchHighlight';
import { ReviewHighlight } from '../../extensions/ReviewHighlight';
import TiptapTypography from '@tiptap/extension-typography';
import { useProjectSettings } from '../../hooks/useProjectSettings';
import { useScenes, useScene, useDeleteScene, SCENE_KEYS } from '../../hooks/useScenes';
import { useDraftDocument, flattenDraftScenes } from '../../hooks/useDraftDocument';
import { useChapter } from '../../hooks/useChapters';
import { useCodexType } from '../../hooks/useCodex';
import { useGlobalTemplate, useBookTemplate, TEMPLATE_KEYS } from '../../hooks/useTemplates';
import { useBook } from '../../hooks/useBooks';
import client from '../../api/client';
import { useProject } from '../../hooks/useProjects';
import { useGlobalStyles, useBookStyles } from '../../hooks/useStyles';
import { scenesApi } from '../../api/scenes';
import { templatesApi } from '../../api/templates';
import { chapterMemoryApi } from '../../api/chapterMemory';
import { summaryApi } from '../../api/summary';
import { editorialApi } from '../../api/editorial';
import {
	useChapterMemoryVariants, useChapterMemoryStatus,
	useGenerateChapterMemory, useDeleteChapterMemory, CHAPTER_MEMORY_KEYS,
} from '../../hooks/useChapterMemory';
import {
	useChapterSummaryVariants, useBookChapterSummaries,
	useBookSummaryVariants, useBookSummaryStatus,
	useGenerateChapterSummary, useDeleteChapterSummary,
	useGenerateBookSummary, useDeleteBookSummary, SUMMARY_KEYS,
} from '../../hooks/useSummary';
import {
	useChapterEditorialVariants,
	useGenerateChapterEditorial, useDeleteChapterEditorial, EDITORIAL_KEYS,
} from '../../hooks/useEditorial';
import { useAiCredentials } from '../../hooks/useAiCredentials';
import { providerLabel } from '../ai/aiProviders';
import { flaggedPreceding, formatTime as formatMemoryTime, stateColor as memoryStateColor, stateExplanation as memoryStateExplanation, stateLabel as memoryStateLabel } from '../ai/memoryStatus';
import { flaggedChapters, stateColor as summaryStateColor, stateExplanation as summaryStateExplanation, stateLabel as summaryStateLabel } from '../ai/summaryStatus';
import PreReviewMemoryDialog from '../ai/PreReviewMemoryDialog';
import PreBookSummaryDialog from '../ai/PreBookSummaryDialog';
import RegenerateConfirmDialog from '../ai/RegenerateConfirmDialog';
import { resolveValues, renderPreviewHtml, tokensForType } from '../../utils/tokenUtils';
import { buildStyleSx } from '../../utils/styles';
import { derivePageConfig } from '../../utils/pageConfig';
import EditorToolbar from '../editor/EditorToolbar';
import SplitSceneDialog from '../nav/dialogs/SplitSceneDialog';
import SearchBar from '../search/SearchBar';
import { useSearch } from '../../search/SearchContext';
import { useReview } from '../../review/ReviewContext';
import ReviewRail from '../ai/ReviewRail';
import { countHtmlOccurrences } from '../../search/searchUtils';
import BookCoverPreview from '../editor/BookCoverPreview';
import PartPagePreview from '../editor/PartPagePreview';
import ProjectShelf from '../editor/ProjectShelf';
import CodexEntryFields from '../codex/CodexEntryFields';
import { generateDefaultSceneTitle } from '../../utils/sceneTitles';
import { alpha } from '@mui/material/styles'

const AUTOSAVE_DELAY_MS = 1500;

const isEditorReady = (ed) =>
	Boolean(ed && !ed.isDestroyed && ed.view);

const runEditorCommand = (ed, fn) => {
	if (!isEditorReady(ed)) return false;
	fn(ed);
	return true;
};

// ── Review-rail resize ────────────────────────────────────────────────────────
const RAIL_STORAGE_KEY = 'novelkms.reviewRailWidth';
const DEFAULT_RAIL_WIDTH = 332;
const MIN_RAIL_WIDTH = 240;
const MAX_RAIL_WIDTH = 600;
const clampRail = (v) => Math.min(Math.max(v, MIN_RAIL_WIDTH), MAX_RAIL_WIDTH);

// Fallback page dimensions used when the book record hasn't loaded yet, or
// when the book has no page layout configured.  6" × 9" Trade Paperback at
// 96 DPI is a neutral default that gives a recognisable page shape without
// requiring the author to enable page layout first. Used only for the book
// cover / part page PREVIEW CANVAS — see EXPORT_FALLBACK_PAGE_CONFIG below
// for the estimated-page-count fallback, which must match export instead.
const DEFAULT_PAGE_CONFIG = {
	widthPx: 576,  // 6.0" × 96 dpi
	heightPx: 864,  // 9.0" × 96 dpi
	marginTopPx: 96,  // 1.0"
	marginBottomPx: 96,  // 1.0"
	marginInnerPx: 120,  // 1.25"
	marginOuterPx: 96,  // 1.0"
};

// Fallback page dimensions for the estimated page count specifically, used
// when the book has no page layout configured. Must mirror ExportService's
// actual disabled-layout default (Letter, 1" top/bottom/outer margins, 1.25"
// inner margin — see DEFAULT_WIDTH_IN etc. in ExportService.java), NOT
// DEFAULT_PAGE_CONFIG above: that one is a 6"×9" Trade Paperback shape chosen
// only to make the preview canvas look like a recognisable page before page
// layout is configured, and using it here would understate the usable page
// area and badly overstate the estimate against what DOCX export produces.
const EXPORT_FALLBACK_PAGE_CONFIG = {
	widthPx: 816,  // 8.5" × 96 dpi
	heightPx: 1056, // 11.0" × 96 dpi
	marginTopPx: 96,  // 1.0"
	marginBottomPx: 96,  // 1.0"
	marginInnerPx: 120,  // 1.25"
	marginOuterPx: 96,  // 1.0"
};

// ── helpers ───────────────────────────────────────────────────────────────────

function escapeAttr(value) {
	return String(value ?? '')
		.replaceAll('&', '&amp;')
		.replaceAll('"', '&quot;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;');
}

function headingHtml(kind, entityId, title, subtitle = '') {
	return `<div data-draft-heading="${kind}" data-entity-id="${escapeAttr(entityId)}" data-title="${escapeAttr(title)}" data-subtitle="${escapeAttr(subtitle)}"></div>`;
}

function buildCombinedHTML(scenes, draft = null) {
	if (!scenes?.length) return '';

	if (!draft?.groups) {
		return scenes.map((scene, i) => {
			const content = scene.content || '<p></p>';
			if (i < scenes.length - 1) {
				return content + `<hr data-scene-after="${scenes[i + 1].id}">`;
			}
			return content;
		}).join('');
	}

	let html = '';
	let firstScene = true;
	for (const group of draft.groups) {
		if (group.part) {
			const partTitle = group.part.title?.trim() || `Part ${group.part.partNumber ?? ''}`.trim();
			html += headingHtml('part', group.part.id, partTitle, group.part.subtitle?.trim() || '');
		}
		for (const chapter of group.chapters || []) {
			const chapterTitle = chapter.title?.trim() || `Chapter ${chapter.chapterNumber ?? ''}`.trim();
			const chapterScenes = chapter.scenes || [];
			if (!chapterScenes.length) {
				html += headingHtml('chapter', chapter.id, chapterTitle, chapter.subtitle?.trim() || '');
				continue;
			}
			if (!firstScene) html += `<hr data-scene-after="${chapterScenes[0].id}" data-locked="true">`;
			html += headingHtml('chapter', chapter.id, chapterTitle, chapter.subtitle?.trim() || '');
			chapterScenes.forEach((scene, index) => {
				html += scene.content || '<p></p>';
				if (index < chapterScenes.length - 1) {
					html += `<hr data-scene-after="${chapterScenes[index + 1].id}" data-locked="true">`;
				}
				firstScene = false;
			});
		}
	}
	return html;
}

function stripDraftHeadings(html) {
	return html.replace(/<div[^>]*data-draft-heading="[^"]+"[^>]*>[\s\S]*?<\/div>/gi, '');
}

function parseSceneChunks(html, firstSceneId) {
	const chunks = [];
	const re = /<hr[^>]+data-scene-after="([^"]+)"[^>]*\/?>(?:<\/hr>)?/gi;
	let lastIndex = 0;
	let currentSceneId = firstSceneId;
	let match;

	while ((match = re.exec(html)) !== null) {
		chunks.push({
			sceneId: currentSceneId,
			content: stripDraftHeadings(html.slice(lastIndex, match.index)).trim() || '<p></p>',
		});
		currentSceneId = match[1];
		lastIndex = match.index + match[0].length;
	}
	chunks.push({
		sceneId: currentSceneId,
		content: stripDraftHeadings(html.slice(lastIndex)).trim() || '<p></p>',
	});

	return chunks;
}

function serializeFragment(fragment, schema) {
	const wrapper = document.createElement('div');
	wrapper.appendChild(DOMSerializer.fromSchema(schema).serializeFragment(fragment));
	return wrapper.innerHTML.trim() || '<p></p>';
}

function fragmentHasMeaningfulContent(fragment) {
	let meaningful = false;
	fragment.descendants((node) => {
		if (meaningful) return false;
		if (node.isText) {
			if (node.text?.trim()) meaningful = true;
			return false;
		}
		if (node.isLeaf && node.type.name !== 'hardBreak') meaningful = true;
		return !meaningful;
	});
	return meaningful;
}

function locateSceneAtPosition(doc, firstSceneId, position) {
	if (!firstSceneId) return null;
	let sceneId = firstSceneId;
	let from = 0;
	let to = doc.content.size;

	doc.forEach((node, offset) => {
		if (node.type.name !== 'sceneBreak') return;
		if (offset < position) {
			sceneId = node.attrs.sceneId || null;
			from = offset + node.nodeSize;
		} else if (to === doc.content.size) {
			to = offset;
		}
	});

	return { sceneId, from, to };
}

function buildSceneSplitDraft(editor, singleSceneId, firstSceneId) {
	const { doc, selection } = editor.state;
	if (!selection.empty) {
		return { error: 'Place the cursor where the scene should be split; do not select text.' };
	}

	const splitPosition = selection.from;
	const bounds = singleSceneId
		? { sceneId: singleSceneId, from: 0, to: doc.content.size }
		: locateSceneAtPosition(doc, firstSceneId, splitPosition);

	if (!bounds?.sceneId) {
		return { error: 'The cursor is not inside a persisted manuscript scene.' };
	}

	const beforeDoc = doc.cut(bounds.from, splitPosition);
	const afterDoc = doc.cut(splitPosition, bounds.to);
	if (!fragmentHasMeaningfulContent(beforeDoc.content)
			|| !fragmentHasMeaningfulContent(afterDoc.content)) {
		return { error: 'Place the cursor between existing content so both scenes contain text.' };
	}

	const beforeContent = serializeFragment(beforeDoc.content, editor.schema);
	const afterContent = serializeFragment(afterDoc.content, editor.schema);
	return {
		sourceSceneId: bounds.sceneId,
		splitPosition,
		beforeContent,
		beforeWordCount: countWords(beforeContent),
		afterContent,
		afterWordCount: countWords(afterContent),
	};
}

function getDocSceneBreakIds(editor) {
	const ids = [];
	editor.state.doc.descendants((node) => {
		if (node.type.name === 'sceneBreak' && node.attrs.sceneId) {
			ids.push(node.attrs.sceneId);
		}
	});
	return ids;
}

function templateKey(t) {
	if (!t) return null;
	return `${t.scope}:${t.templateType}:${t.bookId ?? ''}:${t.updatedAt}`;
}

// Identifies one AI document variant's current saved state (memory doc / chapter
// summary / book summary / editorial, for a given provider). generatedAt is
// bumped by both a fresh generation and a hand-edit save (upsertGenerated/
// updateEdited both treat an edit as a refresh — see ChapterMemoryDao), so it's a
// reliable change marker; provider is included so switching provider always
// reloads even if two variants coincidentally share a generatedAt.
function aiDocKey(type, parentId, provider, doc) {
	if (!type || !parentId) return null;
	return `${type}:${parentId}:${provider ?? 'preferred'}:${doc?.generatedAt ?? 'none'}`;
}

// Replaces (or inserts) a provider variant in a variants list, newest first, so
// an autosave can update the cached variants array without a refetch flash.
function upsertVariant(list, doc) {
	if (!doc) return list;
	const arr = Array.isArray(list) ? list : [];
	return [doc, ...arr.filter(v => v.provider !== doc.provider)];
}

/**
 * Counts words in an HTML string by stripping tags and splitting on whitespace.
 * Used for per-scene word counts in multi-scene mode where only the raw HTML
 * chunk is available (not a TipTap editor instance).
 * Uses /\S+/g matching to stay consistent with TipTap's CharacterCount.words().
 */
function countWords(html) {
	if (!html) return 0;
	const text = html.replace(/<[^>]+>/g, ' ');
	const matches = text.match(/\S+/g);
	return matches ? matches.length : 0;
}

// ── component ─────────────────────────────────────────────────────────────────

/**
 * EditorPanel
 *
 * Props:
 *   partId         — selected part (part page preview mode).
 *   chapterId      — selected chapter (multi-scene or chapter-heading mode).
 *   sceneId        — selected scene (single-scene mode).
 *   projectId      — current project.
 *   bookId         — current book.
 *   templateType   — 'cover' | 'part' | null.
 *   templateScope  — 'global' | 'book' | null.
 *   aiDocType      — 'memory' | 'chapterSummary' | 'bookSummary' | null. When set,
 *                    chapterId (memory/chapterSummary) or bookId (bookSummary)
 *                    identifies the document. Not manuscript text — never
 *                    exported, never counted toward word totals.
 *   setSelection   — passed through to ReviewRail so its Memory tab's
 *                    "Edit in document" link can select the same document
 *                    this panel edits.
 *   onSelectBook   — callback(bookId) invoked when the user clicks a book in
 *                    the project shelf (project home mode).
 *   onOpenContextSettings
 *                  — opens the project/book settings dialog for the current selection.
 *   contextSettingsLabel
 *                  — label for the project/book settings menu item in the editor gear.
 *
 * Modes (priority order):
 *   1. AI doc mode         — aiDocType set (memory document / chapter or book summary)
 *   2. template mode       — templateType set
 *   3. book cover preview  — bookId set, no partId/chapterId/sceneId/template
 *   4. part page preview   — partId set, no chapterId/sceneId/template
 *   5. project shelf       — projectId set, no bookId (project home / book picker)
 *   6. single-scene mode   — sceneId set; no chapter heading
 *   7. multi-scene mode    — chapterId set; chapter title/subtitle shown above prose
 *
 * Codex entries are scenes (single-scene mode) whose parent chapter is a codex
 * category. "Is this a codex entry?" is determined from chapterData.codexCategory
 * (a plain field on the Chapter row — categories are chapter rows with codex_id
 * set and book_id null) rather than from selection.codexId, because several nav
 * click handlers carry codexId/codexCategory forward via `...prev` and can leave
 * it stale when navigating between the codex tree and the manuscript tree.
 * Reading the ground-truth field off the fetched chapter avoids that entirely.
 * When true, the entry's own scene title is rendered above the prose as a
 * non-editable heading (chapter-title styling); editing the title in the
 * Properties panel updates SCENE_KEYS.detail(sceneId), which this component
 * already subscribes to via useScene, so the heading stays in sync automatically.
 *
 * The book cover and part page previews activate whenever a book or part is
 * selected, regardless of whether page layout is enabled on the book.  When
 * page layout is not configured, DEFAULT_PAGE_CONFIG provides fallback
 * dimensions (6" × 9" Trade Paperback) so the canvas always renders.
 */
export default function EditorPanel({
	partId, chapterId, sceneId, projectId, bookId, codexId,
	templateType, templateScope, aiDocType, aiDocProvider, setSelection, onSelectBook,
	onOpenContextSettings, contextSettingsLabel,
}) {
	const { settings, updateSettings } = useProjectSettings(projectId);
	const queryClient = useQueryClient();
	const search = useSearch();
	const review = useReview();

	// ── Mode flags ────────────────────────────────────────────────────────────
	const aiDocMode = !!aiDocType;
	const isMemoryDoc = aiDocMode && aiDocType === 'memory';
	const isChapterSummaryDoc = aiDocMode && aiDocType === 'chapterSummary';
	const isBookSummaryDoc = aiDocMode && aiDocType === 'bookSummary';
	const isEditorialDoc = aiDocMode && aiDocType === 'editorial';
	const templateMode = !aiDocMode && !!templateType;
	const singleSceneMode = !aiDocMode && !templateMode && !!sceneId;
	const multiSceneMode = !aiDocMode && !templateMode && !singleSceneMode && !!chapterId && !codexId;
	const partDraftMode = !aiDocMode && !templateMode && !chapterId && !sceneId && !!partId && !!bookId;
	const bookDraftMode = !aiDocMode && !templateMode && !partId && !chapterId && !sceneId && !!bookId;
	const aggregateDraftMode = partDraftMode || bookDraftMode;

	// Review Mode is a layer on top of normal chapter editing: the rail shows
	// the selected manuscript chapter's AI review. It appears only for a
	// manuscript chapter (a chapter inside a book, never a codex entry), which
	// covers both multi-scene (chapter selected) and single-scene (a scene
	// within that chapter) editing.
	const reviewRailVisible = review.open && !aiDocMode && !!chapterId && !!bookId && !codexId;

	const isGlobalTpl = templateMode && templateScope === 'global';
	const isBookTpl = templateMode && templateScope === 'book';

	// Page-layout preview: fires whenever a book (or part within a book) is
	// selected with no chapter/scene/template/AI-doc active — regardless of
	// whether page layout is configured on the book.
	const pagePreviewEligible = !aiDocMode && !templateMode && !chapterId && !sceneId && !!bookId;

	const { data: previewPageBook } = useBook(pagePreviewEligible ? bookId : null);
	const { data: previewPageProject } = useProject(pagePreviewEligible ? projectId : null);

	// Use the book's configured page dimensions when available; fall back to
	// Trade Paperback defaults so the canvas renders even when page layout is
	// not yet enabled on the book.
	const configuredPageConfig = useMemo(
		() => pagePreviewEligible ? derivePageConfig(previewPageBook) : null,
		[pagePreviewEligible, previewPageBook]
	);
	const effectivePageConfig = configuredPageConfig ?? (pagePreviewEligible ? DEFAULT_PAGE_CONFIG : null);

	// Book cover: book selected, no part underneath.
	const bookCoverMode = pagePreviewEligible && !partId;
	// Part page: a part within the book is selected.
	const partPageMode = pagePreviewEligible && !!partId;
	const inPagePreviewMode = bookCoverMode || partPageMode;

	// Project shelf: project selected but no book open yet.
	// Clicking a book card calls onSelectBook to open it.
	const projectShelfMode = !aiDocMode && !templateMode && !bookId && !chapterId && !sceneId && !!projectId;

	// ── Chapter data for heading ───────────────────────────────────────────────
	// Fetched in multi-scene mode (chapter title/subtitle heading), single-scene
	// mode (to read codexCategory off the parent chapter so we can tell a codex
	// entry apart from a manuscript scene — see isCodexEntry below), and the two
	// chapter-scoped AI-doc modes (memory/chapter-summary heading label).
	const { data: chapterData } = useChapter(
		(multiSceneMode || singleSceneMode || isMemoryDoc || isChapterSummaryDoc || isEditorialDoc) ? chapterId : null
	);

	// ── AI document data (memory / chapter summary / book summary / editorial) ─
	// Since the provider-variants work, each AI doc has one variant per provider.
	// We fetch ALL variants for the active doc in one query and pick the selected
	// provider's variant client-side, so toggling providers is instant. Autosave
	// goes through the stable API modules directly inside scheduleSave (see
	// below), not the mutation hooks — scheduleSave's useCallback has a limited
	// dependency array, so a hook-returned mutate function captured in its closure
	// could go stale. The mutation hooks below are only used for the explicit
	// Generate/Regenerate and per-provider Clear actions, triggered directly from
	// click handlers where that staleness risk doesn't apply. Status/coverage
	// queries stay on the user's default provider for now (provider-aware coverage
	// is a later increment).
	const { data: aiCredentials = [] } = useAiCredentials();

	const { data: memoryVariants = [], isLoading: memoryVariantsLoading } = useChapterMemoryVariants(isMemoryDoc ? chapterId : null, isMemoryDoc);
	const { data: memoryStatusRows = [] } = useChapterMemoryStatus(isMemoryDoc ? bookId : null, isMemoryDoc);
	const { mutateAsync: generateMemoryAsync, isPending: generatingMemory } = useGenerateChapterMemory();
	const { mutateAsync: deleteMemoryAsync } = useDeleteChapterMemory();

	const { data: chapterSummaryVariants = [], isLoading: chapterSummaryVariantsLoading } = useChapterSummaryVariants(isChapterSummaryDoc ? chapterId : null, isChapterSummaryDoc);
	const { data: chapterSummaryRows = [] } = useBookChapterSummaries(isChapterSummaryDoc ? bookId : null, isChapterSummaryDoc);
	const { mutateAsync: generateChapterSummaryAsync, isPending: generatingChapterSummary } = useGenerateChapterSummary();
	const { mutateAsync: deleteChapterSummaryAsync } = useDeleteChapterSummary();

	const { data: bookSummaryVariants = [], isLoading: bookSummaryVariantsLoading } = useBookSummaryVariants(isBookSummaryDoc ? bookId : null, isBookSummaryDoc);
	const { data: bookSummaryStatusData } = useBookSummaryStatus(isBookSummaryDoc ? bookId : null, isBookSummaryDoc);
	const { data: bookSummaryChapterRows = [] } = useBookChapterSummaries(isBookSummaryDoc ? bookId : null, isBookSummaryDoc);
	const { mutateAsync: generateBookSummaryAsync, isPending: generatingBookSummary } = useGenerateBookSummary();
	const { mutateAsync: deleteBookSummaryAsync } = useDeleteBookSummary();

	// Editorial is chapter-scoped like memory/chapter-summary, but has no
	// book-wide aggregate or staleness view — it's purely author-facing and
	// never consumed by another AI function, so no status query.
	const { data: editorialVariants = [], isLoading: editorialVariantsLoading } = useChapterEditorialVariants(isEditorialDoc ? chapterId : null, isEditorialDoc);
	const { mutateAsync: generateEditorialAsync, isPending: generatingEditorial } = useGenerateChapterEditorial();
	const { mutateAsync: deleteEditorialAsync } = useDeleteChapterEditorial();

	// Heading label for the AI-doc modes ("Chapter 3: Title" / book title).
	const { data: aiDocBook } = useBook(isBookSummaryDoc ? bookId : null);

	// Providers the user holds an active credential for (can generate under), and
	// the default provider (matches the backend's "preferred" resolution).
	const aiCredentialProviders = useMemo(() => {
		const out = [];
		for (const c of aiCredentials || []) {
			const active = c.status ? c.status === 'ACTIVE' : true;
			if (active && c.provider && !out.includes(c.provider)) out.push(c.provider);
		}
		return out;
	}, [aiCredentials]);
	const defaultProviderKey = useMemo(() => {
		const active = (aiCredentials || []).filter(c => (c.status ? c.status === 'ACTIVE' : true));
		return (active.find(c => c.isDefault) || active[0])?.provider ?? null;
	}, [aiCredentials]);
	const credentialForProvider = useCallback((prov) => {
		if (!prov) return null;
		const forProv = (aiCredentials || []).filter(c => c.provider === prov && (c.status ? c.status === 'ACTIVE' : true));
		return forProv.find(c => c.isDefault) || forProv[0] || null;
	}, [aiCredentials]);

	const aiDocVariants = isMemoryDoc ? memoryVariants
		: isChapterSummaryDoc ? chapterSummaryVariants
			: isBookSummaryDoc ? bookSummaryVariants
				: isEditorialDoc ? editorialVariants : [];
	const aiDocLoading = isMemoryDoc ? memoryVariantsLoading
		: isChapterSummaryDoc ? chapterSummaryVariantsLoading
			: isBookSummaryDoc ? bookSummaryVariantsLoading
				: isEditorialDoc ? editorialVariantsLoading : false;

	// Selected provider: an explicit selection.aiDocProvider wins; otherwise the
	// default provider; otherwise (no credentials) fall back to whatever variant
	// exists so an existing doc is still shown.
	const selectedProvider = aiDocMode
		? (aiDocProvider || defaultProviderKey || (aiDocVariants[0]?.provider ?? null))
		: null;
	const aiDocCurrent = (aiDocMode && selectedProvider)
		? (aiDocVariants.find(v => v.provider === selectedProvider) ?? null)
		: null;
	const aiDocGenerating = isMemoryDoc ? generatingMemory : isChapterSummaryDoc ? generatingChapterSummary : isBookSummaryDoc ? generatingBookSummary : isEditorialDoc ? generatingEditorial : false;

	// Can the selected provider be (re)generated? Only if the user holds a
	// credential for it. A variant-only provider (key later removed) is viewable
	// but its Generate is disabled.
	const selectedProviderCredential = credentialForProvider(selectedProvider);
	const aiDocCanGenerate = !!selectedProviderCredential;

	// Preceding-chapter gating (memory only) / coverage gating (book summary only).
	const aiDocFlaggedPreceding = useMemo(
		() => isMemoryDoc ? flaggedPreceding(memoryStatusRows, chapterId) : [],
		[isMemoryDoc, memoryStatusRows, chapterId]
	);

	const aiDocFlaggedChapters = useMemo(
		() => isBookSummaryDoc ? flaggedChapters(bookSummaryChapterRows) : [],
		[isBookSummaryDoc, bookSummaryChapterRows]
	);

	const aiDocStatusState = isMemoryDoc
		? memoryStatusRows.find(s => s.chapterId === chapterId)?.state
		: isChapterSummaryDoc
			? chapterSummaryRows.find(s => s.chapterId === chapterId)?.state
			: isBookSummaryDoc
				? (!bookSummaryStatusData ? null : !bookSummaryStatusData.hasDoc ? 'MISSING' : bookSummaryStatusData.stale ? 'STALE_CONTENT' : 'OK')
				: null;

	const aiDocStatusChip = !aiDocStatusState ? null : isBookSummaryDoc
		? {
			label: aiDocStatusState === 'MISSING' ? 'Not generated' : summaryStateLabel(aiDocStatusState),
			color: summaryStateColor(aiDocStatusState),
			tooltip: summaryStateExplanation(aiDocStatusState),
		}
		: isChapterSummaryDoc
			? { label: summaryStateLabel(aiDocStatusState), color: summaryStateColor(aiDocStatusState), tooltip: summaryStateExplanation(aiDocStatusState) }
			: { label: memoryStateLabel(aiDocStatusState), color: memoryStateColor(aiDocStatusState), tooltip: memoryStateExplanation(aiDocStatusState) };

	const aiDocHeadingLabel = isBookSummaryDoc
		? (aiDocBook?.title?.trim() || 'This book')
		: chapterData
			? (chapterData.title?.trim() ? `Chapter ${chapterData.chapterNumber}: ${chapterData.title.trim()}` : `Chapter ${chapterData.chapterNumber}`)
			: '';

	const aiDocTypeLabel = isMemoryDoc ? 'Memory document' : isChapterSummaryDoc ? 'Chapter summary' : isEditorialDoc ? 'Editorial' : 'Book summary';
	const aiDocMetaLine = aiDocCurrent
		? [
			aiDocCurrent.source === 'EDITED' ? 'Edited' : 'Generated',
			aiDocCurrent.generatedAt ? formatMemoryTime(aiDocCurrent.generatedAt) : null,
			aiDocCurrent.model || null,
			isBookSummaryDoc && typeof aiDocCurrent.wordCount === 'number' ? `${aiDocCurrent.wordCount} words` : null,
		].filter(Boolean).join(' · ')
		: '';

	// ── Scene / template data ─────────────────────────────────────────────────
	const { data: scenes, isLoading: scenesLoading } = useScenes(multiSceneMode ? chapterId : null);
	const { data: singleScene, isLoading: singleSceneLoading } = useScene(singleSceneMode ? sceneId : null);
	const { data: draftDocument, isLoading: draftLoading } = useDraftDocument({
		bookId, partId, enabled: aggregateDraftMode,
	});
	const draftScenes = useMemo(() => flattenDraftScenes(draftDocument), [draftDocument]);
	const activeScenes = aggregateDraftMode ? draftScenes : scenes;

	const { data: globalTpl, isLoading: globalTplLoading } = useGlobalTemplate(templateType, isGlobalTpl);
	const { data: bookTpl, isLoading: bookTplLoading } = useBookTemplate(bookId, templateType, isBookTpl);
	const template = isGlobalTpl ? globalTpl : (isBookTpl ? bookTpl : null);
	const templateLoading = (isGlobalTpl && globalTplLoading) || (isBookTpl && bookTplLoading);

	const { data: previewBook } = useBook(isBookTpl ? bookId : null);
	const { data: previewProject } = useProject(projectId);

	const { data: bookStyleSheet } = useBookStyles(bookId, !!bookId);
	const { data: globalStyleSheet } = useGlobalStyles(!bookId);
	const styleSheet = bookId ? bookStyleSheet : globalStyleSheet;

	// ── Context-sensitive word count ──────────────────────────────────────────
	// Project mode: fetch project total from API (all books).
	// Book mode: fetch book total from API (includes chapter/part headings).
	// Part mode: fetch part total from API (includes part + chapter headings).
	// Chapter mode: TipTap live count + heading words computed below.
	// Scene mode: TipTap live count only.
	//
	// Each response also carries paragraphCount, feeding the estimated page
	// count (see toolbarPageConfig below) — the project-shelf query doesn't
	// need it since the estimate isn't shown there (no single book/page size
	// to estimate against), so only book/part responses are read for it.
	const { data: projectWordCount } = useQuery({
		queryKey: ['projects', projectId, 'word-count'],
		queryFn: () => client.get(`/projects/${projectId}/word-count`).then(r => r.data.wordCount),
		enabled: !!projectId && projectShelfMode,
		staleTime: 60_000,
	});

	const { data: bookWordCountData } = useQuery({
		queryKey: ['books', bookId, 'word-count'],
		queryFn: () => client.get(`/books/${bookId}/word-count`).then(r => r.data),
		// Enabled for book draft/cover preview (status bar) and book-scope
		// template mode (WORDS token in template editor preview).
		enabled: !!bookId && (bookDraftMode || templateMode),
		staleTime: 60_000,
	});

	const { data: partWordCountData } = useQuery({
		queryKey: ['parts', partId, 'word-count'],
		queryFn: () => client.get(`/parts/${partId}/word-count`).then(r => r.data),
		enabled: !!partId && partDraftMode,
		staleTime: 60_000,
	});

	// Words contributed by the chapter heading (title + subtitle) displayed above
	// the editor in multi-scene mode. These are outside the TipTap document so
	// CharacterCount cannot see them; we add them to the live count manually.
	const chapterHeadingWords = useMemo(() => {
		if (!multiSceneMode || !chapterData) return 0;
		const title = chapterData.title?.trim() || `Chapter ${chapterData.chapterNumber}`;
		const subtitle = chapterData.subtitle?.trim() || '';
		return countWords(title) + countWords(subtitle);
	}, [multiSceneMode, chapterData]);

	// Paragraph-count counterpart to chapterHeadingWords: the title line
	// counts as one "paragraph" line, plus one more for a non-blank subtitle —
	// matching how BookDao/PartDao count chapter/part headings server-side.
	const chapterHeadingParagraphs = useMemo(() => {
		if (!multiSceneMode || !chapterData) return 0;
		return 1 + (chapterData.subtitle?.trim() ? 1 : 0);
	}, [multiSceneMode, chapterData]);

	// Override the toolbar word count for modes where there is no live TipTap
	// editor (or the TipTap count is stale/irrelevant).
	const toolbarWordCountOverride = projectShelfMode
		? (projectWordCount ?? 0)
		: (bookDraftMode || bookCoverMode)
			? (bookWordCountData?.wordCount ?? 0)
			: partDraftMode
				? (partWordCountData?.wordCount ?? 0)
				: null;

	// Paragraph-count counterpart to toolbarWordCountOverride, feeding the
	// estimated page count. Project-shelf mode has no counterpart — the
	// estimate isn't shown there (see toolbarPageConfig below).
	const toolbarParagraphCountOverride = (bookDraftMode || bookCoverMode)
		? (bookWordCountData?.paragraphCount ?? 0)
		: partDraftMode
			? (partWordCountData?.paragraphCount ?? 0)
			: null;

	// Resolved page size feeding the editor status bar's estimated page count.
	// Reuses the same book record and derivePageConfig()/DEFAULT_PAGE_CONFIG
	// fallback that already drive the book cover / part page preview canvas
	// above, so the estimate matches what the author sees there. Shown for
	// book/part/chapter/scene editing only — not AI documents, templates, or
	// the project shelf (which has no single book/page size to estimate
	// against).
	const { data: pageEstimateBook } = useBook(bookId);
	const toolbarPageConfig = (!aiDocMode && !templateMode && !!bookId)
		? (derivePageConfig(pageEstimateBook) ?? EXPORT_FALLBACK_PAGE_CONFIG)
		: null;

	const isLoading = aiDocMode
		? aiDocLoading
		: templateMode
			? templateLoading
			: singleSceneMode
				? singleSceneLoading
				: aggregateDraftMode
					? draftLoading
					: scenesLoading;

	const { mutate: deleteScene } = useDeleteScene();

	const [isSaving, setIsSaving] = useState(false);
	const [previewActive, setPreviewActive] = useState(false);
	const [splitDraft, setSplitDraft] = useState(null);
	const [splitTitle, setSplitTitle] = useState('');
	const [splitPending, setSplitPending] = useState(false);
	const [splitSnack, setSplitSnack] = useState(null);

	// ── Review-rail resize state ──────────────────────────────────────────────
	const [railWidth, setRailWidth] = useState(() => {
		const raw = window.localStorage.getItem(RAIL_STORAGE_KEY);
		const parsed = Number(raw);
		return Number.isFinite(parsed) ? clampRail(parsed) : DEFAULT_RAIL_WIDTH;
	});
	const railResizeRef = useRef(null);

	const showEditorPreview = templateMode && previewActive;

	// ── refs ─────────────────────────────────────────────────────────────────
	const saveTimer = useRef(null);
	const firstSceneIdRef = useRef(null);
	const prevSceneBreakIdsRef = useRef([]);
	const loadedChapterIdRef = useRef(null);
	const loadedSceneOrderRef = useRef('');
	const loadedSceneIdRef = useRef(null);
	const loadedTemplateKeyRef = useRef(null);
	const loadedAiDocKeyRef = useRef(null);
	const aiDocTypeRef = useRef(aiDocType);
	const aiDocModeRef = useRef(aiDocMode);
	const aiDocProviderRef = useRef(selectedProvider);
	const singleSceneModeRef = useRef(singleSceneMode);
	const templateModeRef = useRef(templateMode);
	const templateScopeRef = useRef(templateScope);
	const templateTypeRef = useRef(templateType);
	const bookIdRef = useRef(bookId);
	const sceneIdRef = useRef(sceneId);
	const scheduleSaveRef = useRef(null);
	const creatingFirstSceneRef = useRef(false);
	const chapterIdRef = useRef(chapterId);
	const editorRef = useRef(null);
	const searchRef = useRef(search);
	const aggregateDraftModeRef = useRef(aggregateDraftMode);
	const expectedSceneIdsRef = useRef([]);
	const activeScenesRef = useRef([]);

	// A codex entry is a scene (single-scene mode) whose parent chapter is a
	// codex entry — ground-truthed via chapterData.codexId (set on all codex
	// type chapters, both system-seeded and author-created).
	const isCodexEntry = singleSceneMode && !!chapterData?.codexId;
	const codexEntryHeadingTitle = isCodexEntry
		? (singleScene?.title?.trim() || 'Untitled Entry')
		: null;

	// Structured-field schema for a codex entry, resolved from the entry's own
	// Type instance (its parent category chapter) via GET /codex/types/{typeId}.
	// Each Type owns its active fields, so renaming/removing a field affects only
	// this project. A schema-less Type resolves to empty fields and the entry is
	// plain title-plus-body, unchanged.
	const codexTypeId = isCodexEntry ? (chapterData?.id ?? null) : null;
	const { data: codexType } = useCodexType(codexTypeId);
	const codexEntrySchema = useMemo(() => {
		if (!isCodexEntry || !codexType) return null;
		return { fields: codexType.fields || [] };
	}, [isCodexEntry, codexType]);

	useEffect(() => { chapterIdRef.current = chapterId; }, [chapterId]);
	useEffect(() => { singleSceneModeRef.current = singleSceneMode; }, [singleSceneMode]);
	useEffect(() => { templateModeRef.current = templateMode; }, [templateMode]);
	useEffect(() => { templateScopeRef.current = templateScope; }, [templateScope]);
	useEffect(() => { templateTypeRef.current = templateType; }, [templateType]);
	useEffect(() => { aiDocTypeRef.current = aiDocType; }, [aiDocType]);
	useEffect(() => { aiDocModeRef.current = aiDocMode; }, [aiDocMode]);
	useEffect(() => { aiDocProviderRef.current = selectedProvider; }, [selectedProvider]);
	useEffect(() => { bookIdRef.current = bookId; }, [bookId]);
	useEffect(() => { sceneIdRef.current = sceneId; }, [sceneId]);
	useEffect(() => { searchRef.current = search; }, [search]);
	useEffect(() => { aggregateDraftModeRef.current = aggregateDraftMode; }, [aggregateDraftMode]);
	useEffect(() => {
		expectedSceneIdsRef.current = (activeScenes || []).map(s => s.id);
		activeScenesRef.current = activeScenes || [];
	}, [activeScenes]);

	useEffect(() => { if (activeScenes?.length) firstSceneIdRef.current = activeScenes[0].id; }, [activeScenes]);

	// ── Rail resize — persist width and handle drag ───────────────────────────
	useEffect(() => {
		window.localStorage.setItem(RAIL_STORAGE_KEY, String(railWidth));
	}, [railWidth]);

	useEffect(() => {
		const handleMouseMove = (e) => {
			const state = railResizeRef.current;
			if (!state) return;
			const delta = e.clientX - state.startX;
			setRailWidth(clampRail(state.startWidth - delta));
		};
		const handleMouseUp = () => { railResizeRef.current = null; };
		window.addEventListener('mousemove', handleMouseMove);
		window.addEventListener('mouseup', handleMouseUp);
		return () => {
			window.removeEventListener('mousemove', handleMouseMove);
			window.removeEventListener('mouseup', handleMouseUp);
		};
	}, []);

	const handleRailResizeMouseDown = useCallback((e) => {
		e.preventDefault();
		railResizeRef.current = { startX: e.clientX, startWidth: railWidth };
	}, [railWidth]);

	useEffect(() => {
		if (singleSceneMode) {
			loadedChapterIdRef.current = null;
			loadedSceneOrderRef.current = '';
		} else {
			loadedSceneIdRef.current = null;
		}
	}, [singleSceneMode]);

	useEffect(() => {
		if (saveTimer.current) { clearTimeout(saveTimer.current); saveTimer.current = null; }
		loadedTemplateKeyRef.current = null;
		loadedAiDocKeyRef.current = null;
		loadedChapterIdRef.current = null;
		loadedSceneOrderRef.current = '';
		loadedSceneIdRef.current = null;
		prevSceneBreakIdsRef.current = [];
		creatingFirstSceneRef.current = false;
	}, [templateMode, templateType, templateScope, aiDocType, selectedProvider, bookId, partId, chapterId]);

	// ── save ─────────────────────────────────────────────────────────────────

	const scheduleSave = useCallback((html) => {
		if (saveTimer.current) clearTimeout(saveTimer.current);
		saveTimer.current = setTimeout(async () => {
			setIsSaving(true);
			try {
				if (aiDocTypeRef.current) {
					const type = aiDocTypeRef.current;
					const prov = aiDocProviderRef.current;
					if (type === 'memory') {
						const cid = chapterIdRef.current;
						if (!cid) return;
						const saved = await chapterMemoryApi.save(cid, html, prov);
						loadedAiDocKeyRef.current = aiDocKey(type, cid, saved.provider, saved);
						queryClient.setQueryData(CHAPTER_MEMORY_KEYS.variants(cid), (old) => upsertVariant(old, saved));
						queryClient.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.doc(cid) });
						queryClient.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.status(bookIdRef.current) });
					} else if (type === 'chapterSummary') {
						const cid = chapterIdRef.current;
						if (!cid) return;
						const saved = await summaryApi.saveChapter(cid, html, prov);
						loadedAiDocKeyRef.current = aiDocKey(type, cid, saved.provider, saved);
						queryClient.setQueryData(SUMMARY_KEYS.chapterVariants(cid), (old) => upsertVariant(old, saved));
						queryClient.invalidateQueries({ queryKey: SUMMARY_KEYS.chapterDoc(cid) });
						queryClient.invalidateQueries({ queryKey: SUMMARY_KEYS.chapters(bookIdRef.current) });
						queryClient.invalidateQueries({ queryKey: SUMMARY_KEYS.bookStatus(bookIdRef.current) });
					} else if (type === 'bookSummary') {
						const bid = bookIdRef.current;
						if (!bid) return;
						const saved = await summaryApi.saveBook(bid, html, prov);
						loadedAiDocKeyRef.current = aiDocKey(type, bid, saved.provider, saved);
						queryClient.setQueryData(SUMMARY_KEYS.bookVariants(bid), (old) => upsertVariant(old, saved));
						queryClient.invalidateQueries({ queryKey: SUMMARY_KEYS.bookDoc(bid) });
						queryClient.invalidateQueries({ queryKey: SUMMARY_KEYS.bookStatus(bid) });
					} else if (type === 'editorial') {
						const cid = chapterIdRef.current;
						if (!cid) return;
						const saved = await editorialApi.save(cid, html, prov);
						loadedAiDocKeyRef.current = aiDocKey(type, cid, saved.provider, saved);
						queryClient.setQueryData(EDITORIAL_KEYS.variants(cid), (old) => upsertVariant(old, saved));
						queryClient.invalidateQueries({ queryKey: EDITORIAL_KEYS.doc(cid) });
					}
					return;
				}

				if (templateModeRef.current) {
					const type = templateTypeRef.current;
					let saved;
					if (templateScopeRef.current === 'global') {
						saved = await templatesApi.updateGlobal(type, html);
						loadedTemplateKeyRef.current = templateKey(saved);
						queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.global(type) });
						// Books with no BOOK override resolve to the global via resolveForBook.
						// Invalidate all cached book-template entries so BookCoverPreview and
						// PartPagePreview re-fetch and reflect the updated global immediately.
						queryClient.invalidateQueries({ queryKey: ['templates', 'book'] });
					} else {
						saved = await templatesApi.upsertBook(bookIdRef.current, type, html);
						loadedTemplateKeyRef.current = templateKey(saved);
						queryClient.invalidateQueries({ queryKey: TEMPLATE_KEYS.book(bookIdRef.current, type) });
					}
					return;
				}

				if (singleSceneModeRef.current) {
					const sid = sceneIdRef.current;
					if (!sid) return;
					// Use TipTap's CharacterCount for the most accurate word count —
					// it operates on the parsed document model, matching what the
					// status bar displays.
					const wc = editorRef.current?.storage?.characterCount?.words() ?? 0;
					await scenesApi.updateContent(sid, html, wc);
					queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(sid) });
				} else {
					let firstId = firstSceneIdRef.current;
					if (!firstId) {
						// Chapter mode with no scenes yet — auto-create the first scene so
						// the user's typing is persisted without having to use Add Scene.
						// creatingFirstSceneRef guards against duplicate creation on rapid typing.
						const cid = chapterIdRef.current;
						if (!cid || aggregateDraftModeRef.current || creatingFirstSceneRef.current) return;
						creatingFirstSceneRef.current = true;
						try {
							const newScene = await scenesApi.create(cid, { title: '' });
							firstId = newScene.id;
							firstSceneIdRef.current = firstId;
							expectedSceneIdsRef.current = [firstId];
							// Pre-set the loaded-order refs so the upcoming query refetch
							// doesn't see a scope/order change and re-blank the editor.
							loadedChapterIdRef.current = `chapter:${cid}`;
							loadedSceneOrderRef.current = firstId;
							const wc = countWords(html);
							await scenesApi.updateContent(firstId, html, wc);
							queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(cid) });
							queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(firstId) });
						} catch (err) {
							console.error('[EditorPanel] Failed to auto-create first scene:', err);
						} finally {
							creatingFirstSceneRef.current = false;
						}
						return;
					}
					const chunks = parseSceneChunks(html, firstId);
					if (!chunks.length) return;
					const expectedIds = expectedSceneIdsRef.current;
					if (aggregateDraftModeRef.current) {
						const actualIds = chunks.map(c => c.sceneId);
						if (actualIds.length !== expectedIds.length || actualIds.some((id, i) => id !== expectedIds[i])) {
							console.error('[EditorPanel] Draft scene boundaries changed; save aborted to protect scene ownership.');
							return;
						}
					}
					// Count words per chunk from the HTML — TipTap's CharacterCount
					// only gives a total for the whole document, not per-scene.
					// countWords() uses the same /\S+/g algorithm TipTap uses internally,
					// so the per-scene values sum to the same total the status bar shows.
					await Promise.all(
						chunks.map(c => scenesApi.updateContent(c.sceneId, c.content, countWords(c.content)))
					);
					chunks.forEach(c =>
						queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(c.sceneId) })
					);
				}
			} catch (err) {
				console.error('[EditorPanel] Save failed:', err);
			} finally {
				setIsSaving(false);
			}
		}, AUTOSAVE_DELAY_MS);
	}, [queryClient]);

	useEffect(() => { scheduleSaveRef.current = scheduleSave; }, [scheduleSave]);

	// ── editor ────────────────────────────────────────────────────────────────

	const editor = useEditor({
		extensions: [
			StarterKit.configure({ paragraph: false, underline: false, horizontalRule: false }),
			StyledParagraph,
			FontSize,
			SceneBreak,
			DraftHeading,
			TemplateToken,
			Underline,
			TextAlign.configure({ types: ['heading', 'paragraph'], defaultAlignment: 'left' }),
			// Images are stored as base64 data URLs embedded in scene content HTML.
			// ResizableImage extends the built-in Image extension with width and align
			// attributes, a drag-to-resize handle, and a floating control bar.
			// allowBase64 and inline:false are configured inside the extension.
			ResizableImage,
			Placeholder.configure({
				placeholder: isCodexEntry ? 'Begin your codex item…' : 'Begin your scene…',
			}),
			CharacterCount,
			SearchHighlight,
			ReviewHighlight,
			// Em dash, ellipsis, and curly quotes only — the rest of the built-in
			// typography rules (arrows, (c)/(tm)/(r), fractions, x for multiplication,
			// etc.) are disabled so ordinary manuscript prose isn't rewritten.
			TiptapTypography.configure({
				emDash: '—',
				ellipsis: '…',
				openDoubleQuote: '“',
				closeDoubleQuote: '”',
				openSingleQuote: '‘',
				closeSingleQuote: '’',
				leftArrow: false,
				rightArrow: false,
				copyright: false,
				trademark: false,
				servicemark: false,
				registeredTrademark: false,
				oneHalf: false,
				oneQuarter: false,
				threeQuarters: false,
				plusMinus: false,
				notEqual: false,
				laquo: false,
				raquo: false,
				multiplication: false,
				superscriptTwo: false,
				superscriptThree: false,
			}),
		],
		content: '',
		onUpdate: ({ editor }) => {
			if (!aiDocModeRef.current && !singleSceneModeRef.current && !templateModeRef.current && !aggregateDraftModeRef.current) {
				const currentIds = getDocSceneBreakIds(editor);
				const prevIds = prevSceneBreakIdsRef.current;
				const removedIds = prevIds.filter(id => !currentIds.includes(id));
				if (removedIds.length > 0) {
					const cid = chapterIdRef.current;
					loadedSceneOrderRef.current = loadedSceneOrderRef.current
						.split(',').filter(id => !removedIds.includes(id)).join(',');
					removedIds.forEach(id => deleteScene({ id, chapterId: cid }));
				}
				prevSceneBreakIdsRef.current = currentIds;
			}
			const currentHtml = editor.getHTML();
			const liveSearch = searchRef.current;
			if (liveSearch.open && liveSearch.query && !aiDocModeRef.current && !templateModeRef.current) {
				if (singleSceneModeRef.current) {
					const sid = sceneIdRef.current;
					if (sid) {
						const count = searchHighlightKey.getState(editor.state)?.matches?.length ?? 0;
						liveSearch.updateLiveSceneCount(sid, count, {
							chapterId: chapterIdRef.current,
							partId, bookId, title: singleScene?.title || 'Untitled Scene',
						});
					}
				} else if (firstSceneIdRef.current) {
					const chunks = parseSceneChunks(currentHtml, firstSceneIdRef.current);
					chunks.forEach(chunk => {
						const source = activeScenesRef.current.find(item => item.id === chunk.sceneId);
						liveSearch.updateLiveSceneCount(
							chunk.sceneId,
							countHtmlOccurrences(chunk.content, liveSearch.query, liveSearch.matchCase),
							{ chapterId: chapterIdRef.current, partId, bookId, title: source?.title || 'Untitled Scene' },
						);
					});
				}
			}
			scheduleSaveRef.current?.(currentHtml);
		},
		editorProps: {
			attributes: {
				style: [
					'font-family: var(--nkms-font-family)',
					'font-size: var(--nkms-font-size)',
					'line-height: var(--nkms-line-height)',
					'max-width: 72ch',
					'margin: 0 auto',
					'padding: 0 8px',
					'min-height: 400px',
					'outline: none',
				].join('; '),
			},
		},
	}, [isCodexEntry]);

	useEffect(() => { editorRef.current = editor; }, [editor]);

	// Keep the transient ProseMirror search decorations synchronized with the
	// shared search bar. Decorations never enter the stored HTML.
	useEffect(() => {
		if (!isEditorReady(editor)) return;
		if (!search.open || !search.query || templateMode || aiDocMode) {
			runEditorCommand(editor, ed => ed.commands.clearSearch());
			return;
		}
		// Search navigation must never change the manuscript selection. In
		// chapter mode the editor contains every scene in the chapter, so the
		// provider's flattened match index is already the correct document-wide
		// index. In single-scene mode it is likewise local to that scene.
		const editorMatchIndex = search.activeIndex >= 0 ? search.activeIndex : 0;
		const applied = runEditorCommand(editor, ed => ed.commands.setSearch({
			query: search.query,
			matchCase: search.matchCase,
			activeIndex: editorMatchIndex,
		}));
		if (applied && search.totalCount > 0) {
			requestAnimationFrame(() => {
				runEditorCommand(editor, ed => ed.commands.scrollToSearchMatch(editorMatchIndex));
			});
		}
	}, [editor, search.open, search.query, search.matchCase, search.activeIndex, search.totalCount, templateMode, aiDocMode]);

	const { registerEditorActions } = search;

	useEffect(() => {
		if (!isEditorReady(editor)) return;

		registerEditorActions({
			next: () =>
				runEditorCommand(editor, ed => ed.commands.goToNextSearchMatch()),
			previous: () =>
				runEditorCommand(editor, ed => ed.commands.goToPreviousSearchMatch()),
			replaceCurrent: (replacement) =>
				runEditorCommand(editor, ed => ed.commands.replaceCurrentSearchMatch(replacement)),
			replaceAll: (replacement) =>
				runEditorCommand(editor, ed => ed.commands.replaceAllSearchMatches(replacement)),
		});

		return () => {
			registerEditorActions(null);
		};
	}, [editor, registerEditorActions]);

	// ── split scene at cursor ─────────────────────────────────────────────────

	const handleSplitSceneClick = useCallback(() => {
		const ed = editorRef.current;
		if (!isEditorReady(ed)) return;
		try {
			const draft = buildSceneSplitDraft(
				ed,
				singleSceneModeRef.current ? sceneIdRef.current : null,
				firstSceneIdRef.current,
			);
			if (draft.error) {
				setSplitSnack({ severity: 'warning', message: draft.error });
				return;
			}
			setSplitTitle(generateDefaultSceneTitle());
			setSplitDraft(draft);
		} catch (err) {
			console.error('[EditorPanel] Could not prepare scene split:', err);
			setSplitSnack({ severity: 'error', message: 'Could not determine the split position.' });
		}
	}, []);

	const handleSplitSceneCancel = useCallback(() => {
		if (splitPending) return;
		setSplitDraft(null);
		setSplitTitle('');
	}, [splitPending]);

	const handleSplitSceneConfirm = useCallback(async () => {
		const draft = splitDraft;
		const title = splitTitle.trim();
		if (!draft || !title || splitPending || isSaving) return;

		if (saveTimer.current) {
			clearTimeout(saveTimer.current);
			saveTimer.current = null;
		}

		setSplitPending(true);
		setIsSaving(true);
		try {
			const newScene = await scenesApi.split(draft.sourceSceneId, {
				title,
				beforeContent: draft.beforeContent,
				beforeWordCount: draft.beforeWordCount,
				afterContent: draft.afterContent,
				afterWordCount: draft.afterWordCount,
			});

			const ed = editorRef.current;
			if (!isEditorReady(ed)) throw new Error('Editor is no longer available.');

			if (singleSceneModeRef.current) {
				ed.commands.setContent(draft.beforeContent, false);
				ed.commands.focus('end');
			} else {
				const currentScenes = activeScenesRef.current || [];
				const sourceIndex = currentScenes.findIndex(scene => scene.id === draft.sourceSceneId);
				if (sourceIndex >= 0) {
					const nextScenes = [...currentScenes];
					nextScenes.splice(sourceIndex + 1, 0, newScene);
					activeScenesRef.current = nextScenes;
					expectedSceneIdsRef.current = nextScenes.map(scene => scene.id);
				}

				const inserted = ed.chain()
					.focus()
					.setTextSelection(draft.splitPosition)
					.setSceneBreak({ sceneId: newScene.id })
					.run();

				if (inserted && firstSceneIdRef.current) {
					const orderedIds = [firstSceneIdRef.current, ...getDocSceneBreakIds(ed)];
					loadedSceneOrderRef.current = orderedIds.join(',');
					expectedSceneIdsRef.current = orderedIds;
				} else {
					// The database split already succeeded. Force the chapter editor to
					// rebuild from the authoritative scene list if the local transform failed.
					loadedChapterIdRef.current = null;
					loadedSceneOrderRef.current = '';
				}
			}

			const cid = chapterIdRef.current;
			queryClient.setQueryData(SCENE_KEYS.detail(newScene.id), newScene);
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(draft.sourceSceneId) });
			if (cid) queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(cid) });
			queryClient.invalidateQueries({
				predicate: query => query.queryKey[query.queryKey.length - 1] === 'word-count',
			});

			setSplitDraft(null);
			setSplitTitle('');
			setSplitSnack({ severity: 'success', message: `Scene split. Created "${newScene.title}".` });
		} catch (err) {
			console.error('[EditorPanel] Failed to split scene:', err);
			setSplitSnack({
				severity: 'error',
				message: err?.response?.data?.message ?? err?.message ?? 'Scene split failed.',
			});
			const ed = editorRef.current;
			if (isEditorReady(ed)) scheduleSaveRef.current?.(ed.getHTML());
		} finally {
			setSplitPending(false);
			setIsSaving(false);
		}
	}, [splitDraft, splitTitle, splitPending, isSaving, queryClient]);

	// ── token insertion + preview ─────────────────────────────────────────────

	const tokenOptions = useMemo(
		() => (templateMode ? tokensForType(templateType) : []),
		[templateMode, templateType]
	);
	const handleInsertToken = useCallback((token) => {
		const ed = editorRef.current;
		if (!isEditorReady(ed)) return;
		ed.chain().focus().insertTemplateToken({ token }).run();
	}, []);
	const handleTogglePreview = useCallback(() => setPreviewActive(p => !p), []);

	const previewValues = useMemo(
		() => resolveValues({ scope: templateScope, book: previewBook, project: previewProject, wordCount: bookWordCountData?.wordCount ?? null }),
		[templateScope, previewBook, previewProject, bookWordCountData]
	);
	const previewHtml = useMemo(() => {
		if (!showEditorPreview || !isEditorReady(editor)) return '';
		return renderPreviewHtml(editor.getHTML(), previewValues);
	}, [showEditorPreview, previewValues, editor]);

	const styleSx = useMemo(() => buildStyleSx(styleSheet), [styleSheet]);

	// ── load content ─────────────────────────────────────────────────────────

	useEffect(() => {
		if (!isEditorReady(editor)) return;

		let cancelled = false;
		const setEditorContent = (html) => {
			queueMicrotask(() => {
				runEditorCommand(editor, ed => {
					if (!cancelled) ed.commands.setContent(html, false);
				});
			});
			return () => {
				cancelled = true;
			};
		};

		if (aiDocMode) {
			if (aiDocLoading) return;
			const parentId = isBookSummaryDoc ? bookId : chapterId;
			const key = aiDocKey(aiDocType, parentId, selectedProvider, aiDocCurrent);
			if (loadedAiDocKeyRef.current === key) return;
			prevSceneBreakIdsRef.current = [];
			loadedAiDocKeyRef.current = key;
			return setEditorContent(aiDocCurrent?.content || '<p></p>');
		}

		if (templateMode) {
			if (!template) return;
			const key = templateKey(template);
			if (loadedTemplateKeyRef.current === key) return;
			prevSceneBreakIdsRef.current = [];
			loadedTemplateKeyRef.current = key;
			return setEditorContent(template.content || '<p></p>');
		}

		if (singleSceneMode) {
			if (!singleScene) return;
			if (loadedSceneIdRef.current === sceneId) return;
			prevSceneBreakIdsRef.current = [];
			loadedSceneIdRef.current = sceneId;
			return setEditorContent(singleScene.content || '<p></p>');
		}

		const contentScenes = aggregateDraftMode ? activeScenes : scenes;
		if (!contentScenes) return;

		const newOrder = contentScenes.map(s => s.id).join(',');
		const scopeKey = aggregateDraftMode
			? `${partDraftMode ? 'part' : 'book'}:${partId || bookId}`
			: `chapter:${chapterId}`;
		const scopeChanged = loadedChapterIdRef.current !== scopeKey;
		const orderChanged = newOrder !== loadedSceneOrderRef.current;

		if (!scopeChanged && !orderChanged) return;
		if (scopeChanged && saveTimer.current) { clearTimeout(saveTimer.current); saveTimer.current = null; }

		if (contentScenes.length === 0) {
			loadedChapterIdRef.current = scopeKey;
			loadedSceneOrderRef.current = '';
			prevSceneBreakIdsRef.current = [];
			return setEditorContent('');
		}

		const html = buildCombinedHTML(contentScenes, aggregateDraftMode ? draftDocument : null);
		prevSceneBreakIdsRef.current = contentScenes.slice(1).map(s => s.id);
		loadedChapterIdRef.current = scopeKey;
		loadedSceneOrderRef.current = newOrder;
		return setEditorContent(html);
	}, [editor, scenes, activeScenes, draftDocument, aggregateDraftMode, partDraftMode, partId, bookId, chapterId, singleScene, sceneId, singleSceneMode, templateMode, template, aiDocMode, aiDocLoading, aiDocCurrent, aiDocType, selectedProvider, isBookSummaryDoc]);

	useEffect(() => () => { if (saveTimer.current) clearTimeout(saveTimer.current); }, []);

	// ── AI-doc generate / regenerate ──────────────────────────────────────────

	const [aiDocRegenConfirmOpen, setAiDocRegenConfirmOpen] = useState(false);
	const [aiDocGateOpen, setAiDocGateOpen] = useState(false);
	const [aiDocGuidance, setAiDocGuidance] = useState('');
	const [aiDocGuidanceInitKey, setAiDocGuidanceInitKey] = useState(null);
	const [aiDocSnack, setAiDocSnack] = useState(null); // { severity, message, persist } | null

	// Pre-fills from whatever guidance produced the current artifact, re-derived
	// on scope switch and once loading finishes — never auto-cleared after a
	// successful run (the same "setState during render" derived-key pattern used
	// throughout this app instead of an effect, and the same guidance lifecycle
	// as the legacy ChapterMemoryEditor/BookSummaryDialog peek surfaces).
	const aiDocGuidanceKey = aiDocMode
		? `${aiDocType}:${isBookSummaryDoc ? bookId : chapterId}:${selectedProvider ?? 'preferred'}:${aiDocLoading ? 'loading' : 'loaded'}`
		: null;
	if (aiDocMode && aiDocGuidanceKey !== aiDocGuidanceInitKey) {
		setAiDocGuidanceInitKey(aiDocGuidanceKey);
		setAiDocGuidance(aiDocCurrent?.userGuidance ?? '');
	}

	const doAiDocGenerate = useCallback(() => {
		const userGuidance = aiDocGuidance.trim() || null;
		const label = isMemoryDoc ? 'memory document'
			: isChapterSummaryDoc ? 'chapter summary'
				: isBookSummaryDoc ? 'book summary'
					: 'editorial';
		// The generated variant is determined by the credential's provider, so we
		// pass the selected provider's credential explicitly.
		const credentialId = selectedProviderCredential?.id ?? null;
		if (!credentialId) {
			setAiDocSnack({ severity: 'error', message: `No ${providerLabel(selectedProvider) || 'AI'} key is configured. Add one in Settings.`, persist: false });
			return Promise.resolve();
		}
		setAiDocSnack({ severity: 'info', message: `Producing ${label} with ${providerLabel(selectedProvider)}…`, persist: true });
		let p;
		if (isMemoryDoc) {
			p = generateMemoryAsync({ chapterId, bookId, credentialId, userGuidance });
		} else if (isChapterSummaryDoc) {
			p = generateChapterSummaryAsync({ chapterId, bookId, credentialId, userGuidance });
		} else if (isBookSummaryDoc) {
			p = generateBookSummaryAsync({ bookId, credentialId, userGuidance });
		} else if (isEditorialDoc) {
			p = generateEditorialAsync({ chapterId, bookId, credentialId, userGuidance });
		} else {
			setAiDocSnack(null);
			return Promise.resolve();
		}
		return p
			.then(() => setAiDocSnack({ severity: 'success', message: `${label.charAt(0).toUpperCase() + label.slice(1)} generated.`, persist: false }))
			.catch((e) => setAiDocSnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }));
	}, [isMemoryDoc, isChapterSummaryDoc, isBookSummaryDoc, isEditorialDoc, chapterId, bookId, aiDocGuidance,
		selectedProvider, selectedProviderCredential,
		generateMemoryAsync, generateChapterSummaryAsync, generateBookSummaryAsync, generateEditorialAsync]);

	// After the discard-content gate (if any), memory/book-summary generation
	// still checks the continuity/coverage gate before actually running.
	const proceedToAiDocGate = useCallback(() => {
		if (isMemoryDoc && aiDocFlaggedPreceding.length > 0) { setAiDocGateOpen(true); return; }
		if (isBookSummaryDoc && aiDocFlaggedChapters.length > 0) { setAiDocGateOpen(true); return; }
		doAiDocGenerate();
	}, [isMemoryDoc, isBookSummaryDoc, aiDocFlaggedPreceding, aiDocFlaggedChapters, doAiDocGenerate]);

	// First-ever generation has nothing to lose, so it skips straight to the
	// continuity/coverage gate; regenerating existing content warns first.
	const handleAiDocGenerateClick = useCallback(() => {
		if (aiDocCurrent) { setAiDocRegenConfirmOpen(true); return; }
		proceedToAiDocGate();
	}, [aiDocCurrent, proceedToAiDocGate]);

	const handleAiDocRegenConfirm = useCallback(() => {
		setAiDocRegenConfirmOpen(false);
		proceedToAiDocGate();
	}, [proceedToAiDocGate]);

	// Switching the provider variant is a selection change (kept in App's
	// selection so AiDocProperties stays in sync). We re-assert aiDocType and the
	// ancestors because setSelection nulls transient fields on every update.
	const handleAiDocProviderChange = useCallback((key) => {
		setSelection({
			projectId: projectId ?? null,
			bookId: bookId ?? null,
			partId: null,
			chapterId: isBookSummaryDoc ? null : (chapterId ?? null),
			sceneId: null,
			aiDocType,
			aiDocProvider: key,
		});
	}, [setSelection, projectId, bookId, chapterId, aiDocType, isBookSummaryDoc]);

	// Clears only the selected provider's variant. (The nav context-menu Clear
	// targets the user's default provider; this covers the other providers.)
	const handleAiDocClearProvider = useCallback(() => {
		if (!selectedProvider || !aiDocCurrent) return;
		const label = isMemoryDoc ? 'memory document'
			: isChapterSummaryDoc ? 'chapter summary'
				: isBookSummaryDoc ? 'book summary'
					: 'editorial';
		let p;
		if (isMemoryDoc) {
			p = deleteMemoryAsync({ chapterId, bookId, provider: selectedProvider });
		} else if (isChapterSummaryDoc) {
			p = deleteChapterSummaryAsync({ chapterId, bookId, provider: selectedProvider });
		} else if (isBookSummaryDoc) {
			p = deleteBookSummaryAsync({ bookId, provider: selectedProvider });
		} else if (isEditorialDoc) {
			p = deleteEditorialAsync({ chapterId, provider: selectedProvider });
		} else {
			return;
		}
		setAiDocSnack({ severity: 'info', message: `Clearing ${providerLabel(selectedProvider)} ${label}…`, persist: true });
		p.then(() => setAiDocSnack({ severity: 'success', message: `${providerLabel(selectedProvider)} ${label} cleared.`, persist: false }))
			.catch((e) => setAiDocSnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Clear failed.', persist: false }));
	}, [selectedProvider, aiDocCurrent, isMemoryDoc, isChapterSummaryDoc, isBookSummaryDoc, isEditorialDoc,
		chapterId, bookId, deleteMemoryAsync, deleteChapterSummaryAsync, deleteBookSummaryAsync, deleteEditorialAsync]);

	// ── render ────────────────────────────────────────────────────────────────

	// showEmptyState only when nothing at all is selected (not even a project),
	// OR when a codex container/category is selected (editing requires an entry).
	const showEmptyState =
		(!aiDocMode && !templateMode && !chapterId && !sceneId && !inPagePreviewMode && !projectShelfMode)
		|| (!!codexId && !sceneId && !templateMode && !aiDocMode);

	// Toolbar gets a live editor reference only when actually editing;
	// preview and shelf modes pass null so the gear icon stays accessible
	// but formatting controls are inactive.
	const toolbarEditor = projectShelfMode ? null : editor;
	const canSplitScene = multiSceneMode
		|| (singleSceneMode && !isCodexEntry && !!bookId && !!chapterId);

	const chapterHeadingTitle = (multiSceneMode && chapterData)
		? (chapterData.title?.trim() || `Chapter ${chapterData.chapterNumber}`)
		: null;
	const chapterHeadingSubtitle = (multiSceneMode && chapterData?.subtitle?.trim()) || null;

	return (
		<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

			<EditorToolbar
				editor={toolbarEditor}
				settings={settings}
				onSettingsChange={updateSettings}
				onSplitScene={canSplitScene ? handleSplitSceneClick : null}
				isSaving={isSaving}
				templateMode={templateMode}
				tokenOptions={tokenOptions}
				onInsertToken={handleInsertToken}
				previewActive={previewActive}
				onTogglePreview={handleTogglePreview}
				wordCountOverride={toolbarWordCountOverride}
				headingWordCount={multiSceneMode ? chapterHeadingWords : 0}
				paragraphCountOverride={toolbarParagraphCountOverride}
				headingParagraphCount={multiSceneMode ? chapterHeadingParagraphs : 0}
				pageConfig={toolbarPageConfig}
				canReview={!aiDocMode && !!chapterId && !!bookId && !codexId}
				isScene={!!sceneId}
				aiDocMode={aiDocMode}
				aiDocTypeLabel={aiDocTypeLabel}
				aiDocStatus={aiDocStatusChip}
				aiDocBusy={aiDocGenerating}
				aiDocHasContent={!!aiDocCurrent}
				aiDocGuidance={aiDocGuidance}
				onAiDocGuidanceChange={setAiDocGuidance}
				onAiDocGenerate={handleAiDocGenerateClick}
				aiDocCanGenerate={aiDocCanGenerate}
				aiDocProviderSelect={aiDocMode ? {
					value: selectedProvider,
					onChange: handleAiDocProviderChange,
					variants: aiDocVariants,
					credentialProviders: aiCredentialProviders,
					defaultProvider: defaultProviderKey,
					docTypeLabel: aiDocTypeLabel.toLowerCase(),
					onClearProvider: handleAiDocClearProvider,
				} : null}
				onOpenContextSettings={onOpenContextSettings}
				contextSettingsLabel={contextSettingsLabel}
			/>

			<SearchBar />

			{/* ── Content row: editor surface + optional review rail ───────── */}
			<Box sx={{ flex: 1, minHeight: 0, display: 'flex', overflow: 'hidden' }}>
				<Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

					{projectShelfMode ? (
						<ProjectShelf
							projectId={projectId}
							onSelectBook={onSelectBook}
						/>
					) : showEmptyState ? (
						<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'text.disabled' }}>
							<Typography variant="body1">
								{codexId
									? 'Select an entry to begin editing, or add a new one.'
									: 'Select a chapter or scene to begin editing.'}
							</Typography>
						</Box>
					) : isLoading ? (
						<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
							<CircularProgress size={28} />
						</Box>
					) : (
						<Box
							sx={{
								flex: 1,
								overflowY: 'auto',
								py: 5,
								px: 2,

								'--nkms-font-family': settings.fontFamily,
								'--nkms-font-size': settings.fontSize,
								'--nkms-line-height': settings.lineHeight,
								'--nkms-text-indent': templateMode ? '0px' : settings.firstLineIndent,
								'--nkms-spacing-after': settings.spacingAfter,

								'& .tiptap p': {
									textIndent: 'var(--nkms-text-indent)',
									marginBottom: 'var(--nkms-spacing-after)',
									marginTop: 0,
								},
								'& .tiptap': { outline: 'none' },
								'& .tiptap p.is-editor-empty:first-of-type::before': {
									content: 'attr(data-placeholder)',
									color: 'text.disabled',
									pointerEvents: 'none',
									float: 'left',
									height: 0,
								},
								'& .nkms-token': {
									display: 'inline-block',
									px: 0.5,
									borderRadius: 0.75,
									bgcolor: 'primary.main',
									color: 'primary.contrastText',
									fontSize: '0.8em',
									fontFamily: 'system-ui, -apple-system, sans-serif',
									lineHeight: 1.5,
									whiteSpace: 'nowrap',
									userSelect: 'none',
									verticalAlign: 'baseline',
								},
								'& .tiptap blockquote': {
									borderLeft: '3px solid',
									borderColor: 'divider',
									pl: 2,
									ml: 0,
									color: 'text.secondary',
									fontStyle: 'italic',
								},
								'& .tiptap hr': {
									border: 'none',
									textAlign: 'center',
									my: 3,
									'&::after': {
										content: '"· · ·"',
										color: 'text.disabled',
										letterSpacing: '0.5em',
									},
								},
								'& .tiptap h1': { fontSize: '1.6rem', fontWeight: 700, mt: 2, mb: 0.5 },
								'& .tiptap h2': { fontSize: '1.3rem', fontWeight: 700, mt: 2, mb: 0.5 },
								'& .tiptap h3': { fontSize: '1.1rem', fontWeight: 600, mt: 1.5, mb: 0.5 },
								'& .tiptap ul, & .tiptap ol': { pl: 3 },
								'& .nkms-search-match': { bgcolor: 'warning.light', borderRadius: '2px' },
								'& .nkms-search-active': { bgcolor: 'warning.main', outline: '2px solid', outlineColor: 'warning.dark' },
								'& .nkms-review-highlight': {
									bgcolor: 'info.light',
									borderRadius: '2px',
									outline: '2px solid',
									outlineColor: 'info.main',
									transition: 'background-color 0.3s ease',
								},
								'& .nkms-draft-heading': {
									minHeight: '1.5em',
									maxWidth: '72ch', mx: 'auto', px: 1, textAlign: 'center',
									userSelect: 'none', color: 'text.primary',
								},
								'& .nkms-draft-heading-part': { mt: 10, mb: 7 },
								'& .nkms-draft-heading-chapter': { mt: 7, mb: 5 },
								'& .nkms-draft-heading::before': { display: 'block', content: 'attr(data-title)', fontWeight: 700 },
								'& .nkms-draft-heading::after': { display: 'block', content: 'attr(data-subtitle)', mt: 0.75, fontSize: '1.1rem', fontStyle: 'italic', color: 'text.secondary' },
								'& .nkms-draft-heading[data-subtitle=""]::after': { display: 'none' },
								'& .nkms-draft-heading-part::before': { fontSize: '2rem' },
								'& .nkms-draft-heading-chapter::before': { fontSize: '1.75rem' },

								...styleSx,
							}}
						>
							{bookDraftMode && (
								<BookCoverPreview
									bookId={bookId}
									book={previewPageBook}
									project={previewPageProject}
									pageConfig={effectivePageConfig}
									settings={settings}
									embedded
								/>
							)}
							{partDraftMode && (
								<PartPagePreview
									partId={partId}
									bookId={bookId}
									book={previewPageBook}
									project={previewPageProject}
									pageConfig={effectivePageConfig}
									settings={settings}
									embedded
								/>
							)}

							<Box sx={{ display: showEditorPreview ? 'none' : 'block', mt: aggregateDraftMode ? 6 : 0 }}>

								{multiSceneMode && chapterHeadingTitle && (
									<Box
										sx={{
											textAlign: 'center',
											maxWidth: '72ch',
											mx: 'auto',
											px: 1,
											mb: 5,
										}}
									>
										<Typography
											sx={{
												fontFamily: 'var(--nkms-font-family)',
												fontSize: '1.75rem',
												fontWeight: 700,
												lineHeight: 1.2,
												color: 'text.primary',
											}}
										>
											{chapterHeadingTitle}
										</Typography>
										{chapterHeadingSubtitle && (
											<Typography
												sx={{
													fontFamily: 'var(--nkms-font-family)',
													fontSize: '1.1rem',
													fontWeight: 400,
													fontStyle: 'italic',
													color: 'text.secondary',
													mt: 0.75,
												}}
											>
												{chapterHeadingSubtitle}
											</Typography>
										)}
									</Box>
								)}

								{isCodexEntry && codexEntryHeadingTitle && (
									<Box
										sx={{
											textAlign: 'center',
											maxWidth: '72ch',
											mx: 'auto',
											px: 1,
											mb: 5,
										}}
									>
										<Typography
											sx={{
												fontFamily: 'var(--nkms-font-family)',
												fontSize: '1.75rem',
												fontWeight: 700,
												lineHeight: 1.2,
												color: 'text.primary',
											}}
										>
											{codexEntryHeadingTitle}
										</Typography>
									</Box>
								)}

								{isCodexEntry && codexEntrySchema?.fields?.length > 0 && singleScene && (
									<CodexEntryFields
										key={sceneId}
										sceneId={sceneId}
										schema={codexEntrySchema}
										initialData={singleScene.structuredData}
										entryTitle={singleScene.title}
										onBodyGenerated={(html) => {
											runEditorCommand(editor, ed => ed.commands.setContent(html, false));
										}}
									/>
								)}

								{aiDocMode && (
									<Box
										sx={{
											textAlign: 'center',
											maxWidth: '72ch',
											mx: 'auto',
											px: 1,
											mb: 5,
										}}
									>
										<Typography
											sx={{
												fontFamily: 'var(--nkms-font-family)',
												fontSize: '1.5rem',
												fontWeight: 700,
												fontStyle: 'italic',
												lineHeight: 1.2,
												color: 'text.secondary',
											}}
										>
											{aiDocTypeLabel}{aiDocHeadingLabel ? ` — ${aiDocHeadingLabel}` : ''}
										</Typography>
										{aiDocMetaLine && (
											<Typography
												variant="caption"
												sx={{ display: 'block', mt: 0.75, color: 'text.disabled' }}
											>
												{aiDocMetaLine}
											</Typography>
										)}
									</Box>
								)}

								{isCodexEntry && codexEntrySchema ? (
									<Box
										sx={{
											maxWidth: '72ch',
											mx: 'auto',
											width: '100%',
											p: 2.5,
											border: '1px solid',
											borderColor: 'divider',
											borderRadius: 2,
											bgcolor: 'background.paper',
										}}
									>
										<Typography
											variant="overline"
											sx={{ color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1.5 }}
										>
											Description
										</Typography>
										<Box
										    sx={{
										        border: '1px solid',
										        borderColor: 'divider',
										        borderRadius: 1,
										        p: 1.5,
										        minHeight: 120,
										        bgcolor: alpha('#FFFFFF', 0.55),
										    }}
										>
										    <EditorContent editor={editor} />
										</Box>
									</Box>
								) : (
									<EditorContent editor={editor} />
								)}
							</Box>

							{showEditorPreview && (
								<Box
									className="tiptap"
									sx={{
										fontFamily: 'var(--nkms-font-family)',
										fontSize: 'var(--nkms-font-size)',
										lineHeight: 'var(--nkms-line-height)',
										maxWidth: '72ch',
										mx: 'auto',
										px: 1,
									}}
									dangerouslySetInnerHTML={{ __html: previewHtml }}
								/>
							)}
						</Box>
					)}
				</Box>

				{reviewRailVisible && (
					<>
						<Box
							onMouseDown={handleRailResizeMouseDown}
							role="separator"
							aria-orientation="vertical"
							sx={{
								width: 8,
								flexShrink: 0,
								cursor: 'col-resize',
								position: 'relative',
								zIndex: 2,
								'&::after': {
									content: '""',
									position: 'absolute',
									top: 8,
									bottom: 8,
									left: '50%',
									width: 2,
									transform: 'translateX(-50%)',
									borderRadius: 2,
									bgcolor: 'transparent',
									transition: 'background-color 120ms ease',
								},
								'&:hover::after': {
									bgcolor: 'primary.main',
								},
							}}
						/>
						<ReviewRail key={chapterId} chapterId={chapterId} sceneId={sceneId} bookId={bookId} projectId={projectId} editor={editor} setSelection={setSelection} width={railWidth} />
					</>
				)}
			</Box>

			<SplitSceneDialog
				open={!!splitDraft}
				title={splitTitle}
				onTitleChange={setSplitTitle}
				onConfirm={handleSplitSceneConfirm}
				onClose={handleSplitSceneCancel}
				isPending={splitPending || isSaving}
			/>

			<RegenerateConfirmDialog
				open={aiDocRegenConfirmOpen}
				onCancel={() => setAiDocRegenConfirmOpen(false)}
				onConfirm={handleAiDocRegenConfirm}
			/>

			{isMemoryDoc && (
				<PreReviewMemoryDialog
					open={aiDocGateOpen}
					onCancel={() => setAiDocGateOpen(false)}
					onProceed={() => { setAiDocGateOpen(false); doAiDocGenerate(); }}
					flagged={aiDocFlaggedPreceding}
					bookId={bookId}
					credentialId={null}
					title="Earlier chapters’ memory is missing or out of date"
					intro="Memory documents read best as a complete chain in book order."
					proceedLabel="Generate anyway"
					regenerateLabel="Regenerate earlier first"
				/>
			)}

			{isBookSummaryDoc && (
				<PreBookSummaryDialog
					open={aiDocGateOpen}
					onCancel={() => setAiDocGateOpen(false)}
					onProceed={() => { setAiDocGateOpen(false); doAiDocGenerate(); }}
					flagged={aiDocFlaggedChapters}
					bookId={bookId}
					credentialId={null}
				/>
			)}

			<Snackbar
				open={!!aiDocSnack}
				autoHideDuration={aiDocSnack?.persist ? null : 4000}
				onClose={() => setAiDocSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{aiDocSnack ? (
					<Alert severity={aiDocSnack.severity} onClose={() => setAiDocSnack(null)} sx={{ width: '100%' }}>
						{aiDocSnack.message}
					</Alert>
				) : undefined}
			</Snackbar>

			<Snackbar
				open={!!splitSnack}
				autoHideDuration={4000}
				onClose={() => setSplitSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{splitSnack ? (
					<Alert severity={splitSnack.severity} onClose={() => setSplitSnack(null)} sx={{ width: '100%' }}>
						{splitSnack.message}
					</Alert>
				) : undefined}
			</Snackbar>
		</Box>
	);
}