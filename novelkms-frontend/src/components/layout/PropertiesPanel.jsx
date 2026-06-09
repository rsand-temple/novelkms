import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
	Box, Typography, TextField, Divider, CircularProgress,
	Stack, Chip, Button, Switch, FormControlLabel, MenuItem,
} from '@mui/material';
import { useScene, SCENE_KEYS } from '../../hooks/useScenes';
import { useChapter, CHAPTER_KEYS } from '../../hooks/useChapters';
import { usePart, PART_KEYS } from '../../hooks/useParts';
import { useBook, BOOK_KEYS } from '../../hooks/useBooks';
import { useProject, PROJECT_KEYS } from '../../hooks/useProjects';
import { scenesApi } from '../../api/scenes';
import { chaptersApi } from '../../api/chapters';
import { partsApi } from '../../api/parts';
import { booksApi } from '../../api/books';
import { projectsApi } from '../../api/projects';
import { PAGE_SIZE_PRESET_OPTIONS } from '../../utils/pageConfig';


// ── Scene ─────────────────────────────────────────────────────────────────────

function SceneForm({ scene, sceneId, chapterId }) {
	const qc = useQueryClient();
	const [title, setTitle] = useState(scene.title ?? '');
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

	return <SceneForm key={scene.id} scene={scene} sceneId={sceneId} chapterId={chapterId} />;
}

// ── Chapter ───────────────────────────────────────────────────────────────────

function ChapterForm({ chapter, chapterId, bookId }) {
	const qc = useQueryClient();
	const [title, setTitle] = useState(chapter.title ?? '');
	const [subtitle, setSubtitle] = useState(chapter.subtitle ?? '');
	const [notes, setNotes] = useState(chapter.notes ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => chaptersApi.update(chapterId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: CHAPTER_KEYS.detail(chapterId) });
			if (bookId) qc.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Stack direction="row" sx={{ alignItems: 'center', mb: 1 }}>
				<Chip
					label={`Chapter ${chapter.chapterNumber}`}
					size="small"
					variant="outlined"
					sx={{ fontWeight: 500, color: 'text.secondary', borderColor: 'divider' }}
				/>
			</Stack>

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
	const [title, setTitle] = useState(part.title ?? '');
	const [subtitle, setSubtitle] = useState(part.subtitle ?? '');
	const [notes, setNotes] = useState(part.notes ?? '');

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

	// Core fields
	const [title, setTitle]         = useState(book.title     ?? '');
	const [subtitle, setSubtitle]   = useState(book.subtitle  ?? '');
	const [shortTitle, setShortTitle] = useState(book.shortTitle ?? '');
	const [notes, setNotes]         = useState(book.notes     ?? '');

	// Page layout fields
	const [pageLayoutEnabled,  setPageLayoutEnabled]  = useState(book.pageLayoutEnabled  ?? false);
	const [pageSizePreset,     setPageSizePreset]      = useState(book.pageSizePreset     ?? 'LETTER');
	const [pageWidthIn,        setPageWidthIn]         = useState(book.pageWidthIn        ?? '');
	const [pageHeightIn,       setPageHeightIn]        = useState(book.pageHeightIn       ?? '');
	const [pageMarginTopIn,    setPageMarginTopIn]     = useState(book.pageMarginTopIn    || 1.0);
	const [pageMarginBottomIn, setPageMarginBottomIn]  = useState(book.pageMarginBottomIn || 1.0);
	const [pageMarginInnerIn,  setPageMarginInnerIn]   = useState(book.pageMarginInnerIn  || 1.25);
	const [pageMarginOuterIn,  setPageMarginOuterIn]   = useState(book.pageMarginOuterIn  || 1.0);

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => booksApi.update(bookId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) });
			if (projectId) qc.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) });
		},
	});

	// Builds the full save payload from current state, with optional field
	// overrides for cases where we need to save before React re-renders
	// (e.g. the page layout enable/disable toggle).
	function buildPayload(overrides = {}) {
		const preset = overrides.pageSizePreset ?? pageSizePreset;
		return {
			title, subtitle, shortTitle, notes,
			pageLayoutEnabled: overrides.pageLayoutEnabled ?? pageLayoutEnabled,
			pageSizePreset:    preset,
			pageWidthIn:       preset === 'CUSTOM' ? (parseFloat(pageWidthIn)  || null) : null,
			pageHeightIn:      preset === 'CUSTOM' ? (parseFloat(pageHeightIn) || null) : null,
			pageMarginTopIn:    parseFloat(pageMarginTopIn)    || 1.0,
			pageMarginBottomIn: parseFloat(pageMarginBottomIn) || 1.0,
			pageMarginInnerIn:  parseFloat(pageMarginInnerIn)  || 1.25,
			pageMarginOuterIn:  parseFloat(pageMarginOuterIn)  || 1.0,
			...overrides,
		};
	}

	function handleSave(overrides = {}) {
		save(buildPayload(overrides));
	}

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

			{/* ── Page Layout ─────────────────────────────────────────────── */}
			<Divider />
			<Typography variant="overline" color="text.secondary">Page Layout</Typography>

			<FormControlLabel
				control={
					<Switch
						checked={pageLayoutEnabled}
						size="small"
						onChange={(e) => {
							const next = e.target.checked;
							setPageLayoutEnabled(next);
							// Save immediately on toggle so the editor switches
							// to/from page mode without requiring a separate save.
							handleSave({ pageLayoutEnabled: next });
						}}
					/>
				}
				label={<Typography variant="body2">Show page layout in editor</Typography>}
			/>

			{pageLayoutEnabled && (
				<Stack spacing={2}>
					<TextField
						select
						label="Page size"
						size="small"
						fullWidth
						value={pageSizePreset}
						onChange={(e) => {
							const next = e.target.value;
							setPageSizePreset(next);
							// Save immediately when preset changes so the editor
							// reflects the new page dimensions without a manual save.
							handleSave({ pageSizePreset: next });
						}}
					>
						{PAGE_SIZE_PRESET_OPTIONS.map((opt) => (
							<MenuItem key={opt.value} value={opt.value}>
								{opt.label}
							</MenuItem>
						))}
					</TextField>

					{pageSizePreset === 'CUSTOM' && (
						<Box sx={{ display: 'flex', gap: 1 }}>
							<TextField
								label='Width (in)"'
								size="small"
								type="number"
								slotProps={{ htmlInput: { step: 0.01, min: 3, max: 14 } }}
								fullWidth
								value={pageWidthIn}
								onChange={(e) => setPageWidthIn(e.target.value)}
							/>
							<TextField
								label='Height (in)"'
								size="small"
								type="number"
								slotProps={{ htmlInput: { step: 0.01, min: 4, max: 18 } }}
								fullWidth
								value={pageHeightIn}
								onChange={(e) => setPageHeightIn(e.target.value)}
							/>
						</Box>
					)}

					<Typography variant="caption" color="text.secondary">
						Margins (inches)
					</Typography>
					<Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1 }}>
						<TextField
							label="Top"
							size="small"
							type="number"
							slotProps={{ htmlInput: { step: 0.125, min: 0.5, max: 3 } }}
							value={pageMarginTopIn}
							onChange={(e) => setPageMarginTopIn(e.target.value)}
						/>
						<TextField
							label="Bottom"
							size="small"
							type="number"
							slotProps={{ htmlInput: { step: 0.125, min: 0.5, max: 3 } }}
							value={pageMarginBottomIn}
							onChange={(e) => setPageMarginBottomIn(e.target.value)}
						/>
						<TextField
							label="Inner (spine)"
							size="small"
							type="number"
							slotProps={{ htmlInput: { step: 0.125, min: 0.5, max: 3 } }}
							value={pageMarginInnerIn}
							onChange={(e) => setPageMarginInnerIn(e.target.value)}
						/>
						<TextField
							label="Outer (edge)"
							size="small"
							type="number"
							slotProps={{ htmlInput: { step: 0.125, min: 0.5, max: 3 } }}
							value={pageMarginOuterIn}
							onChange={(e) => setPageMarginOuterIn(e.target.value)}
						/>
					</Box>

					<Typography variant="caption" color="text.secondary">
						Page rulers in the editor are approximate. Final pagination is
						handled by the export tools.
					</Typography>
				</Stack>
			)}

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button
					size="small"
					variant="contained"
					onClick={() => handleSave()}
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

// ── Project ───────────────────────────────────────────────────────────────────

function ProjectForm({ project, projectId }) {
	const qc = useQueryClient();
	const [title,           setTitle]           = useState(project.title           ?? '');
	const [description,     setDescription]     = useState(project.description     ?? '');
	const [authorFirstName, setAuthorFirstName] = useState(project.authorFirstName ?? '');
	const [authorLastName,  setAuthorLastName]  = useState(project.authorLastName  ?? '');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => projectsApi.update(projectId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: PROJECT_KEYS.detail(projectId) });
			qc.invalidateQueries({ queryKey: PROJECT_KEYS.all });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Project</Typography>

			<TextField
				label="Title"
				size="small"
				fullWidth
				value={title}
				onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Description"
				size="small"
				fullWidth
				multiline
				minRows={3}
				value={description}
				onChange={(e) => setDescription(e.target.value)}
			/>

			<Divider />
			<Typography variant="overline" color="text.secondary">Author</Typography>
			<Typography variant="caption" color="text.secondary" sx={{ mt: -1 }}>
				Used in manuscript headers and copyright footers when page layout is enabled.
			</Typography>

			<Box sx={{ display: 'flex', gap: 1 }}>
				<TextField
					label="First name"
					size="small"
					fullWidth
					value={authorFirstName}
					onChange={(e) => setAuthorFirstName(e.target.value)}
				/>
				<TextField
					label="Last name"
					size="small"
					fullWidth
					value={authorLastName}
					onChange={(e) => setAuthorLastName(e.target.value)}
				/>
			</Box>

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button
					size="small"
					variant="contained"
					onClick={() => save({ title, description, authorFirstName, authorLastName })}
					disabled={isPending}
				>
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
