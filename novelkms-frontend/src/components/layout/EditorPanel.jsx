import { useEffect, useRef, useCallback } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
//import { useQuery } from '@tanstack/react-query';
import { Box, Paper, Toolbar, IconButton, Divider, Typography, Tooltip, CircularProgress } from '@mui/material';
import FormatBoldIcon from '@mui/icons-material/FormatBold';
import FormatItalicIcon from '@mui/icons-material/FormatItalic';
//import FormatUnderlinedIcon from '@mui/icons-material/FormatUnderlined';
import FormatQuoteIcon from '@mui/icons-material/FormatQuote';
import HorizontalRuleIcon from '@mui/icons-material/HorizontalRule';
import { useScene, useUpdateSceneContent } from '../../hooks/useScenes';

const AUTOSAVE_DELAY_MS = 1500;

export default function EditorPanel({ sceneId }) {
	const saveTimer = useRef(null);
	const isDirty = useRef(false);

	const { data: scene, isLoading } = useScene(sceneId);

	const { mutate: saveContent, isPending: isSaving } = useUpdateSceneContent();

	const scheduleSave = useCallback((html) => {
		isDirty.current = true;
		if (saveTimer.current) clearTimeout(saveTimer.current);
		saveTimer.current = setTimeout(() => {
			saveContent({ id: sceneId, content: html });
			isDirty.current = false;
		}, AUTOSAVE_DELAY_MS);
	}, [saveContent, sceneId]);

	const editor = useEditor({
		extensions: [
			StarterKit,
			Placeholder.configure({ placeholder: 'Begin your scene…' }),
			CharacterCount,
		],
		content: '',
		onUpdate: ({ editor }) => {
			scheduleSave(editor.getHTML());
		},
		editorProps: {
			attributes: {
				style: [
					'font-family: Georgia, serif',
					'font-size: 1.05rem',
					'line-height: 1.9',
					'max-width: 72ch',
					'margin: 0 auto',
					'padding: 0 8px',
					'min-height: 400px',
					'outline: none',
					'white-space: pre-wrap',
				].join('; '),
			},
		},
	});

	// Load scene content into editor when scene or sceneId changes
	useEffect(() => {
		if (!editor || !scene) return;
		// Flush any pending save from previous scene
		if (saveTimer.current) {
			clearTimeout(saveTimer.current);
			saveTimer.current = null;
		}
		isDirty.current = false;
		editor.commands.setContent(scene.content || '', false); // false = don't emit update
	}, [editor, scene, sceneId]);

	// Flush on unmount
	useEffect(() => {
		return () => {
			if (saveTimer.current) clearTimeout(saveTimer.current);
		};
	}, []);

	if (!sceneId) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'text.disabled' }}>
				<Typography variant="body1">Select a scene to begin editing.</Typography>
			</Box>
		);
	}

	if (isLoading) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
				<CircularProgress size={28} />
			</Box>
		);
	}

	return (
		<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
			{/* Formatting toolbar */}
			<Paper elevation={0} square sx={{ borderBottom: 1, borderColor: 'divider' }}>
				<Toolbar variant="dense" disableGutters sx={{ px: 1, gap: 0.5 }}>
					<Tooltip title="Bold">
						<IconButton
							size="small"
							onClick={() => editor?.chain().focus().toggleBold().run()}
							color={editor?.isActive('bold') ? 'primary' : 'default'}
						>
							<FormatBoldIcon fontSize="small" />
						</IconButton>
					</Tooltip>
					<Tooltip title="Italic">
						<IconButton
							size="small"
							onClick={() => editor?.chain().focus().toggleItalic().run()}
							color={editor?.isActive('italic') ? 'primary' : 'default'}
						>
							<FormatItalicIcon fontSize="small" />
						</IconButton>
					</Tooltip>
					<Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
					<Tooltip title="Block quote">
						<IconButton
							size="small"
							onClick={() => editor?.chain().focus().toggleBlockquote().run()}
							color={editor?.isActive('blockquote') ? 'primary' : 'default'}
						>
							<FormatQuoteIcon fontSize="small" />
						</IconButton>
					</Tooltip>
					<Tooltip title="Scene break (—)">
						<IconButton
							size="small"
							onClick={() => editor?.chain().focus().setHorizontalRule().run()}
						>
							<HorizontalRuleIcon fontSize="small" />
						</IconButton>
					</Tooltip>

					{/* Status indicators pushed right */}
					<Box sx={{ flex: 1 }} />
					{isSaving && <CircularProgress size={14} sx={{ mr: 1 }} />}
					<Typography variant="caption" color="text.secondary" sx={{ mr: 1 }}>
						{editor ? `${editor.storage.characterCount.words()} words` : ''}
					</Typography>
				</Toolbar>
			</Paper>

			{/* Editor content area */}
			<Box
				sx={{
					flex: 1,
					overflowY: 'auto',
					py: 4,
					px: 2,
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
						'&::after': { content: '"· · ·"', color: 'text.disabled', letterSpacing: '0.5em' },
					},
				}}
			>
				<EditorContent editor={editor} />
			</Box>
		</Box>
	);
}