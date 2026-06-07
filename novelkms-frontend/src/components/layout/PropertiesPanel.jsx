import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
	Box, Typography, TextField, Divider, CircularProgress,
	Stack, Chip, Button,
} from '@mui/material';
import { useScene, SCENE_KEYS }     from '../../hooks/useScenes';
import { useChapter, CHAPTER_KEYS } from '../../hooks/useChapters';
import { usePart, PART_KEYS }       from '../../hooks/useParts';
import { useBook, BOOK_KEYS }       from '../../hooks/useBooks';
import { scenesApi }   from '../../api/scenes';
import { chaptersApi } from '../../api/chapters';
import { partsApi }    from '../../api/parts';
import { booksApi }    from '../../api/books';

// ── Scene ─────────────────────────────────────────────────────────────────────

function SceneForm({ scene, sceneId, chapterId }) {
	const qc = useQueryClient();
	const [title, setTitle] = useState(scene.title ?? '');
	const [synopsis, setSynopsis] = useState(scene.synopsis ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => scenesApi.update(sceneId, patch),
		onSuccess: () => {
			// Fix key mismatch (was ['scene', …]) + refresh nav tree list
			qc.invalidateQueries({ queryKey: SCENE_KEYS.detail(sceneId) });
			if (chapterId) qc.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Scene</Typography>
			<TextField
				label="Title"
				size="small"
				fullWidth
				value={title}
				onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Synopsis"
				size="small"
				fullWidth
				multiline
				minRows={3}
				value={synopsis}
				onChange={(e) => setSynopsis(e.target.value)}
			/>
			<Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
				<Chip label={`${scene.wordCount ?? 0} words`} size="small" variant="outlined" />
				<Button
					size="small"
					variant="contained"
					onClick={() => save({ title, synopsis })}
					disabled={isPending}
				>
					Save
				</Button>
			</Box>
		</Stack>
	);
}

function SceneProperties({ sceneId, chapterId }) {
	const { data: scene, isLoading } = useScene(sceneId);

	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!scene) return null;

	// key={scene.id} remounts SceneForm when the selected scene changes,
	// resetting local title/synopsis state without needing an effect.
	return <SceneForm key={scene.id} scene={scene} sceneId={sceneId} chapterId={chapterId} />;
}

// ── Chapter ───────────────────────────────────────────────────────────────────

function ChapterForm({ chapter, chapterId, bookId }) {
	const qc = useQueryClient();
	const [title,    setTitle]    = useState(chapter.title    ?? '');
	const [subtitle, setSubtitle] = useState(chapter.subtitle ?? '');
	const [notes,    setNotes]    = useState(chapter.notes    ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => chaptersApi.update(chapterId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: CHAPTER_KEYS.detail(chapterId) });
			if (bookId) qc.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Chapter</Typography>
			<TextField
				label="Title"
				size="small"
				fullWidth
				value={title}
				onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Subtitle"
				size="small"
				fullWidth
				value={subtitle}
				onChange={(e) => setSubtitle(e.target.value)}
			/>
			<TextField
				label="Notes"
				size="small"
				fullWidth
				multiline
				minRows={3}
				value={notes}
				onChange={(e) => setNotes(e.target.value)}
			/>
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button
					size="small"
					variant="contained"
					onClick={() => save({ title, subtitle, notes })}
					disabled={isPending}
				>
					Save
				</Button>
			</Box>
		</Stack>
	);
}

function ChapterProperties({ chapterId, bookId }) {
	const { data: chapter, isLoading } = useChapter(chapterId);

	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!chapter) return null;

	return <ChapterForm key={chapter.id} chapter={chapter} chapterId={chapterId} bookId={bookId} />;
}

// ── Part ──────────────────────────────────────────────────────────────────────

function PartForm({ part, partId, bookId }) {
	const qc = useQueryClient();
	const [title,    setTitle]    = useState(part.title    ?? '');
	const [subtitle, setSubtitle] = useState(part.subtitle ?? '');
	const [notes,    setNotes]    = useState(part.notes    ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => partsApi.update(partId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: PART_KEYS.detail(partId) });
			if (bookId) qc.invalidateQueries({ queryKey: PART_KEYS.byBook(bookId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Part</Typography>
			<TextField
				label="Title"
				size="small"
				fullWidth
				value={title}
				onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Subtitle"
				size="small"
				fullWidth
				value={subtitle}
				onChange={(e) => setSubtitle(e.target.value)}
			/>
			<TextField
				label="Notes"
				size="small"
				fullWidth
				multiline
				minRows={3}
				value={notes}
				onChange={(e) => setNotes(e.target.value)}
			/>
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button
					size="small"
					variant="contained"
					onClick={() => save({ title, subtitle, notes })}
					disabled={isPending}
				>
					Save
				</Button>
			</Box>
		</Stack>
	);
}

function PartProperties({ partId, bookId }) {
	const { data: part, isLoading } = usePart(partId);

	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!part) return null;

	return <PartForm key={part.id} part={part} partId={partId} bookId={bookId} />;
}

// ── Book ──────────────────────────────────────────────────────────────────────

function BookForm({ book, bookId, projectId }) {
	const qc = useQueryClient();
	const [title,      setTitle]      = useState(book.title      ?? '');
	const [subtitle,   setSubtitle]   = useState(book.subtitle   ?? '');
	const [shortTitle, setShortTitle] = useState(book.shortTitle ?? '');
	const [notes,      setNotes]      = useState(book.notes      ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => booksApi.update(bookId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) });
			if (projectId) qc.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Book</Typography>
			<TextField
				label="Title"
				size="small"
				fullWidth
				value={title}
				onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Subtitle"
				size="small"
				fullWidth
				value={subtitle}
				onChange={(e) => setSubtitle(e.target.value)}
			/>
			<TextField
				label="Short Title"
				size="small"
				fullWidth
				value={shortTitle}
				onChange={(e) => setShortTitle(e.target.value)}
				helperText="Abbreviated title for display in tight spaces"
			/>
			<TextField
				label="Notes"
				size="small"
				fullWidth
				multiline
				minRows={3}
				value={notes}
				onChange={(e) => setNotes(e.target.value)}
			/>
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button
					size="small"
					variant="contained"
					onClick={() => save({ title, subtitle, shortTitle, notes })}
					disabled={isPending}
				>
					Save
				</Button>
			</Box>
		</Stack>
	);
}

function BookProperties({ bookId, projectId }) {
	const { data: book, isLoading } = useBook(bookId);

	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!book) return null;

	return <BookForm key={book.id} book={book} bookId={bookId} projectId={projectId} />;
}

// ── Root panel ────────────────────────────────────────────────────────────────

export default function PropertiesPanel({ selection }) {
	const { sceneId, chapterId, partId, bookId, projectId } = selection ?? {};

	if (!sceneId && !chapterId && !partId && !bookId) {
		return (
			<Box sx={{ p: 2, color: 'text.disabled' }}>
				<Typography variant="body2">Select a book, part, chapter, or scene to view properties.</Typography>
			</Box>
		);
	}

	return (
		<Box sx={{ height: '100%', overflowY: 'auto' }}>
			{sceneId && <SceneProperties sceneId={sceneId} chapterId={chapterId} />}
			{sceneId && chapterId && <Divider />}
			{chapterId && <ChapterProperties chapterId={chapterId} bookId={bookId} />}
			{/* Part properties: only when part is selected with no chapter active */}
			{partId && !chapterId && <PartProperties partId={partId} bookId={bookId} />}
			{/* Book properties: only when book is the deepest selection */}
			{bookId && !partId && !chapterId && !sceneId && (
				<BookProperties bookId={bookId} projectId={projectId} />
			)}
		</Box>
	);
}