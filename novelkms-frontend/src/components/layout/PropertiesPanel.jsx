import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
	Box, Typography, TextField, Divider, CircularProgress,
	Stack, Chip, Button, Select, MenuItem, FormControl,
	InputLabel, FormControlLabel, Switch,
} from '@mui/material';
import { useScene, SCENE_KEYS }    from '../../hooks/useScenes';
import { useChapter, CHAPTER_KEYS } from '../../hooks/useChapters';
import { usePart, PART_KEYS }       from '../../hooks/useParts';
import { useBook, BOOK_KEYS }       from '../../hooks/useBooks';
import { useProject }               from '../../hooks/useProjects';
import { useUpdateProject, PROJECT_KEYS } from '../../hooks/useProjects';
import { scenesApi }   from '../../api/scenes';
import { chaptersApi } from '../../api/chapters';
import { partsApi }    from '../../api/parts';
import { booksApi }    from '../../api/books';

// ── Page size presets ─────────────────────────────────────────────────────────

const PAGE_SIZE_PRESETS = [
	{ label: 'US Letter (8.5″ × 11″)',      value: 'LETTER',      width: 8.5,  height: 11.0  },
	{ label: 'A4 (8.27″ × 11.69″)',         value: 'A4',          width: 8.27, height: 11.69 },
	{ label: 'Trade Paperback (6″ × 9″)',    value: 'TRADE_PB',    width: 6.0,  height: 9.0   },
	{ label: 'Mass Market (4.25″ × 6.87″)', value: 'MASS_MARKET', width: 4.25, height: 6.87  },
	{ label: 'Hardback (6″ × 9″)',           value: 'HARDBACK',    width: 6.0,  height: 9.0   },
	{ label: 'Custom',                       value: 'CUSTOM',      width: null, height: null  },
]

// ── Scene ─────────────────────────────────────────────────────────────────────

function SceneForm({ scene, sceneId, chapterId }) {
	const qc = useQueryClient();
	const [title,    setTitle]    = useState(scene.title    ?? '');
	const [synopsis, setSynopsis] = useState(scene.synopsis ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => scenesApi.update(sceneId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: SCENE_KEYS.detail(sceneId) });
			if (chapterId) qc.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Scene</Typography>
			<TextField
				label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Synopsis" size="small" fullWidth multiline minRows={3}
				value={synopsis} onChange={(e) => setSynopsis(e.target.value)}
			/>
			<Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
				<Chip label={`${scene.wordCount ?? 0} words`} size="small" variant="outlined" />
				<Button size="small" variant="contained"
					onClick={() => save({ title, synopsis })} disabled={isPending}>
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
			<Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
				<Chip
					label={`Chapter ${chapter.chapterNumber}`}
					size="small" variant="outlined"
					sx={{ fontWeight: 500, color: 'text.secondary', borderColor: 'divider' }}
				/>
			</Stack>
			<TextField label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)} />
			<TextField label="Subtitle" size="small" fullWidth
				value={subtitle} onChange={(e) => setSubtitle(e.target.value)} />
			<TextField label="Notes" size="small" fullWidth multiline minRows={3}
				value={notes} onChange={(e) => setNotes(e.target.value)} />
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained"
					onClick={() => save({ title, subtitle, notes })} disabled={isPending}>
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
			<TextField label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)} />
			<TextField label="Subtitle" size="small" fullWidth
				value={subtitle} onChange={(e) => setSubtitle(e.target.value)} />
			<TextField label="Notes" size="small" fullWidth multiline minRows={3}
				value={notes} onChange={(e) => setNotes(e.target.value)} />
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained"
					onClick={() => save({ title, subtitle, notes })} disabled={isPending}>
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

	// Metadata
	const [title,     setTitle]     = useState(book.title     ?? '');
	const [subtitle,  setSubtitle]  = useState(book.subtitle  ?? '');
	const [shortTitle,setShortTitle]= useState(book.shortTitle ?? '');
	const [notes,     setNotes]     = useState(book.notes     ?? '');

	// Page layout
	const [pageLayoutEnabled,  setPageLayoutEnabled]  = useState(book.pageLayoutEnabled  ?? false);
	const [pageSizePreset,     setPageSizePreset]     = useState(book.pageSizePreset     ?? 'LETTER');
	const [pageWidthIn,        setPageWidthIn]        = useState(book.pageWidthIn        != null ? String(book.pageWidthIn)        : '');
	const [pageHeightIn,       setPageHeightIn]       = useState(book.pageHeightIn       != null ? String(book.pageHeightIn)       : '');
	const [pageMarginTopIn,    setPageMarginTopIn]    = useState(book.pageMarginTopIn    != null ? String(book.pageMarginTopIn)    : '1');
	const [pageMarginBottomIn, setPageMarginBottomIn] = useState(book.pageMarginBottomIn != null ? String(book.pageMarginBottomIn) : '1');
	const [pageMarginInnerIn,  setPageMarginInnerIn]  = useState(book.pageMarginInnerIn  != null ? String(book.pageMarginInnerIn)  : '1.25');
	const [pageMarginOuterIn,  setPageMarginOuterIn]  = useState(book.pageMarginOuterIn  != null ? String(book.pageMarginOuterIn)  : '1');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => booksApi.update(bookId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) });
			if (projectId) qc.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) });
		},
	});

	function handlePresetChange(val) {
		setPageSizePreset(val);
		const preset = PAGE_SIZE_PRESETS.find(p => p.value === val);
		if (preset?.width != null) {
			setPageWidthIn(String(preset.width));
			setPageHeightIn(String(preset.height));
		}
	}

	function handleSave() {
		save({
			title, subtitle, shortTitle, notes,
			pageLayoutEnabled,
			pageSizePreset,
			pageWidthIn:        pageWidthIn        ? parseFloat(pageWidthIn)        : null,
			pageHeightIn:       pageHeightIn       ? parseFloat(pageHeightIn)       : null,
			pageMarginTopIn:    pageMarginTopIn    ? parseFloat(pageMarginTopIn)    : null,
			pageMarginBottomIn: pageMarginBottomIn ? parseFloat(pageMarginBottomIn) : null,
			pageMarginInnerIn:  pageMarginInnerIn  ? parseFloat(pageMarginInnerIn)  : null,
			pageMarginOuterIn:  pageMarginOuterIn  ? parseFloat(pageMarginOuterIn)  : null,
		});
	}

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Book</Typography>

			{/* ── Metadata ─────────────────────────────────────────────────── */}
			<TextField label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)} />
			<TextField label="Subtitle" size="small" fullWidth
				value={subtitle} onChange={(e) => setSubtitle(e.target.value)} />
			<TextField label="Short Title" size="small" fullWidth
				value={shortTitle} onChange={(e) => setShortTitle(e.target.value)}
				helperText="Abbreviated title for display in tight spaces" />
			<TextField label="Notes" size="small" fullWidth multiline minRows={3}
				value={notes} onChange={(e) => setNotes(e.target.value)} />

			{/* ── Page Layout ───────────────────────────────────────────────── */}
			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Page Layout
			</Typography>

			<FormControlLabel
				control={
					<Switch
						size="small"
						checked={pageLayoutEnabled}
						onChange={(e) => setPageLayoutEnabled(e.target.checked)}
					/>
				}
				label={
					<Typography variant="body2">
						{pageLayoutEnabled ? 'Enabled for preview and export' : 'Disabled'}
					</Typography>
				}
			/>

			<FormControl size="small" fullWidth>
				<InputLabel>Page Size</InputLabel>
				<Select
					value={pageSizePreset}
					label="Page Size"
					onChange={(e) => handlePresetChange(e.target.value)}
				>
					{PAGE_SIZE_PRESETS.map(p => (
						<MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>
					))}
				</Select>
			</FormControl>

			{pageSizePreset === 'CUSTOM' && (
				<Stack direction="row" spacing={1}>
					<TextField
						label="Width (in)" size="small" type="number" fullWidth
						value={pageWidthIn} onChange={(e) => setPageWidthIn(e.target.value)}
						inputProps={{ step: 0.125, min: 3, max: 12 }}
					/>
					<TextField
						label="Height (in)" size="small" type="number" fullWidth
						value={pageHeightIn} onChange={(e) => setPageHeightIn(e.target.value)}
						inputProps={{ step: 0.125, min: 4, max: 18 }}
					/>
				</Stack>
			)}

			<Typography variant="caption" color="text.secondary">Margins (inches)</Typography>
			<Stack direction="row" spacing={1}>
				<TextField
					label="Top" size="small" type="number" fullWidth
					value={pageMarginTopIn} onChange={(e) => setPageMarginTopIn(e.target.value)}
					inputProps={{ step: 0.125, min: 0.25, max: 3 }}
				/>
				<TextField
					label="Bottom" size="small" type="number" fullWidth
					value={pageMarginBottomIn} onChange={(e) => setPageMarginBottomIn(e.target.value)}
					inputProps={{ step: 0.125, min: 0.25, max: 3 }}
				/>
			</Stack>
			<Stack direction="row" spacing={1}>
				<TextField
					label="Inner" size="small" type="number" fullWidth
					value={pageMarginInnerIn} onChange={(e) => setPageMarginInnerIn(e.target.value)}
					inputProps={{ step: 0.125, min: 0.25, max: 3 }}
					helperText="Binding side"
				/>
				<TextField
					label="Outer" size="small" type="number" fullWidth
					value={pageMarginOuterIn} onChange={(e) => setPageMarginOuterIn(e.target.value)}
					inputProps={{ step: 0.125, min: 0.25, max: 3 }}
				/>
			</Stack>

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained" onClick={handleSave} disabled={isPending}>
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

// ── Project ───────────────────────────────────────────────────────────────────

function ProjectForm({ project, projectId }) {
	const [title,           setTitle]           = useState(project.title           ?? '');
	const [description,     setDescription]     = useState(project.description     ?? '');
	const [authorFirstName, setAuthorFirstName] = useState(project.authorFirstName ?? '');
	const [authorLastName,  setAuthorLastName]  = useState(project.authorLastName  ?? '');

	const { mutate: save, isPending } = useUpdateProject();

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Project</Typography>
			<TextField label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)} />
			<TextField label="Description" size="small" fullWidth multiline minRows={2}
				value={description} onChange={(e) => setDescription(e.target.value)} />

			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Author
			</Typography>
			<Stack direction="row" spacing={1}>
				<TextField label="First Name" size="small" fullWidth
					value={authorFirstName} onChange={(e) => setAuthorFirstName(e.target.value)} />
				<TextField label="Last Name" size="small" fullWidth
					value={authorLastName} onChange={(e) => setAuthorLastName(e.target.value)} />
			</Stack>

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained" disabled={isPending}
					onClick={() => save({ id: projectId, data: { title, description, authorFirstName, authorLastName } })}>
					Save
				</Button>
			</Box>
		</Stack>
	);
}

function ProjectProperties({ projectId }) {
	const { data: project, isLoading } = useProject(projectId);
	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!project) return null;
	return <ProjectForm key={project.id} project={project} projectId={projectId} />;
}

// ── Root panel ────────────────────────────────────────────────────────────────

export default function PropertiesPanel({ selection }) {
	const { sceneId, chapterId, partId, bookId, projectId } = selection ?? {};

	if (!sceneId && !chapterId && !partId && !bookId && !projectId) {
		return (
			<Box sx={{ p: 2, color: 'text.disabled' }}>
				<Typography variant="body2">Select a project, book, part, chapter, or scene to view properties.</Typography>
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
			{/* Project properties: only when project is the deepest selection */}
			{projectId && !bookId && !partId && !chapterId && !sceneId && (
				<ProjectProperties projectId={projectId} />
			)}
		</Box>
	);
}
