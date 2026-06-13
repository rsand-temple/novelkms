import { useEffect, useRef, useCallback, useState, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
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
import { TemplateToken } from '../../extensions/TemplateToken';
import { useProjectSettings } from '../../hooks/useProjectSettings';
import { useScenes, useScene, useDeleteScene, SCENE_KEYS } from '../../hooks/useScenes';
import { useChapter } from '../../hooks/useChapters';
import { useGlobalTemplate, useBookTemplate, TEMPLATE_KEYS } from '../../hooks/useTemplates';
import { useBook } from '../../hooks/useBooks';
import { useProject } from '../../hooks/useProjects';
import { useGlobalStyles, useBookStyles } from '../../hooks/useStyles';
import { scenesApi } from '../../api/scenes';
import { templatesApi } from '../../api/templates';
import { resolveValues, renderPreviewHtml, tokensForType } from '../../utils/templateTokens';
import { buildStyleSx } from '../../utils/styles';
import { derivePageConfig } from '../../utils/pageConfig';
import EditorToolbar from '../editor/EditorToolbar';
import BookCoverPreview from '../editor/BookCoverPreview';
import PartPagePreview from '../editor/PartPagePreview';

const AUTOSAVE_DELAY_MS = 1500;

// Fallback page dimensions used when the book record hasn't loaded yet, or
// when the book has no page layout configured.  6" × 9" Trade Paperback at
// 96 DPI is a neutral default that gives a recognisable page shape without
// requiring the author to enable page layout first.
const DEFAULT_PAGE_CONFIG = {
	widthPx:        576,  // 6.0" × 96 dpi
	heightPx:       864,  // 9.0" × 96 dpi
	marginTopPx:     96,  // 1.0"
	marginBottomPx:  96,  // 1.0"
	marginInnerPx:  120,  // 1.25"
	marginOuterPx:   96,  // 1.0"
};

// ── helpers ───────────────────────────────────────────────────────────────────

function buildCombinedHTML(scenes) {
	if (!scenes?.length) return '';
	return scenes.map((scene, i) => {
		const content = scene.content || '<p></p>';
		if (i < scenes.length - 1) {
			return content + `<hr data-scene-after="${scenes[i + 1].id}">`;
		}
		return content;
	}).join('');
}

function parseSceneChunks(html, firstSceneId) {
	const chunks = [];
	const re = /<hr[^>]+data-scene-after="([^"]+)"[^>]*\/?>/gi;
	let lastIndex = 0;
	let currentSceneId = firstSceneId;
	let match;

	while ((match = re.exec(html)) !== null) {
		chunks.push({
			sceneId: currentSceneId,
			content: html.slice(lastIndex, match.index).trim() || '<p></p>',
		});
		currentSceneId = match[1];
		lastIndex = match.index + match[0].length;
	}
	chunks.push({
		sceneId: currentSceneId,
		content: html.slice(lastIndex).trim() || '<p></p>',
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
 *
 * Modes (priority order):
 *   1. template mode      — templateType set
 *   2. book cover preview — bookId set, no partId/chapterId/sceneId/template
 *   3. part page preview  — partId set, no chapterId/sceneId/template
 *   4. single-scene mode  — sceneId set; no chapter heading
 *   5. multi-scene mode   — chapterId set; chapter title/subtitle shown above prose
 *
 * The book cover and part page previews activate whenever a book or part is
 * selected, regardless of whether page layout is enabled on the book.  When
 * page layout is not configured, DEFAULT_PAGE_CONFIG provides fallback
 * dimensions (6" × 9" Trade Paperback) so the canvas always renders.
 */
export default function EditorPanel({ partId, chapterId, sceneId, projectId, bookId, templateType, templateScope }) {
	const { settings, updateSettings } = useProjectSettings(projectId);
	const queryClient = useQueryClient();

	// ── Mode flags ────────────────────────────────────────────────────────────
	const templateMode    = !!templateType;
	const singleSceneMode = !templateMode && !!sceneId;
	const multiSceneMode  = !templateMode && !singleSceneMode && !!chapterId;

	const isGlobalTpl = templateMode && templateScope === 'global';
	const isBookTpl   = templateMode && templateScope === 'book';

	// Page-layout preview: fires whenever a book (or part within a book) is
	// selected with no chapter/scene/template active — regardless of whether
	// page layout is configured on the book.
	const pagePreviewEligible = !templateMode && !chapterId && !sceneId && !!bookId;

	const { data: previewPageBook }    = useBook(pagePreviewEligible ? bookId : null);
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
	const bookCoverMode    = pagePreviewEligible && !partId;
	// Part page: a part within the book is selected.
	const partPageMode     = pagePreviewEligible && !!partId;
	const inPagePreviewMode = bookCoverMode || partPageMode;

	// ── Chapter data for heading (multi-scene mode only) ──────────────────────
	const { data: chapterData } = useChapter(multiSceneMode ? chapterId : null);

	// ── Scene / template data ─────────────────────────────────────────────────
	const { data: scenes,      isLoading: scenesLoading      } = useScenes(multiSceneMode ? chapterId : null);
	const { data: singleScene, isLoading: singleSceneLoading } = useScene(singleSceneMode ? sceneId : null);

	const { data: globalTpl, isLoading: globalTplLoading } = useGlobalTemplate(templateType, isGlobalTpl);
	const { data: bookTpl,   isLoading: bookTplLoading   } = useBookTemplate(bookId, templateType, isBookTpl);
	const template        = isGlobalTpl ? globalTpl : (isBookTpl ? bookTpl : null);
	const templateLoading = (isGlobalTpl && globalTplLoading) || (isBookTpl && bookTplLoading);

	const { data: previewBook }    = useBook(isBookTpl ? bookId : null);
	const { data: previewProject } = useProject(projectId);

	const { data: bookStyleSheet }   = useBookStyles(bookId, !!bookId);
	const { data: globalStyleSheet } = useGlobalStyles(!bookId);
	const styleSheet = bookId ? bookStyleSheet : globalStyleSheet;

	const isLoading = templateMode
		? templateLoading
		: singleSceneMode
			? singleSceneLoading
			: scenesLoading;

	const { mutate: deleteScene } = useDeleteScene();

	const [isSaving, setIsSaving]           = useState(false);
	const [previewActive, setPreviewActive] = useState(false);

	const showEditorPreview = templateMode && previewActive;

	// ── refs ─────────────────────────────────────────────────────────────────
	const saveTimer             = useRef(null);
	const firstSceneIdRef       = useRef(null);
	const prevSceneBreakIdsRef  = useRef([]);
	const loadedChapterIdRef    = useRef(null);
	const loadedSceneOrderRef   = useRef('');
	const loadedSceneIdRef      = useRef(null);
	const loadedTemplateKeyRef  = useRef(null);
	const singleSceneModeRef    = useRef(singleSceneMode);
	const templateModeRef       = useRef(templateMode);
	const templateScopeRef      = useRef(templateScope);
	const templateTypeRef       = useRef(templateType);
	const bookIdRef             = useRef(bookId);
	const sceneIdRef            = useRef(sceneId);
	const scheduleSaveRef       = useRef(null);
	const chapterIdRef          = useRef(chapterId);
	const editorRef             = useRef(null);

	useEffect(() => { chapterIdRef.current       = chapterId;       }, [chapterId]);
	useEffect(() => { singleSceneModeRef.current = singleSceneMode; }, [singleSceneMode]);
	useEffect(() => { templateModeRef.current    = templateMode;    }, [templateMode]);
	useEffect(() => { templateScopeRef.current   = templateScope;   }, [templateScope]);
	useEffect(() => { templateTypeRef.current    = templateType;    }, [templateType]);
	useEffect(() => { bookIdRef.current          = bookId;          }, [bookId]);
	useEffect(() => { sceneIdRef.current         = sceneId;         }, [sceneId]);
	useEffect(() => { if (scenes?.length) firstSceneIdRef.current = scenes[0].id; }, [scenes]);

	useEffect(() => {
		if (singleSceneMode) {
			loadedChapterIdRef.current  = null;
			loadedSceneOrderRef.current = '';
		} else {
			loadedSceneIdRef.current = null;
		}
	}, [singleSceneMode]);

	useEffect(() => {
		if (saveTimer.current) { clearTimeout(saveTimer.current); saveTimer.current = null; }
		loadedTemplateKeyRef.current = null;
		loadedChapterIdRef.current   = null;
		loadedSceneOrderRef.current  = '';
		loadedSceneIdRef.current     = null;
		prevSceneBreakIdsRef.current = [];
	}, [templateMode, templateType, templateScope, bookId]);

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
					await scenesApi.updateContent(sid, html);
					queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(sid) });
				} else {
					const firstId = firstSceneIdRef.current;
					if (!firstId) return;
					const chunks = parseSceneChunks(html, firstId);
					if (!chunks.length) return;
					await Promise.all(chunks.map(c => scenesApi.updateContent(c.sceneId, c.content)));
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
		],
		content: '',
		onUpdate: ({ editor }) => {
			if (!singleSceneModeRef.current && !templateModeRef.current) {
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
			scheduleSaveRef.current?.(editor.getHTML());
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
	const handleInsertToken   = useCallback((token) => {
		editorRef.current?.chain().focus().insertTemplateToken({ token }).run();
	}, []);
	const handleTogglePreview = useCallback(() => setPreviewActive(p => !p), []);

	const previewValues = useMemo(
		() => resolveValues({ scope: templateScope, book: previewBook, project: previewProject }),
		[templateScope, previewBook, previewProject]
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

		if (!scenes) return;

		const newOrder = scenes.map(s => s.id).join(',');
		const chapterChanged = loadedChapterIdRef.current !== chapterId;
		const orderChanged   = newOrder !== loadedSceneOrderRef.current;

		if (!chapterChanged && !orderChanged) return;
		if (chapterChanged && saveTimer.current) { clearTimeout(saveTimer.current); saveTimer.current = null; }

		if (scenes.length === 0) {
			loadedChapterIdRef.current   = chapterId;
			loadedSceneOrderRef.current  = '';
			prevSceneBreakIdsRef.current = [];
			editor.commands.setContent('', false);
			return;
		}

		const html = buildCombinedHTML(scenes);
		prevSceneBreakIdsRef.current = scenes.slice(1).map(s => s.id);
		loadedChapterIdRef.current   = chapterId;
		loadedSceneOrderRef.current  = newOrder;
		editor.commands.setContent(html, false);
	}, [editor, scenes, chapterId, singleScene, sceneId, singleSceneMode, templateMode, template]);

	useEffect(() => () => { if (saveTimer.current) clearTimeout(saveTimer.current); }, []);

	// ── render ────────────────────────────────────────────────────────────────

	const showEmptyState = !templateMode && !chapterId && !sceneId && !inPagePreviewMode;
	const toolbarEditor  = inPagePreviewMode ? null : editor;

	const chapterHeadingTitle    = chapterData
		? (chapterData.title?.trim() || `Chapter ${chapterData.chapterNumber}`)
		: null;
	const chapterHeadingSubtitle = chapterData?.subtitle?.trim() || null;

	return (
		<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

			<EditorToolbar
				editor={toolbarEditor}
				settings={settings}
				onSettingsChange={updateSettings}
				onSceneBreak={(singleSceneMode || templateMode || inPagePreviewMode) ? null : handleSceneBreak}
				isSaving={isSaving}
				templateMode={templateMode}
				tokenOptions={tokenOptions}
				onInsertToken={handleInsertToken}
				previewActive={previewActive}
				onTogglePreview={handleTogglePreview}
			/>

			{/* ── Content area ─────────────────────────────────────────────── */}

			{bookCoverMode ? (
				<BookCoverPreview
					bookId={bookId}
					book={previewPageBook}
					project={previewPageProject}
					pageConfig={effectivePageConfig}
					settings={settings}
				/>
			) : partPageMode ? (
				<PartPagePreview
					partId={partId}
					bookId={bookId}
					book={previewPageBook}
					project={previewPageProject}
					pageConfig={effectivePageConfig}
					settings={settings}
				/>
			) : showEmptyState ? (
				<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'text.disabled' }}>
					<Typography variant="body1">Select a chapter or scene to begin editing.</Typography>
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

						'--nkms-font-family':   settings.fontFamily,
						'--nkms-font-size':     settings.fontSize,
						'--nkms-line-height':   settings.lineHeight,
						'--nkms-text-indent':   templateMode ? '0px' : settings.firstLineIndent,
						'--nkms-spacing-after': settings.spacingAfter,

						'& .tiptap p': {
							textIndent:   'var(--nkms-text-indent)',
							marginBottom: 'var(--nkms-spacing-after)',
							marginTop:    0,
						},
						'& .tiptap': { outline: 'none' },
						'& .tiptap p.is-editor-empty:first-of-type::before': {
							content:       'attr(data-placeholder)',
							color:         'text.disabled',
							pointerEvents: 'none',
							float:         'left',
							height:        0,
						},
						'& .nkms-token': {
							display:       'inline-block',
							px:            0.5,
							borderRadius:  0.75,
							bgcolor:       'primary.main',
							color:         'primary.contrastText',
							fontSize:      '0.8em',
							fontFamily:    'system-ui, -apple-system, sans-serif',
							lineHeight:    1.5,
							whiteSpace:    'nowrap',
							userSelect:    'none',
							verticalAlign: 'baseline',
						},
						'& .tiptap blockquote': {
							borderLeft:  '3px solid',
							borderColor: 'divider',
							pl:          2,
							ml:          0,
							color:       'text.secondary',
							fontStyle:   'italic',
						},
						'& .tiptap hr': {
							border:    'none',
							textAlign: 'center',
							my:        3,
							'&::after': {
								content:       '"· · ·"',
								color:         'text.disabled',
								letterSpacing: '0.5em',
							},
						},
						'& .tiptap h1': { fontSize: '1.6rem', fontWeight: 700, mt: 2, mb: 0.5 },
						'& .tiptap h2': { fontSize: '1.3rem', fontWeight: 700, mt: 2, mb: 0.5 },
						'& .tiptap h3': { fontSize: '1.1rem', fontWeight: 600, mt: 1.5, mb: 0.5 },
						'& .tiptap ul, & .tiptap ol': { pl: 3 },


						...styleSx,
					}}
				>
					<Box sx={{ display: showEditorPreview ? 'none' : 'block' }}>

						{multiSceneMode && chapterHeadingTitle && (
							<Box
								sx={{
									textAlign: 'center',
									maxWidth:  '72ch',
									mx:        'auto',
									px:        1,
									mb:        5,
								}}
							>
								<Typography
									sx={{
										fontFamily: 'var(--nkms-font-family)',
										fontSize:   '1.75rem',
										fontWeight: 700,
										lineHeight: 1.2,
										color:      'text.primary',
									}}
								>
									{chapterHeadingTitle}
								</Typography>
								{chapterHeadingSubtitle && (
									<Typography
										sx={{
											fontFamily: 'var(--nkms-font-family)',
											fontSize:   '1.1rem',
											fontWeight: 400,
											fontStyle:  'italic',
											color:      'text.secondary',
											mt:         0.75,
										}}
									>
										{chapterHeadingSubtitle}
									</Typography>
								)}
							</Box>
						)}

						<EditorContent editor={editor} />
					</Box>

					{showEditorPreview && (
						<Box
							className="tiptap"
							sx={{
								fontFamily: 'var(--nkms-font-family)',
								fontSize:   'var(--nkms-font-size)',
								lineHeight: 'var(--nkms-line-height)',
								maxWidth:   '72ch',
								mx:         'auto',
								px:         1,
							}}
							dangerouslySetInnerHTML={{ __html: previewHtml }}
						/>
					)}
				</Box>
			)}
		</Box>
	);
}