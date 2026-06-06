import { useEffect, useRef, useCallback, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import { Box, Typography, CircularProgress } from '@mui/material';

import { StyledParagraph } from '../../extensions/StyledParagraph';
import { FontSize } from '../../extensions/FontSize';
import { SceneBreak } from '../../extensions/SceneBreak';
import { useProjectSettings } from '../../hooks/useProjectSettings';
import { useScenes, useCreateScene, useDeleteScene } from '../../hooks/useScenes';
import { scenesApi } from '../../api/scenes';
import EditorToolbar from '../editor/EditorToolbar';

const AUTOSAVE_DELAY_MS = 1500;

// ── helpers ───────────────────────────────────────────────────────────────────

/**
 * Build the combined HTML that the editor will display for a whole chapter.
 * Scenes are joined by <hr data-scene-after="nextSceneId"> markers.
 * The first scene's content has no preceding HR; the last has no trailing HR.
 */
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

/**
 * Split editor HTML into per-scene chunks using <hr data-scene-after="id">
 * as delimiters.  The first chunk always belongs to firstSceneId.
 * Returns [{ sceneId, content }, ...] in document order.
 */
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

/**
 * Collect the sceneId values of every SceneBreak node currently in the doc.
 * Used to detect deleted scene boundaries between onUpdate calls.
 */
function getDocSceneBreakIds(editor) {
	const ids = [];
	editor.state.doc.descendants((node) => {
		if (node.type.name === 'sceneBreak' && node.attrs.sceneId) {
			ids.push(node.attrs.sceneId);
		}
	});
	return ids;
}

// ── component ─────────────────────────────────────────────────────────────────

/**
 * EditorPanel
 *
 * Props:
 *   chapterId  — ID of the currently selected chapter.  The panel loads and
 *                edits ALL scenes for this chapter as a single concatenated
 *                document, using SceneBreak nodes as boundaries.
 *   projectId  — ID of the current project (drives useProjectSettings).
 *
 * Scene break lifecycle
 *   Insert: user clicks the toolbar button → DB scene is created first →
 *           SceneBreak node inserted with new scene's ID → onUpdate saves →
 *           scenes query invalidated → nav tree shows new scene.
 *
 *   Delete: user deletes the HR node → onUpdate detects the missing sceneId →
 *           DB scene deleted → nav tree pruned → merged content saved to the
 *           absorbing scene.
 */
export default function EditorPanel({ chapterId, projectId }) {
	const { settings, updateSettings } = useProjectSettings(projectId);

	const { data: scenes, isLoading: scenesLoading } = useScenes(chapterId);
	const { mutateAsync: createScene } = useCreateScene();
	const { mutate: deleteScene } = useDeleteScene();

	// Local saving state (we call scenesApi directly for parallel multi-scene saves)
	const [isSaving, setIsSaving] = useState(false);

	// ── refs ─────────────────────────────────────────────────────────────────
	const saveTimer = useRef(null);
	const firstSceneIdRef = useRef(null);   // ID of scene[0] in the current chapter
	const prevSceneBreakIdsRef = useRef([]);      // SceneBreak IDs from the last onUpdate tick
	const loadedChapterIdRef = useRef(null);    // Guard: only reload editor on chapter switch
	// Stable refs used inside useEditor callbacks to avoid stale closures
	const scheduleSaveRef = useRef(null);
	const chapterIdRef = useRef(chapterId);
	const editorRef = useRef(null);

	// Keep mutable refs current on every render
	useEffect(() => { chapterIdRef.current = chapterId; }, [chapterId]);
	useEffect(() => { if (scenes?.length) firstSceneIdRef.current = scenes[0].id; }, [scenes]);
	
	// ── save ──────────────────────────────────────────────────────────────────

	const scheduleSave = useCallback((html) => {
		if (saveTimer.current) clearTimeout(saveTimer.current);
		saveTimer.current = setTimeout(async () => {
			const firstId = firstSceneIdRef.current;
			if (!firstId) return;
			const chunks = parseSceneChunks(html, firstId);
			if (!chunks.length) return;
			setIsSaving(true);
			try {
				await Promise.all(
					chunks.map(c => scenesApi.updateContent(c.sceneId, c.content))
				);
			} catch (err) {
				console.error('[EditorPanel] Scene save failed:', err);
			} finally {
				setIsSaving(false);
			}
		}, AUTOSAVE_DELAY_MS);
	}, []); // No deps — reads everything via refs

	useEffect(() => { scheduleSaveRef.current = scheduleSave; }, [scheduleSave]);

	// ── editor ────────────────────────────────────────────────────────────────

	const editor = useEditor({
		extensions: [
			StarterKit.configure({
				paragraph: false, // → StyledParagraph
				underline: false, // → explicit Underline import
				horizontalRule: false, // → SceneBreak
			}),
			StyledParagraph,
			FontSize,
			SceneBreak,
			Underline,
			TextAlign.configure({ types: ['heading', 'paragraph'], defaultAlignment: 'left' }),
			Placeholder.configure({ placeholder: 'Begin your scene…' }),
			CharacterCount,
		],
		content: '',
		onUpdate: ({ editor }) => {
			// 1. Detect removed SceneBreak nodes → delete their DB scenes
			const currentIds = getDocSceneBreakIds(editor);
			const prevIds = prevSceneBreakIdsRef.current;
			const removedIds = prevIds.filter(id => !currentIds.includes(id));

			if (removedIds.length > 0) {
				const cid = chapterIdRef.current;
				removedIds.forEach(id => {
					console.debug('[EditorPanel] Scene boundary removed → deleting scene', id);
					deleteScene({ id, chapterId: cid });
				});
			}

			prevSceneBreakIdsRef.current = currentIds;

			// 2. Schedule multi-scene autosave
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

	// ── scene break insertion (async: DB first, then node) ────────────────────

	const handleSceneBreak = useCallback(async () => {
		const cid = chapterIdRef.current;
		if (!cid || !editorRef.current) return;

		try {
			const newScene = await createScene({ chapterId: cid, data: { title: '' } });
			editorRef.current
				.chain()
				.focus()
				.setSceneBreak({ sceneId: newScene.id })
				.run();
		} catch (err) {
			console.error('[EditorPanel] Failed to create scene for break:', err);
		}
	}, [createScene]);

	// ── load combined chapter content on chapter switch ───────────────────────

	useEffect(() => {
		if (!editor || !scenes) return;
		if (loadedChapterIdRef.current === chapterId) return; // Guard: don't reload on refetch

		// Flush any pending save from the previous chapter
		if (saveTimer.current) {
			clearTimeout(saveTimer.current);
			saveTimer.current = null;
		}

		if (scenes.length === 0) {
			// Chapter exists but has no scenes yet — show empty editor
			loadedChapterIdRef.current = chapterId;
			prevSceneBreakIdsRef.current = [];
			editor.commands.setContent('', false);
			return;
		}

		// Build combined HTML and prime the SceneBreak ID tracker
		const html = buildCombinedHTML(scenes);
		prevSceneBreakIdsRef.current = scenes.slice(1).map(s => s.id);
		loadedChapterIdRef.current = chapterId;
		editor.commands.setContent(html, false); // false = don't emit onUpdate
	}, [editor, scenes, chapterId]);

	// Flush pending save on unmount
	useEffect(() => () => {
		if (saveTimer.current) clearTimeout(saveTimer.current);
	}, []);

	// ── render ────────────────────────────────────────────────────────────────

	if (!chapterId) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'text.disabled' }}>
				<Typography variant="body1">Select a scene to begin editing.</Typography>
			</Box>
		);
	}

	if (scenesLoading) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
				<CircularProgress size={28} />
			</Box>
		);
	}

	return (
		<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

			<EditorToolbar
				editor={editor}
				settings={settings}
				onSettingsChange={updateSettings}
				onSceneBreak={handleSceneBreak}
				isSaving={isSaving}
			/>

			<Box
				sx={{
					flex: 1,
					overflowY: 'auto',
					py: 5,
					px: 2,

					// ── Project-level defaults as CSS custom properties ──────
					'--nkms-font-family': settings.fontFamily,
					'--nkms-font-size': settings.fontSize,
					'--nkms-line-height': settings.lineHeight,
					'--nkms-text-indent': settings.firstLineIndent,
					'--nkms-spacing-after': settings.spacingAfter,

					// ── Paragraph defaults (per-paragraph inline styles override these)
					'& .tiptap p': {
						textIndent: 'var(--nkms-text-indent)',
						marginBottom: 'var(--nkms-spacing-after)',
						marginTop: 0,
					},

					// ── TipTap chrome ────────────────────────────────────────
					'& .tiptap': { outline: 'none' },

					'& .tiptap p.is-editor-empty:first-of-type::before': {
						content: 'attr(data-placeholder)',
						color: 'text.disabled',
						pointerEvents: 'none',
						float: 'left',
						height: 0,
					},

					'& .tiptap blockquote': {
						borderLeft: '3px solid',
						borderColor: 'divider',
						pl: 2,
						ml: 0,
						color: 'text.secondary',
						fontStyle: 'italic',
					},

					// All <hr> elements are SceneBreak nodes — render as · · ·
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
				}}
			>
				<EditorContent editor={editor} />
			</Box>
		</Box>
	);
}
