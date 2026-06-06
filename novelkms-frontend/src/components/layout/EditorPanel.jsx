import { useEffect, useRef, useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
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
import { useScenes, useDeleteScene, SCENE_KEYS } from '../../hooks/useScenes';
import { scenesApi } from '../../api/scenes';
import EditorToolbar from '../editor/EditorToolbar';

const AUTOSAVE_DELAY_MS = 1500;

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

// ── component ─────────────────────────────────────────────────────────────────

/**
 * EditorPanel
 *
 * Props:
 *   chapterId  — ID of the currently selected chapter.
 *   projectId  — ID of the current project (drives useProjectSettings).
 *
 * Reload policy
 *   The editor reloads its content when either:
 *     (a) chapterId changes (chapter switch), or
 *     (b) the ordered list of scene IDs changes within the same chapter
 *         (external reorder via the nav tree ↑/↓ buttons).
 *
 *   Routine refetches caused by autosave or other invalidations do NOT
 *   trigger a reload, because the scene IDs and their order are unchanged.
 *
 *   When EditorPanel itself drives a scene order change (scene break insert
 *   or scene break delete), it pre-updates loadedSceneOrderRef before the
 *   invalidation fires, so the subsequent refetch is treated as expected.
 */
export default function EditorPanel({ chapterId, projectId }) {
	const { settings, updateSettings } = useProjectSettings(projectId);

	const queryClient = useQueryClient();

	const { data: scenes, isLoading: scenesLoading } = useScenes(chapterId);
	const { mutate: deleteScene } = useDeleteScene();

	const [isSaving, setIsSaving] = useState(false);

	// ── refs ─────────────────────────────────────────────────────────────────
	const saveTimer = useRef(null);
	const firstSceneIdRef = useRef(null);
	const prevSceneBreakIdsRef = useRef([]);
	const loadedChapterIdRef = useRef(null);
	// Tracks the scene ID order the editor last loaded, as a comma-joined string.
	// Used to detect externally-driven reorders that should reload the editor.
	const loadedSceneOrderRef = useRef('');
	const scheduleSaveRef = useRef(null);
	const chapterIdRef = useRef(chapterId);
	const editorRef = useRef(null);

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
	}, []);

	useEffect(() => { scheduleSaveRef.current = scheduleSave; }, [scheduleSave]);

	// ── editor ────────────────────────────────────────────────────────────────

	const editor = useEditor({
		extensions: [
			StarterKit.configure({
				paragraph: false,
				underline: false,
				horizontalRule: false,
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
			const currentIds = getDocSceneBreakIds(editor);
			const prevIds = prevSceneBreakIdsRef.current;
			const removedIds = prevIds.filter(id => !currentIds.includes(id));

			if (removedIds.length > 0) {
				const cid = chapterIdRef.current;
				// Pre-update the known scene order BEFORE deleteScene triggers a
				// refetch, so the load effect treats the refetch as expected and
				// does not reload the editor (which already has the merged content).
				loadedSceneOrderRef.current = loadedSceneOrderRef.current
					.split(',')
					.filter(id => !removedIds.includes(id))
					.join(',');
				removedIds.forEach(id => {
					deleteScene({ id, chapterId: cid });
				});
			}

			prevSceneBreakIdsRef.current = currentIds;
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

			// Pre-update the known order before invalidation so the load effect
			// does not treat the upcoming refetch as an external reorder.
			loadedSceneOrderRef.current = orderedIds.join(',');
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(cid) });

		} catch (err) {
			console.error('[EditorPanel] Failed to insert scene break:', err);
		}
	}, [queryClient]);

	// ── load combined chapter content ─────────────────────────────────────────

	useEffect(() => {
		if (!editor || !scenes) return;

		const newOrder = scenes.map(s => s.id).join(',');
		const chapterChanged = loadedChapterIdRef.current !== chapterId;
		const orderChanged = newOrder !== loadedSceneOrderRef.current;

		// No reload needed: same chapter, same scene order.
		// This handles routine refetches from autosave invalidation.
		if (!chapterChanged && !orderChanged) return;

		// On chapter switch, flush any pending save from the previous chapter.
		if (chapterChanged && saveTimer.current) {
			clearTimeout(saveTimer.current);
			saveTimer.current = null;
		}

		if (scenes.length === 0) {
			loadedChapterIdRef.current = chapterId;
			loadedSceneOrderRef.current = '';
			prevSceneBreakIdsRef.current = [];
			editor.commands.setContent('', false);
			return;
		}

		const html = buildCombinedHTML(scenes);
		prevSceneBreakIdsRef.current = scenes.slice(1).map(s => s.id);
		loadedChapterIdRef.current = chapterId;
		loadedSceneOrderRef.current = newOrder;
		editor.commands.setContent(html, false); // false = don't emit onUpdate
	}, [editor, scenes, chapterId]);

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

					'--nkms-font-family': settings.fontFamily,
					'--nkms-font-size': settings.fontSize,
					'--nkms-line-height': settings.lineHeight,
					'--nkms-text-indent': settings.firstLineIndent,
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
				}}
			>
				<EditorContent editor={editor} />
			</Box>
		</Box>
	);
}
