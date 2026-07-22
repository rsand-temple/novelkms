import { useState, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
	Box, Typography, TextField, Divider, CircularProgress,
	Stack, Chip, Button, Select, MenuItem, FormControl,
	InputLabel, FormControlLabel, Switch, Checkbox,
} from '@mui/material';
import { useScene, SCENE_KEYS } from '../../hooks/useScenes';
import { useChapter } from '../../hooks/useChapters';
import { usePart, PART_KEYS } from '../../hooks/useParts';
import { useBook, BOOK_KEYS, useUploadCoverImage, useDeleteCoverImage } from '../../hooks/useBooks';
import { useProject } from '../../hooks/useProjects';
import { useUpdateProject } from '../../hooks/useProjects';
import {
	useGlobalTemplate, useBookTemplate,
	useResetGlobalTemplate, useDeleteBookTemplate,
} from '../../hooks/useTemplates';
import { scenesApi } from '../../api/scenes';
import { chaptersApi } from '../../api/chapters';
import { partsApi } from '../../api/parts';
import { booksApi } from '../../api/books';
import client from '../../api/client';
import AiFormInstructionsEditor from '../ai/AiFormInstructionsEditor';
import MemoryTemplateEditor from '../ai/MemoryTemplateEditor';
import { useChapterMemoryVariants, useChapterMemoryStatus } from '../../hooks/useChapterMemory';
import { useChapterSummaryVariants, useBookChapterSummaries, useBookSummaryVariants, useBookSummaryStatus } from '../../hooks/useSummary';
import { useChapterEditorialVariants } from '../../hooks/useEditorial';
import { useAiCredentials } from '../../hooks/useAiCredentials';
import { providerLabel } from '../ai/aiProviders';
import { useCodexAiContext, useSetScenePinned, useSetCategoryPinned } from '../../hooks/useAiContext';
import { stateColor as memoryStateColor, stateExplanation as memoryStateExplanation, stateLabel as memoryStateLabel, formatTime as formatMemoryTime } from '../ai/memoryStatus';
import { stateColor as summaryStateColor, stateExplanation as summaryStateExplanation, stateLabel as summaryStateLabel } from '../ai/summaryStatus';

// ── Page size presets ─────────────────────────────────────────────────────────

const PAGE_SIZE_PRESETS = [
	{ label: 'US Letter (8.5″ × 11″)', value: 'LETTER', width: 8.5, height: 11.0 },
	{ label: 'A4 (8.27″ × 11.69″)', value: 'A4', width: 8.27, height: 11.69 },
	{ label: 'Trade Paperback (6″ × 9″)', value: 'TRADE_PB', width: 6.0, height: 9.0 },
	{ label: 'Mass Market (4.25″ × 6.87″)', value: 'MASS_MARKET', width: 4.25, height: 6.87 },
	{ label: 'Hardback (6″ × 9″)', value: 'HARDBACK', width: 6.0, height: 9.0 },
	{ label: 'Custom', value: 'CUSTOM', width: null, height: null },
]

// ── Codex AI context ──────────────────────────────────────────────────────────

// Display names for the seven seeded Types, keyed by their system key
// (chapter.codex_category). Author-created Types have a NULL key and fall back
// to their own title. UI says "Type"; the column keeps its historic name.
const CODEX_TYPE_LABELS = {
	CHARACTER: 'Characters',
	VOICE: 'Voices',
	PLOT: 'Plot',
	WORLD: 'World',
	TIMELINE: 'Timeline',
	CANON: 'Canon',
	NOTES: 'Notes',
};

/**
 * Presentational AI-context toggle shared by the codex container, codex Type,
 * and codex entry property panels. The checkbox shares its meaning
 * with the nav context-menu "Include in AI context" actions: a checked box
 * means the entry/entries are shared with the AI as reference context for
 * chapter/scene reviews.
 */
function AiContextToggle({ label, checked, indeterminate = false, disabled = false, onChange, summary, helper }) {
	return (
		<Box>
			<FormControlLabel
				sx={{ m: 0 }}
				control={
					<Checkbox
						size="small"
						checked={checked}
						indeterminate={indeterminate}
						disabled={disabled}
						onChange={(e) => onChange(e.target.checked)}
					/>
				}
				label={<Typography variant="body2">{label}</Typography>}
			/>
			{summary && (
				<Typography variant="caption" color="text.secondary" sx={{ display: 'block', pl: 3.5 }}>
					{summary}
				</Typography>
			)}
			{helper && (
				<Typography variant="caption" color="text.secondary" sx={{ display: 'block', pl: 3.5, mt: 0.5 }}>
					{helper}
				</Typography>
			)}
		</Box>
	);
}

// ── Scene ─────────────────────────────────────────────────────────────────────

function SceneForm({ scene, sceneId, chapterId, codexId = null }) {
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

	// Codex entries (codexId set) can be shared with the AI as reference context.
	const isCodexEntry = !!codexId;
	const { mutate: setScenePinned } = useSetScenePinned();

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">{isCodexEntry ? 'Codex Entry' : 'Scene'}</Typography>
			<TextField
				label="Title" size="small" fullWidth
				value={title} onChange={(e) => setTitle(e.target.value)}
			/>
			<TextField
				label="Synopsis" size="small" fullWidth multiline minRows={3}
				value={synopsis} onChange={(e) => setSynopsis(e.target.value)}
			/>

			{isCodexEntry && (
				<AiContextToggle
					label="Include in AI context"
					checked={!!scene.aiContextPinned}
					onChange={(pinned) => setScenePinned({ sceneId, chapterId, codexId, pinned })}
					summary={scene.aiContextPinned
						? 'Shared with the AI as reference context.'
						: 'Not shared with the AI.'}
				/>
			)}

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

function SceneProperties({ sceneId, chapterId, codexId = null }) {
	const { data: scene, isLoading } = useScene(sceneId);
	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!scene) return null;
	return <SceneForm key={`${scene.id}:${scene.title ?? ''}:${scene.aiContextPinned}`} scene={scene} sceneId={sceneId} chapterId={chapterId} codexId={codexId} />;
}

// ── Chapter ───────────────────────────────────────────────────────────────────

function ChapterForm({ chapter, chapterId }) {
	const qc = useQueryClient();
	const [title, setTitle] = useState(chapter.title ?? '');
	const [subtitle, setSubtitle] = useState(chapter.subtitle ?? '');
	const [notes, setNotes] = useState(chapter.notes ?? '');
	const [resetsNumbering, setResetsNumbering] = useState(chapter.resetsNumbering ?? false);

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => chaptersApi.update(chapterId, patch),
		onSuccess: () => {
			// Broad invalidation: toggling resetsNumbering can shift the computed
			// chapterNumber of every chapter that follows it in book order —
			// potentially in other parts, or in the direct-book chapter list —
			// not just this chapter's own cache entries. Mirrors useMoveChapter.
			qc.invalidateQueries({ queryKey: ['chapters'] });
			qc.invalidateQueries({ queryKey: ['parts'] });
		},
	});

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Stack direction="row" sx={{ mb: 1, alignItems: 'center' }}>
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

			<FormControlLabel
				control={
					<Checkbox
						size="small"
						checked={resetsNumbering}
						onChange={(e) => setResetsNumbering(e.target.checked)}
					/>
				}
				label="Reset numbering"
			/>
			<Typography variant="caption" color="text.secondary" sx={{ mt: -1.5 }}>
				Every chapter after this one renumbers from here until the next reset point.
			</Typography>

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained"
					onClick={() => save({ title, subtitle, notes, resetsNumbering })} disabled={isPending}>
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
	return <ChapterForm key={`${chapter.id}:${chapter.title ?? ''}`} chapter={chapter} chapterId={chapterId} bookId={bookId} />;
}

// ── Codex Type (a Type chapter inside a codex) ────────────────────────────────

/**
 * Properties for a codex Type. Unlike a manuscript chapter, a Type shows its
 * read-only name (no "Chapter N", no title/subtitle/notes) plus a bulk
 * AI-context toggle that shares (or unshares) every entry of that Type — the
 * same meaning as the nav "Include/Exclude all in AI context". The Type's name,
 * description, and field set are edited in the Type editor (nav context menu →
 * "Edit Type…", or the codex's "Manage Codex Types…" dialog), not here.
 *
 * `codexCategory` is the Type's system key — NULL for author-created Types,
 * which fall back to the Type's own title.
 */
function CodexTypeProperties({ chapterId, codexId, codexCategory }) {
	const { data: ctx, isLoading } = useCodexAiContext(codexId);
	const { mutate: setCategoryPinned } = useSetCategoryPinned();

	const items = (ctx?.entries ?? []).filter(e => e.chapterId === chapterId);
	const total = items.length;
	const pinned = items.filter(e => e.pinned).length;
	const label = items[0]?.categoryTitle?.trim()
		|| CODEX_TYPE_LABELS[codexCategory]
		|| 'Type';

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Codex Type</Typography>
			<Chip
				label={label}
				size="small" variant="outlined"
				sx={{ alignSelf: 'flex-start', fontWeight: 500, color: 'text.secondary', borderColor: 'divider' }}
			/>

			{isLoading ? (
				<CircularProgress size={18} />
			) : (
				<AiContextToggle
					label="Include all in AI context"
					checked={total > 0 && pinned === total}
					indeterminate={pinned > 0 && pinned < total}
					disabled={total === 0}
					onChange={(checked) => setCategoryPinned({ chapterId, codexId, pinned: checked })}
					summary={total === 0
						? 'No entries of this type yet.'
						: `${pinned} of ${total} ${total === 1 ? 'entry' : 'entries'} shared with the AI.`}
				/>
			)}
		</Stack>
	);
}

// ── Codex container ───────────────────────────────────────────────────────────

/**
 * Properties for the codex container. Shows a codex-wide AI-context toggle that
 * shares (or unshares) every entry across all Types — the same meaning as
 * the nav "Manage AI Context" dialog's Clear-all / select-all, surfaced as a
 * single checkbox.
 */
function CodexProperties({ codexId, title }) {
	const { data: ctx, isLoading } = useCodexAiContext(codexId);
	const { mutate: setCategoryPinned } = useSetCategoryPinned();

	const entries = ctx?.entries ?? [];
	const total = entries.length;
	const pinned = entries.filter(e => e.pinned).length;

	const setAll = (checked) => {
		// Bulk-toggle each distinct Type chapter, mirroring the Manage dialog.
		const chapterIds = [...new Set(entries.map(e => e.chapterId))];
		for (const cid of chapterIds) {
			setCategoryPinned({ chapterId: cid, codexId, pinned: checked });
		}
	};

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Codex</Typography>
			<Chip
				label={title || 'Codex'}
				size="small" variant="outlined"
				sx={{ alignSelf: 'flex-start', fontWeight: 500, color: 'text.secondary', borderColor: 'divider' }}
			/>

			{isLoading ? (
				<CircularProgress size={18} />
			) : (
				<AiContextToggle
					label="Include all entries in AI context"
					checked={total > 0 && pinned === total}
					indeterminate={pinned > 0 && pinned < total}
					disabled={total === 0}
					onChange={setAll}
					summary={total === 0
						? 'No entries in this codex yet.'
						: `${pinned} of ${total} ${total === 1 ? 'entry' : 'entries'} shared with the AI.`}
					helper="Right-click the Codex → Manage AI Context for per-entry control."
				/>
			)}
		</Stack>
	);
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
			<Stack direction="row" sx={{ mb: 1, alignItems: 'center' }}>
				<Chip
					label={`Part ${part.partNumber}`}
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

function PartProperties({ partId, bookId }) {
	const { data: part, isLoading } = usePart(partId);
	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!part) return null;
	return <PartForm key={`${part.id}:${part.title ?? ''}:${part.partNumber}`} part={part} partId={partId} bookId={bookId} />;
}

// ── Book ──────────────────────────────────────────────────────────────────────

function BookForm({ book, bookId, projectId, selectTemplate }) {
	const qc = useQueryClient();

	// Metadata
	const [title, setTitle] = useState(book.title ?? '');
	const [subtitle, setSubtitle] = useState(book.subtitle ?? '');
	const [shortTitle, setShortTitle] = useState(book.shortTitle ?? '');
	const [notes, setNotes] = useState(book.notes ?? '');

	// Page layout
	const [pageLayoutEnabled, setPageLayoutEnabled] = useState(book.pageLayoutEnabled ?? false);
	const [pageSizePreset, setPageSizePreset] = useState(book.pageSizePreset ?? 'LETTER');
	const [pageWidthIn, setPageWidthIn] = useState(book.pageWidthIn != null ? String(book.pageWidthIn) : '');
	const [pageHeightIn, setPageHeightIn] = useState(book.pageHeightIn != null ? String(book.pageHeightIn) : '');
	const [pageMarginTopIn, setPageMarginTopIn] = useState(book.pageMarginTopIn != null ? String(book.pageMarginTopIn) : '1');
	const [pageMarginBottomIn, setPageMarginBottomIn] = useState(book.pageMarginBottomIn != null ? String(book.pageMarginBottomIn) : '1');
	const [pageMarginInnerIn, setPageMarginInnerIn] = useState(book.pageMarginInnerIn != null ? String(book.pageMarginInnerIn) : '1.25');
	const [pageMarginOuterIn, setPageMarginOuterIn] = useState(book.pageMarginOuterIn != null ? String(book.pageMarginOuterIn) : '1');

	const { mutate: save, isPending } = useMutation({
		mutationFn: (patch) => booksApi.update(bookId, patch),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) });
			if (projectId) qc.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) });
		},
	});

	// ── Cover image ───────────────────────────────────────────────────────────

	const fileInputRef = useRef(null);
	const uploadCoverImage = useUploadCoverImage();
	const deleteCoverImage = useDeleteCoverImage();

	function handleImageUpload(e) {
		const file = e.target.files?.[0];
		if (!file) return;
		// Reset the input so the same file can be re-selected after a remove.
		e.target.value = '';
		uploadCoverImage.mutate({ id: bookId, file, projectId });
	}

	function handleImageDelete() {
		deleteCoverImage.mutate({ id: bookId, projectId });
	}

	// Cache-bust the thumbnail URL when the book record changes (both
	// setCoverImage and deleteCoverImage bump updated_at on the server).
	const thumbnailUrl = booksApi.getCoverImageUrl(bookId) +
		`?t=${encodeURIComponent(book.updatedAt ?? '')}`;

	const imageIsBusy = uploadCoverImage.isPending || deleteCoverImage.isPending;

	// ── Page size preset change ───────────────────────────────────────────────

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
			pageWidthIn: pageWidthIn ? parseFloat(pageWidthIn) : null,
			pageHeightIn: pageHeightIn ? parseFloat(pageHeightIn) : null,
			pageMarginTopIn: pageMarginTopIn ? parseFloat(pageMarginTopIn) : null,
			pageMarginBottomIn: pageMarginBottomIn ? parseFloat(pageMarginBottomIn) : null,
			pageMarginInnerIn: pageMarginInnerIn ? parseFloat(pageMarginInnerIn) : null,
			pageMarginOuterIn: pageMarginOuterIn ? parseFloat(pageMarginOuterIn) : null,
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

			{/* ── Cover Image ───────────────────────────────────────────────── */}
			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Cover Image
			</Typography>
			<Typography variant="caption" color="text.secondary">
				Displayed as page 1 when viewing the book in page layout mode. Scaled to fill the full page.
			</Typography>

			{book.hasCoverImage && (
				<Box sx={{ textAlign: 'center' }}>
					<Box
						component="img"
						src={thumbnailUrl}
						alt="Cover"
						sx={{
							maxWidth: '100%',
							maxHeight: 180,
							objectFit: 'contain',
							borderRadius: 1,
							border: '1px solid',
							borderColor: 'divider',
							display: 'block',
							mx: 'auto',
						}}
					/>
				</Box>
			)}

			<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
				{/* Hidden file input; triggered by the Button label below. */}
				<input
					ref={fileInputRef}
					type="file"
					accept="image/*"
					style={{ display: 'none' }}
					onChange={handleImageUpload}
				/>
				<Button
					size="small"
					variant="outlined"
					disabled={imageIsBusy}
					onClick={() => fileInputRef.current?.click()}
				>
					{book.hasCoverImage ? 'Replace Image' : 'Upload Cover Image'}
				</Button>
				{book.hasCoverImage && (
					<Button
						size="small"
						variant="outlined"
						color="warning"
						disabled={imageIsBusy}
						onClick={handleImageDelete}
					>
						Remove
					</Button>
				)}
			</Stack>

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
						slotProps={{
							htmlInput: { step: 0.125, min: 4, max: 18 },
						}}
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
					slotProps={{
						htmlInput: { step: 0.125, min: 0.25, max: 3 },
					}}
				/>
				<TextField
					label="Bottom" size="small" type="number" fullWidth
					value={pageMarginBottomIn} onChange={(e) => setPageMarginBottomIn(e.target.value)}
					slotProps={{
						htmlInput: { step: 0.125, min: 0.25, max: 3 },
					}}
				/>
			</Stack>
			<Stack direction="row" spacing={1}>
				<TextField
					label="Inner" size="small" type="number" fullWidth
					value={pageMarginInnerIn} onChange={(e) => setPageMarginInnerIn(e.target.value)}
					slotProps={{
						htmlInput: { step: 0.125, min: 0.25, max: 3 },
					}}
					helperText="Binding side"
				/>
				<TextField
					label="Outer" size="small" type="number" fullWidth
					value={pageMarginOuterIn} onChange={(e) => setPageMarginOuterIn(e.target.value)}
					slotProps={{
						htmlInput: { step: 0.125, min: 0.25, max: 3 },
					}}
				/>
			</Stack>

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained" onClick={handleSave} disabled={isPending}>
					Save
				</Button>
			</Box>

			{/* ── Page Templates ────────────────────────────────────────────── */}
			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Page Templates
			</Typography>
			<Typography variant="caption" color="text.secondary">
				Cover and part pages for this book. Editing creates a per-book override of the global template.
			</Typography>
			<Stack direction="row" spacing={1}>
				<Button size="small" variant="outlined"
					onClick={() => selectTemplate?.({ type: 'cover', scope: 'book', bookId })}>
					Cover Page
				</Button>
				<Button size="small" variant="outlined"
					onClick={() => selectTemplate?.({ type: 'part', scope: 'book', bookId })}>
					Part Page
				</Button>
			</Stack>

			{/* ── AI Review Instructions ────────────────────────────────────── */}
			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				AI Review Instructions
			</Typography>
			<AiFormInstructionsEditor scope="book" id={bookId} />

			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Memory Document Template
			</Typography>
			<MemoryTemplateEditor scope="book" id={bookId} />
		</Stack>
	);
}

function BookProperties({ bookId, projectId, selectTemplate }) {
	const { data: book, isLoading } = useBook(bookId);
	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!book) return null;
	return <BookForm key={`${book.id}:${book.title ?? ''}`} book={book} bookId={bookId} projectId={projectId} selectTemplate={selectTemplate} />;
}

// ── Project ───────────────────────────────────────────────────────────────────

function ProjectForm({ project, projectId }) {
	const [title, setTitle] = useState(project.title ?? '')
	const [description, setDescription] = useState(project.description ?? '')
	const [authorFirstName, setAuthorFirstName] = useState(project.authorFirstName ?? '')
	const [authorLastName, setAuthorLastName] = useState(project.authorLastName ?? '')
	const [displayName, setDisplayName] = useState(project.displayName ?? '')
	const [copyright, setCopyright] = useState(project.copyright ?? '')
	const [emailAddress, setEmailAddress] = useState(project.emailAddress ?? '')
	const [phoneNumber, setPhoneNumber] = useState(project.phoneNumber ?? '')

	const { mutate: save, isPending } = useUpdateProject()

	// Total word count — fetched separately; non-editable.
	const { data: wcData } = useQuery({
		queryKey: ['projects', projectId, 'word-count'],
		queryFn: async () => {
			const res = await client.get(`/projects/${projectId}/word-count`)
			return res.data
		},
		enabled: !!projectId,
		staleTime: 60_000,
	})
	const totalWords = wcData?.wordCount ?? null

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
			<TextField label="Display Name" size="small" fullWidth
				value={displayName} onChange={(e) => setDisplayName(e.target.value)}
				helperText="Resolves the Display Name field token on cover pages" />
			<TextField label="Copyright" size="small" fullWidth
				value={copyright} onChange={(e) => setCopyright(e.target.value)}
				helperText="Resolves the Copyright field token (e.g. © 2026 Your Name)" />

			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Contact
			</Typography>
			<TextField label="Email Address" size="small" fullWidth
				value={emailAddress} onChange={(e) => setEmailAddress(e.target.value)} />
			<TextField label="Phone Number" size="small" fullWidth
				value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />

			{totalWords !== null && (
				<>
					<Divider />
					<Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
						<Typography variant="caption" color="text.secondary">
							Total words
						</Typography>
						<Chip label={totalWords.toLocaleString()} size="small" variant="outlined" />
					</Box>
				</>
			)}

			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" variant="contained" disabled={isPending}
					onClick={() => save({
						id: projectId,
						data: {
							title, description,
							authorFirstName, authorLastName,
							displayName, copyright,
							emailAddress, phoneNumber,
						},
					})}>
					Save
				</Button>
			</Box>

			{/* ── AI Review Instructions ────────────────────────────────────── */}
			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				AI Review Instructions
			</Typography>
			<AiFormInstructionsEditor scope="project" id={projectId} />

			<Divider />
			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
				Memory Document Template
			</Typography>
			<MemoryTemplateEditor scope="project" id={projectId} />
		</Stack>
	)
}

function ProjectProperties({ projectId }) {
	const { data: project, isLoading } = useProject(projectId);
	if (isLoading) return <CircularProgress size={20} sx={{ m: 2 }} />;
	if (!project) return null;
	return <ProjectForm key={`${project.id}:${project.title ?? ''}`} project={project} projectId={projectId} />;
}

// ── AI document (memory / chapter summary / book summary) ─────────────────────

function AiDocProperties({ selection, setSelection }) {
	const { aiDocType, chapterId, bookId, projectId, aiDocProvider } = selection;
	const isMemory = aiDocType === 'memory';
	const isChapterSummary = aiDocType === 'chapterSummary';
	const isBookSummary = aiDocType === 'bookSummary';
	const isEditorial = aiDocType === 'editorial';

	// Provider-variant aware: fetch all variants and show the one the editor's
	// selector has selected (selection.aiDocProvider), falling back to the default
	// provider, then to whatever variant exists. Coverage/staleness chips below
	// stay on the default provider for now (a later increment makes them
	// provider-aware).
	const { data: aiCredentials = [] } = useAiCredentials();
	const activeCredentials = (aiCredentials || []).filter(c => (c.status ? c.status === 'ACTIVE' : true));
	const defaultProviderKey = (activeCredentials.find(c => c.isDefault) || activeCredentials[0])?.provider ?? null;

	const { data: memoryVariants = [] } = useChapterMemoryVariants(isMemory ? chapterId : null, isMemory);
	const { data: memoryRows = [] } = useChapterMemoryStatus(isMemory ? bookId : null, isMemory);
	const { data: chapterSummaryVariants = [] } = useChapterSummaryVariants(isChapterSummary ? chapterId : null, isChapterSummary);
	const { data: chapterSummaryRows = [] } = useBookChapterSummaries(isChapterSummary ? bookId : null, isChapterSummary);
	const { data: bookSummaryVariants = [] } = useBookSummaryVariants(isBookSummary ? bookId : null, isBookSummary);
	const { data: bookSummaryStatusData } = useBookSummaryStatus(isBookSummary ? bookId : null, isBookSummary);
	const { data: editorialVariants = [] } = useChapterEditorialVariants(isEditorial ? chapterId : null, isEditorial);

	const variants = isMemory ? memoryVariants
		: isChapterSummary ? chapterSummaryVariants
			: isEditorial ? editorialVariants
				: bookSummaryVariants;
	const selectedProvider = aiDocProvider || defaultProviderKey || (variants[0]?.provider ?? null);
	const doc = variants.find(v => v.provider === selectedProvider) ?? null;

	// Editorials have no aggregate/staleness view — purely author-facing — so no
	// state chip; only the generation metadata below is shown.
	const stateValue = isMemory
		? memoryRows.find(s => s.chapterId === chapterId)?.state
		: isChapterSummary
			? chapterSummaryRows.find(s => s.chapterId === chapterId)?.state
			: isEditorial
				? null
				: !bookSummaryStatusData ? null
					: !bookSummaryStatusData.hasDoc ? 'MISSING'
						: bookSummaryStatusData.stale ? 'STALE_CONTENT' : 'OK';

	const label = isMemory
		? memoryStateLabel(stateValue)
		: isChapterSummary
			? summaryStateLabel(stateValue)
			: (stateValue === 'MISSING' ? 'Not generated' : summaryStateLabel(stateValue));
	const color = isMemory ? memoryStateColor(stateValue) : summaryStateColor(stateValue);
	const explanation = isMemory ? memoryStateExplanation(stateValue) : summaryStateExplanation(stateValue);

	const typeLabel = isMemory ? 'Memory document' : isChapterSummary ? 'Chapter summary' : isEditorial ? 'Editorial' : 'Book summary';

	function handleClose() {
		setSelection({
			projectId: projectId ?? null,
			bookId: bookId ?? null,
			partId: null,
			chapterId: isBookSummary ? null : (chapterId ?? null),
			sceneId: null,
		});
	}

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">{typeLabel}</Typography>

			<Typography variant="body2" color="text.secondary">
				Not part of the manuscript — never exported, never counted toward word totals.
			</Typography>

			{stateValue && (
				<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
					<Chip label={label} size="small" color={color} variant="outlined" />
				</Stack>
			)}
			{explanation && (
				<Typography variant="caption" color="text.secondary">{explanation}</Typography>
			)}

			{doc && (
				<Stack spacing={0.5}>
					<Typography variant="caption" color="text.secondary">
						{doc.source === 'EDITED' ? 'Edited' : 'Generated'}
						{doc.generatedAt ? ` · ${formatMemoryTime(doc.generatedAt)}` : ''}
					</Typography>
					{doc.provider && (
						<Typography variant="caption" color="text.secondary">Provider: {providerLabel(doc.provider)}</Typography>
					)}
					{doc.model && (
						<Typography variant="caption" color="text.secondary">Model: {doc.model}</Typography>
					)}
					{isBookSummary && typeof doc.wordCount === 'number' && (
						<Typography variant="caption" color="text.secondary">{doc.wordCount} words</Typography>
					)}
					{doc.userGuidance && (
						<Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
							Last guidance: “{doc.userGuidance}”
						</Typography>
					)}
				</Stack>
			)}

			{!doc && variants.length > 0 && selectedProvider && (
				<Typography variant="caption" color="text.secondary">
					Not generated for {providerLabel(selectedProvider)} yet.
				</Typography>
			)}

			{variants.length > 1 && (
				<Typography variant="caption" color="text.secondary">
					{variants.length} providers have this {typeLabel.toLowerCase()}.
				</Typography>
			)}

			<Divider />
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" onClick={handleClose}>Done</Button>
			</Box>
		</Stack>
	);
}

// ── Template ──────────────────────────────────────────────────────────────────

function TemplateProperties({ selection, setSelection }) {
	const { templateType, templateScope, bookId, projectId } = selection;
	const isGlobal = templateScope === 'global';
	const typeLabel = templateType === 'part' ? 'Part Page' : 'Cover Page';
	const scopeLabel = isGlobal ? 'Global default' : 'This book';

	// Both hooks are always called; only the relevant one is enabled.
	const { data: globalTpl } = useGlobalTemplate(templateType, isGlobal);
	const { data: bookTpl } = useBookTemplate(bookId, templateType, !isGlobal);
	const tpl = isGlobal ? globalTpl : bookTpl;
	const isOverriding = !isGlobal && tpl?.scope === 'BOOK';

	const resetGlobal = useResetGlobalTemplate();
	const deleteOverride = useDeleteBookTemplate();

	function handleClose() {
		setSelection({
			projectId: projectId ?? null,
			bookId: isGlobal ? null : (bookId ?? null),
			partId: null,
			chapterId: null,
			sceneId: null,
		});
	}

	return (
		<Stack spacing={2} sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Template</Typography>

			<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
				<Chip label={typeLabel} size="small" variant="outlined" />
				<Chip label={scopeLabel} size="small" variant="outlined"
					color={isGlobal ? 'default' : 'primary'} />
			</Stack>

			{isGlobal ? (
				<>
					<Typography variant="body2" color="text.secondary">
						Edits here apply to every project unless a book overrides them. Use the preview button to see tokens with sample values.
					</Typography>
					<Button size="small" variant="outlined" color="warning"
						disabled={resetGlobal.isPending}
						onClick={() => resetGlobal.mutate({ type: templateType })}>
						Reset to default
					</Button>
				</>
			) : isOverriding ? (
				<>
					<Typography variant="body2" color="text.secondary">
						This book overrides the global {typeLabel.toLowerCase()}. Tokens preview against this book; part tokens use sample values.
					</Typography>
					<Button size="small" variant="outlined" color="warning"
						disabled={deleteOverride.isPending}
						onClick={() => deleteOverride.mutate({ bookId, type: templateType })}>
						Reset to global (remove override)
					</Button>
				</>
			) : (
				<Typography variant="body2" color="text.secondary">
					Inherited from the global default. Editing will automatically create an override for this book.
				</Typography>
			)}

			<Divider />
			<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
				<Button size="small" onClick={handleClose}>Done</Button>
			</Box>
		</Stack>
	);
}

// ── Root panel ────────────────────────────────────────────────────────────────

export default function PropertiesPanel({ selection, setSelection, selectTemplate }) {
	const { sceneId, chapterId, partId, bookId, projectId, codexId, codexCategory, templateType, aiDocType } = selection ?? {};

	// AI document mode (memory document / chapter or book summary) takes over
	// the panel entirely, same as template mode.
	if (aiDocType) {
		return (
			<Box sx={{ height: '100%', overflowY: 'auto' }}>
				<AiDocProperties selection={selection} setSelection={setSelection} />
			</Box>
		);
	}

	// Template mode takes over the panel entirely.
	if (templateType) {
		return (
			<Box sx={{ height: '100%', overflowY: 'auto' }}>
				<TemplateProperties selection={selection} setSelection={setSelection} />
			</Box>
		);
	}

	// Codex selections take over the panel — they are not manuscript nodes, so
	// they never render the chapter/scene manuscript forms ("Chapter 0", etc.).
	if (codexId) {
		let body;
		if (sceneId) {
			body = <SceneProperties sceneId={sceneId} chapterId={chapterId} codexId={codexId} />;
		} else if (chapterId) {
			body = <CodexTypeProperties chapterId={chapterId} codexId={codexId} codexCategory={codexCategory} />;
		} else {
			body = <CodexProperties codexId={codexId} title={selection?.codexTitle} />;
		}
		return <Box sx={{ height: '100%', overflowY: 'auto' }}>{body}</Box>;
	}

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
				<BookProperties bookId={bookId} projectId={projectId} selectTemplate={selectTemplate} />
			)}
			{/* Project properties: only when project is the deepest selection */}
			{projectId && !bookId && !partId && !chapterId && !sceneId && (
				<ProjectProperties projectId={projectId} />
			)}
		</Box>
	);
}