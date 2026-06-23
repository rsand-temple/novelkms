import { useEffect, useRef, useCallback, useState, useMemo } from 'react';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import { ResizableImage } from '../../extensions/ResizableImage.jsx';
import { Box, Typography, CircularProgress } from '@mui/material';

import { StyledParagraph } from '../../extensions/StyledParagraph';
import { FontSize } from '../../extensions/FontSize';
import { SceneBreak } from '../../extensions/SceneBreak';
import { DraftHeading } from '../../extensions/DraftHeading';
import { TemplateToken } from '../../extensions/TemplateToken';
import { SearchHighlight, searchHighlightKey } from '../../extensions/SearchHighlight';
import { useProjectSettings } from '../../hooks/useProjectSettings';
import { useScenes, useScene, useDeleteScene, SCENE_KEYS } from '../../hooks/useScenes';
import { useDraftDocument, flattenDraftScenes } from '../../hooks/useDraftDocument';
import { useChapter } from '../../hooks/useChapters';
import { useGlobalTemplate, useBookTemplate, TEMPLATE_KEYS } from '../../hooks/useTemplates';
import { useBook } from '../../hooks/useBooks';
import client from '../../api/client';
import { useProject } from '../../hooks/useProjects';
import { useGlobalStyles, useBookStyles } from '../../hooks/useStyles';
import { scenesApi } from '../../api/scenes';
import { templatesApi } from '../../api/templates';
import { resolveValues, renderPreviewHtml, tokensForType } from '../../utils/tokenUtils';
import { buildStyleSx } from '../../utils/styles';
import { derivePageConfig } from '../../utils/pageConfig';
import EditorToolbar from '../editor/EditorToolbar';
import SearchBar from '../search/SearchBar';
import { useSearch } from '../../search/SearchContext';
import { useReview } from '../../review/ReviewContext';
import ReviewRail from '../ai/ReviewRail';
import { countHtmlOccurrences } from '../../search/searchUtils';
import BookCoverPreview from '../editor/BookCoverPreview';
import PartPagePreview from '../editor/PartPagePreview';
import ProjectShelf from '../editor/ProjectShelf';

const AUTOSAVE_DELAY_MS = 1500;

// Fallback page dimensions used when the book record hasn't loaded yet, or
// when the book has no page layout configured.  6" × 9" Trade Paperback at
// 96 DPI is a neutral default that gives a recognisable page shape without
// requiring the author to enable page layout first.
const DEFAULT_PAGE_CONFIG = {
	widthPx: 576,  // 6.0" × 96 dpi
	heightPx: 864,  // 9.0" × 96 dpi
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
 *   onSelectBook   — callback(bookId) invoked when the user clicks a book in
 *                    the project shelf (project home mode).
 *
 * Modes (priority order):
 *   1. template mode      — templateType set
 *   2. book cover preview — bookId set, no partId/chapterId/sceneId/template
 *   3. part page preview  — partId set, no chapterId/sceneId/template
 *   4. project shelf      — projectId set, no bookId (project home / book picker)
 *   5. single-scene mode  — sceneId set; no chapter heading
 *   6. multi-scene mode   — chapterId set; chapter title/subtitle shown above prose
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
	templateType, templateScope, onSelectBook,
}) {
	const { settings, updateSettings } = useProjectSettings(projectId);
	const queryClient = useQueryClient();
	const search = useSearch();
	const review = useReview();

	// ── Mode flags ────────────────────────────────────────────────────────────
	const templateMode = !!templateType;
	const singleSceneMode = !templateMode && !!sceneId;
	const multiSceneMode = !templateMode && !singleSceneMode && !!chapterId && !codexId;
	const partDraftMode = !templateMode && !chapterId && !sceneId && !!partId && !!bookId;
	const bookDraftMode = !templateMode && !partId && !chapterId && !sceneId && !!bookId;
	const aggregateDraftMode = partDraftMode || bookDraftMode;

	// Review Mode is a layer on top of normal chapter editing: the rail shows
	// the selected manuscript chapter's AI review. It appears only for a
	// manuscript chapter (a chapter inside a book, never a codex entry), which
	// covers both multi-scene (chapter selected) and single-scene (a scene
	// within that chapter) editing.
	const reviewRailVisible = review.open && !!chapterId && !!bookId && !codexId;

	const isGlobalTpl = templateMode && templateScope === 'global';
	const isBookTpl = templateMode && templateScope === 'book';

	// Page-layout preview: fires whenever a book (or part within a book) is
	// selected with no chapter/scene/template active — regardless of whether
	// page layout is configured on the book.
	const pagePreviewEligible = !templateMode && !chapterId && !sceneId && !!bookId;

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
	const projectShelfMode = !templateMode && !bookId && !chapterId && !sceneId && !!projectId;

	// ── Chapter data for heading ───────────────────────────────────────────────
	// Fetched in multi-scene mode (chapter title/subtitle heading) AND in
	// single-scene mode (to read codexCategory off the parent chapter so we can
	// tell a codex entry apart from a manuscript scene — see isCodexEntry below).
	const { data: chapterData } = useChapter((multiSceneMode || singleSceneMode) ? chapterId : null);

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
	const { data: projectWordCount } = useQuery({
		queryKey: ['projects', projectId, 'word-count'],
		queryFn: () => client.get(`/projects/${projectId}/word-count`).then(r => r.data.wordCount),
		enabled: !!projectId && projectShelfMode,
		staleTime: 60_000,
	});

	const { data: bookWordCount } = useQuery({
		queryKey: ['books', bookId, 'word-count'],
		queryFn: () => client.get(`/books/${bookId}/word-count`).then(r => r.data.wordCount),
		// Enabled for book draft/cover preview (status bar) and book-scope
		// template mode (WORDS token in template editor preview).
		enabled: !!bookId && (bookDraftMode || templateMode),
		staleTime: 60_000,
	});

	const { data: partWordCount } = useQuery({
		queryKey: ['parts', partId, 'word-count'],
		queryFn: () => client.get(`/parts/${partId}/word-count`).then(r => r.data.wordCount),
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

	// Override the toolbar word count for modes where there is no live TipTap
	// editor (or the TipTap count is stale/irrelevant).
	const toolbarWordCountOverride = projectShelfMode
		? (projectWordCount ?? 0)
		: (bookDraftMode || bookCoverMode)
			? (bookWordCount ?? 0)
			: partDraftMode
				? (partWordCount ?? 0)
				: null;

	const isLoading = templateMode
		? templateLoading
		: singleSceneMode
			? singleSceneLoading
			: aggregateDraftMode
				? draftLoading
				: scenesLoading;

	const { mutate: deleteScene } = useDeleteScene();

	const [isSaving, setIsSaving] = useState(false);
	const [previewActive, setPreviewActive] = useState(false);

	const showEditorPreview = templateMode && previewActive;

	// ── refs ─────────────────────────────────────────────────────────────────
	const saveTimer = useRef(null);
	const firstSceneIdRef = useRef(null);
	const prevSceneBreakIdsRef = useRef([]);
	const loadedChapterIdRef = useRef(null);
	const loadedSceneOrderRef = useRef('');
	const loadedSceneIdRef = useRef(null);
	const loadedTemplateKeyRef = useRef(null);
	const singleSceneModeRef = useRef(singleSceneMode);
	const templateModeRef = useRef(templateMode);
	const templateScopeRef = useRef(templateScope);
	const templateTypeRef = useRef(templateType);
	const bookIdRef = useRef(bookId);
	const sceneIdRef = useRef(sceneId);
	const scheduleSaveRef = useRef(null);
	const chapterIdRef = useRef(chapterId);
	const editorRef = useRef(null);
	const searchRef = useRef(search);
	const aggregateDraftModeRef = useRef(aggregateDraftMode);
	const expectedSceneIdsRef = useRef([]);
	const activeScenesRef = useRef([]);

	useEffect(() => { chapterIdRef.current = chapterId; }, [chapterId]);
	useEffect(() => { singleSceneModeRef.current = singleSceneMode; }, [singleSceneMode]);
	useEffect(() => { templateModeRef.current = templateMode; }, [templateMode]);
	useEffect(() => { templateScopeRef.current = templateScope; }, [templateScope]);
	useEffect(() => { templateTypeRef.current = templateType; }, [templateType]);
	useEffect(() => { bookIdRef.current = bookId; }, [bookId]);
	useEffect(() => { sceneIdRef.current = sceneId; }, [sceneId]);
	useEffect(() => { searchRef.current = search; }, [search]);
	useEffect(() => { aggregateDraftModeRef.current = aggregateDraftMode; }, [aggregateDraftMode]);
	useEffect(() => {
		expectedSceneIdsRef.current = (activeScenes || []).map(s => s.id);
		activeScenesRef.current = activeScenes || [];
	}, [activeScenes]);

	useEffect(() => { if (activeScenes?.length) firstSceneIdRef.current = activeScenes[0].id; }, [activeScenes]);

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
		loadedChapterIdRef.current = null;
		loadedSceneOrderRef.current = '';
		loadedSceneIdRef.current = null;
		prevSceneBreakIdsRef.current = [];
	}, [templateMode, templateType, templateScope, bookId, partId]);

	// ── save ─────────────────────────────────────────────────────────────────

	const scheduleSave = useCallback((html) => {
		if (saveTimer.current) clearTimeout(saveTimer.current);
		saveTimer.current = setTimeout(async () => {
			setIsSaving(true);
			try {
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
					const firstId = firstSceneIdRef.current;
					if (!firstId) return;
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
			Placeholder.configure({ placeholder: 'Begin your scene…' }),
			CharacterCount,
			SearchHighlight,
		],
		content: '',
		onUpdate: ({ editor }) => {
			if (!singleSceneModeRef.current && !templateModeRef.current && !aggregateDraftModeRef.current) {
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
			if (liveSearch.open && liveSearch.query && !templateModeRef.current) {
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
	});

	useEffect(() => { editorRef.current = editor; }, [editor]);

	// Keep the transient ProseMirror search decorations synchronized with the
	// shared search bar. Decorations never enter the stored HTML.
	useEffect(() => {
		if (!editor) return;
		if (!search.open || !search.query || templateMode) {
			editor.commands.clearSearch();
			return;
		}
		// Search navigation must never change the manuscript selection. In
		// chapter mode the editor contains every scene in the chapter, so the
		// provider's flattened match index is already the correct document-wide
		// index. In single-scene mode it is likewise local to that scene.
		const editorMatchIndex = search.activeIndex >= 0 ? search.activeIndex : 0;
		editor.commands.setSearch({
			query: search.query,
			matchCase: search.matchCase,
			activeIndex: editorMatchIndex,
		});
		if (search.totalCount > 0) {
			requestAnimationFrame(() => editor.commands.scrollToSearchMatch(editorMatchIndex));
		}
	}, [editor, search.open, search.query, search.matchCase, search.activeIndex, search.totalCount, templateMode]);

	const { registerEditorActions } = search;

	useEffect(() => {
		if (!editor) return;

		registerEditorActions({
			next: () => editor.commands.goToNextSearchMatch(),
			previous: () => editor.commands.goToPreviousSearchMatch(),
			replaceCurrent: (replacement) =>
				editor.commands.replaceCurrentSearchMatch(replacement),
			replaceAll: (replacement) =>
				editor.commands.replaceAllSearchMatches(replacement),
		});

		return () => {
			registerEditorActions(null);
		};
	}, [editor, registerEditorActions]);

	// ── scene break insertion ─────────────────────────────────────────────────

	const handleSceneBreak = useCallback(async () => {
		const cid = chapterIdRef.current;
		const firstId = firstSceneIdRef.current;
		if (!cid || !firstId || !editorRef.current) return;
		try {
			const newScene = await scenesApi.create(cid, { title: '' });
			const ed = editorRef.current;
			ed.chain().focus().setSceneBreak({ sceneId: newScene.id }).run();
			const breakIds = getDocSceneBreakIds(ed);
			const orderedIds = [firstId, ...breakIds];
			await scenesApi.reorderInChapter(cid, orderedIds);
			loadedSceneOrderRef.current = orderedIds.join(',');
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(cid) });
		} catch (err) {
			console.error('[EditorPanel] Failed to insert scene break:', err);
		}
	}, [queryClient]);

	// ── token insertion + preview ─────────────────────────────────────────────

	const tokenOptions = useMemo(
		() => (templateMode ? tokensForType(templateType) : []),
		[templateMode, templateType]
	);
	const handleInsertToken = useCallback((token) => {
		editorRef.current?.chain().focus().insertTemplateToken({ token }).run();
	}, []);
	const handleTogglePreview = useCallback(() => setPreviewActive(p => !p), []);

	const previewValues = useMemo(
		() => resolveValues({ scope: templateScope, book: previewBook, project: previewProject, wordCount: bookWordCount ?? null }),
		[templateScope, previewBook, previewProject, bookWordCount]
	);
	const previewHtml = useMemo(() => {
		if (!showEditorPreview || !editor) return '';
		return renderPreviewHtml(editor.getHTML(), previewValues);
	}, [showEditorPreview, previewValues, editor]);

	const styleSx = useMemo(() => buildStyleSx(styleSheet), [styleSheet]);

	// ── load content ─────────────────────────────────────────────────────────

	useEffect(() => {
		if (!editor) return;

		if (templateMode) {
			if (!template) return;
			const key = templateKey(template);
			if (loadedTemplateKeyRef.current === key) return;
			editor.commands.setContent(template.content || '<p></p>', false);
			prevSceneBreakIdsRef.current = [];
			loadedTemplateKeyRef.current = key;
			return;
		}

		if (singleSceneMode) {
			if (!singleScene) return;
			if (loadedSceneIdRef.current === sceneId) return;
			editor.commands.setContent(singleScene.content || '<p></p>', false);
			prevSceneBreakIdsRef.current = [];
			loadedSceneIdRef.current = sceneId;
			return;
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
			editor.commands.setContent('', false);
			return;
		}

		const html = buildCombinedHTML(contentScenes, aggregateDraftMode ? draftDocument : null);
		prevSceneBreakIdsRef.current = contentScenes.slice(1).map(s => s.id);
		loadedChapterIdRef.current = scopeKey;
		loadedSceneOrderRef.current = newOrder;
		editor.commands.setContent(html, false);
	}, [editor, scenes, activeScenes, draftDocument, aggregateDraftMode, partDraftMode, partId, bookId, chapterId, singleScene, sceneId, singleSceneMode, templateMode, template]);

	useEffect(() => () => { if (saveTimer.current) clearTimeout(saveTimer.current); }, []);

	// ── render ────────────────────────────────────────────────────────────────

	// showEmptyState only when nothing at all is selected (not even a project),
	// OR when a codex container/category is selected (editing requires an entry).
	const showEmptyState =
		(!templateMode && !chapterId && !sceneId && !inPagePreviewMode && !projectShelfMode)
		|| (!!codexId && !sceneId && !templateMode);

	// Toolbar gets a live editor reference only when actually editing;
	// preview and shelf modes pass null so the gear icon stays accessible
	// but formatting controls are inactive.
	const toolbarEditor = projectShelfMode ? null : editor;

	const chapterHeadingTitle = (multiSceneMode && chapterData)
		? (chapterData.title?.trim() || `Chapter ${chapterData.chapterNumber}`)
		: null;
	const chapterHeadingSubtitle = (multiSceneMode && chapterData?.subtitle?.trim()) || null;

	// A codex entry is a scene (single-scene mode) whose parent chapter is a
	// codex category — ground-truthed via chapterData.codexCategory rather than
	// selection.codexId (see the mode comment above the component for why).
	const isCodexEntry = singleSceneMode && !!chapterData?.codexCategory;
	const codexEntryHeadingTitle = isCodexEntry
		? (singleScene?.title?.trim() || 'Untitled Entry')
		: null;

	return (
		<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

			<EditorToolbar
				editor={toolbarEditor}
				settings={settings}
				onSettingsChange={updateSettings}
				onSceneBreak={(singleSceneMode || templateMode || aggregateDraftMode || projectShelfMode) ? null : handleSceneBreak}
				isSaving={isSaving}
				templateMode={templateMode}
				tokenOptions={tokenOptions}
				onInsertToken={handleInsertToken}
				previewActive={previewActive}
				onTogglePreview={handleTogglePreview}
				wordCountOverride={toolbarWordCountOverride}
				headingWordCount={multiSceneMode ? chapterHeadingWords : 0}
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

						<EditorContent editor={editor} />
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
				<ReviewRail key={chapterId} chapterId={chapterId} />
			)}
			</Box>
		</Box>
	);
}