import { useState } from 'react'
import { Box, Button, Tooltip, IconButton, Menu, MenuItem } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import DeleteIcon from '@mui/icons-material/Delete'
import AddProjectDialog from './dialogs/AddProjectDialog'
import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import AddPartDialog from './dialogs/AddPartDialog'
import AddPartChapterDialog from './dialogs/AddPartChapterDialog'
import AddCodexEntryDialog from './dialogs/AddCodexEntryDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'
import { shouldSkipDeleteConfirm } from '../../utils/deleteConfirmPrefs'
import { useScenes, useReorderScenes, useDeleteScene } from '../../hooks/useScenes'
import { useChapters, useReorderChapters, useDeleteChapter } from '../../hooks/useChapters'
import { useDeleteBook } from '../../hooks/useBooks'
import { useDeleteCodex, useProjectCodex, useBookCodex, useCreateProjectCodex, useCreateBookCodex } from '../../hooks/useCodex'
import {
	useParts, usePartChapters,
	useReorderParts, useReorderPartChapters,
	useDeletePart,
} from '../../hooks/useParts'

// ── Add-button label ──────────────────────────────────────────────────────────

const ADD_ENTRY_LABELS = {
	CHARACTER: 'Add Character',
	VOICE: 'Add Voice Sheet',
	PLOT: 'Add Plot Element',
	WORLD: 'Add World Entry',
	TIMELINE: 'Add Timeline Entry',
	CANON: 'Add Canon Entry',
	NOTES: 'Add Note',
}

const getAddLabel = (selection) => {
	if (selection.codexId && !selection.chapterId) return null   // codex container — categories are fixed
	if (selection.codexId && selection.chapterId) return ADD_ENTRY_LABELS[selection.codexCategory] ?? 'Add Entry'
	if (selection.chapterId) return 'Add Scene'
	if (selection.partId) return 'Add Chapter'
	if (selection.bookId) return 'Add\u2026'    // "Add…" — opens a menu
	if (selection.projectId) return 'Add\u2026'   // "Add…" — opens a menu
	return 'Add Project'
}

// ── Delete context ────────────────────────────────────────────────────────────

const CODEX_ENTRY_LABELS = {
	CHARACTER: 'Character',
	VOICE: 'Voice Sheet',
	PLOT: 'Plot Element',
	WORLD: 'World Entry',
	TIMELINE: 'Timeline Entry',
	CANON: 'Canon Entry',
	NOTES: 'Note',
}

function getDeleteContext(selection) {
	if (selection.sceneId) {
		const itemType = selection.codexId
			? (CODEX_ENTRY_LABELS[selection.codexCategory] ?? 'Entry')
			: 'Scene'

		return {
			level: 'scene',
			label: `Delete ${itemType}`,
			itemType,
		}
	}

	if (selection.chapterId) {
		// Codex categories are hardcoded — cannot be deleted.
		if (selection.codexId) return null
		return {
			level: 'chapter',
			label: 'Delete Chapter',
			itemType: 'Chapter',
		}
	}

	if (selection.codexId) return {
		level: 'codex',
		label: 'Delete Codex',
		itemType: 'Codex',
	}

	if (selection.partId) return {
		level: 'part',
		label: 'Delete Part',
		itemType: 'Part',
		detail: 'The part will be removed and its chapters will be moved directly under the book. This cannot be undone.',
	}

	if (selection.bookId) return {
		level: 'book',
		label: 'Delete Book',
		itemType: 'Book',
	}

	return null
}

// ── component ─────────────────────────────────────────────────────────────────

export default function NavToolbar({ selection, setSelection }) {

	// ── dialog / menu state ───────────────────────────────────────────────────
	const [projectDialogOpen, setProjectDialogOpen] = useState(false)
	const [bookDialogOpen, setBookDialogOpen] = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [sceneDialogOpen, setSceneDialogOpen] = useState(false)
	const [partDialogOpen, setPartDialogOpen] = useState(false)
	const [partChapterDialogOpen, setPartChapterDialogOpen] = useState(false)
	const [entryDialogOpen, setEntryDialogOpen] = useState(false)
	const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
	const [addMenuAnchor, setAddMenuAnchor] = useState(null)

	// ── selection context flags ───────────────────────────────────────────────
	const isSceneContext = !!selection.sceneId
	const isChapterContext = !selection.sceneId && !!selection.chapterId
	const isChapterInPartContext = isChapterContext && !!selection.partId
	const isDirectChapterContext = isChapterContext && !selection.partId
	const isPartContext = !selection.chapterId && !!selection.partId
	const isBookContext = !selection.partId && !selection.chapterId && !!selection.bookId
	const isProjectContext = !selection.bookId && !selection.partId && !selection.chapterId && !!selection.projectId

	// ── sibling lists (for reordering) ───────────────────────────────────────
	// Each query is enabled only for the context that needs it to avoid over-fetching.
	// These also hit the TanStack Query cache populated by the nav tree.
	const { data: scenes } = useScenes(isSceneContext ? selection.chapterId : null)
	const { data: chapters } = useChapters(isDirectChapterContext ? selection.bookId : null)
	const { data: partChapters } = usePartChapters(isChapterInPartContext ? selection.partId : null)
	const { data: parts } = useParts(isPartContext ? selection.bookId : null)

	// ── Codex existence (for conditional "Add Codex" in the Add menu) ─────
	const { data: projectCodex } = useProjectCodex(isProjectContext ? selection.projectId : null)
	const { data: bookCodex } = useBookCodex(isBookContext ? selection.bookId : null)
	const { mutate: createProjectCodex } = useCreateProjectCodex()
	const { mutate: createBookCodex } = useCreateBookCodex()

	const { mutate: reorderScenes } = useReorderScenes()
	const { mutate: reorderChapters } = useReorderChapters()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderParts } = useReorderParts()

	// Resolve active sibling list and the selected item's index within it
	const siblings = isSceneContext ? scenes
		: isDirectChapterContext ? chapters
			: isChapterInPartContext ? partChapters
				: isPartContext ? parts
					: null

	const selectedId = isSceneContext ? selection.sceneId
		: isChapterContext ? selection.chapterId
			: isPartContext ? selection.partId
				: null

	const index = siblings?.findIndex(s => s.id === selectedId) ?? -1
	const canReorder = !!siblings && siblings.length > 1 && index >= 0
	const isFirst = !canReorder || index === 0
	const isLast = !canReorder || index === siblings.length - 1
	const itemLabel = isSceneContext ? 'scene'
		: isChapterContext ? 'chapter'
			: isPartContext ? 'part'
				: 'item'

	const deleteCtx = getDeleteContext(selection)

	const { mutate: deleteScene, isPending: deletingScene } = useDeleteScene()
	const { mutate: deleteChapter, isPending: deletingChapter } = useDeleteChapter()
	const { mutate: deletePart, isPending: deletingPart } = useDeletePart()
	const { mutate: deleteBook, isPending: deletingBook } = useDeleteBook()
	const { mutate: deleteCodex, isPending: deletingCodex } = useDeleteCodex()
	const isDeleting = deletingScene || deletingChapter || deletingPart || deletingBook || deletingCodex

	// ── reorder handlers ──────────────────────────────────────────────────────

	const handleMoveUp = () => {
		if (isFirst || !siblings) return
		const ids = siblings.map(s => s.id)
			;[ids[index - 1], ids[index]] = [ids[index], ids[index - 1]]
		if (isSceneContext) reorderScenes({ chapterId: selection.chapterId, ids })
		else if (isDirectChapterContext) reorderChapters({ bookId: selection.bookId, ids })
		else if (isChapterInPartContext) reorderPartChapters({ partId: selection.partId, ids })
		else if (isPartContext) reorderParts({ bookId: selection.bookId, ids })
	}

	const handleMoveDown = () => {
		if (isLast || !siblings) return
		const ids = siblings.map(s => s.id)
			;[ids[index], ids[index + 1]] = [ids[index + 1], ids[index]]
		if (isSceneContext) reorderScenes({ chapterId: selection.chapterId, ids })
		else if (isDirectChapterContext) reorderChapters({ bookId: selection.bookId, ids })
		else if (isChapterInPartContext) reorderPartChapters({ partId: selection.partId, ids })
		else if (isPartContext) reorderParts({ bookId: selection.bookId, ids })
	}

	// ── delete handler ────────────────────────────────────────────────────────

	const handleConfirmDelete = () => {
		if (!deleteCtx) return
		const { level } = deleteCtx

		if (level === 'scene') {
			deleteScene(
				{ id: selection.sceneId, chapterId: selection.chapterId },
				{ onSuccess: () => { setSelection(s => ({ ...s, sceneId: null })); setDeleteDialogOpen(false) } }
			)
		} else if (level === 'chapter') {
			deleteChapter(
				{ id: selection.chapterId, bookId: selection.bookId },
				{ onSuccess: () => { setSelection(s => ({ ...s, chapterId: null, sceneId: null })); setDeleteDialogOpen(false) } }
			)
		} else if (level === 'part') {
			deletePart(
				{ id: selection.partId, bookId: selection.bookId },
				{
					onSuccess: () => {
						// Chapters are preserved (ON DELETE SET NULL) — clear part + chapter
						// from selection since the chapter context is now ambiguous.
						setSelection(s => ({ ...s, partId: null, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					},
				}
			)
		} else if (level === 'book') {
			deleteBook(
				{ id: selection.bookId, projectId: selection.projectId },
				{ onSuccess: () => { setSelection(s => ({ ...s, bookId: null, partId: null, chapterId: null, sceneId: null })); setDeleteDialogOpen(false) } }
			)
		} else if (level === 'codex') {
			deleteCodex(
				{ id: selection.codexId },
				{ onSuccess: () => { setSelection(s => ({ ...s, codexId: null, codexCategory: null, chapterId: null, sceneId: null })); setDeleteDialogOpen(false) } }
			)
		}
	}

	// ── add handlers ─────────────────────────────────────────────────────────

	const label = getAddLabel(selection)

	const handleAdd = (e) => {
		if (selection.codexId && selection.chapterId) setEntryDialogOpen(true)
		else if (selection.chapterId) setSceneDialogOpen(true)
		else if (selection.partId) setPartChapterDialogOpen(true)
		else if (selection.bookId) setAddMenuAnchor(e.currentTarget)  // opens menu
		else if (selection.projectId) setAddMenuAnchor(e.currentTarget)  // opens menu
		else setProjectDialogOpen(true)
	}

	// ── render ────────────────────────────────────────────────────────────────

	return (
		<Box sx={{ px: 1, minHeight: 38, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>

			{/* Left: reorder arrows + delete */}
			<Box sx={{ display: 'flex', gap: 0.5 }}>
				<Tooltip title={`Move ${itemLabel} up`}>
					<span>
						<IconButton size="small" onClick={handleMoveUp} disabled={isFirst} aria-label={`Move ${itemLabel} up`}>
							<ArrowUpwardIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>

				<Tooltip title={`Move ${itemLabel} down`}>
					<span>
						<IconButton size="small" onClick={handleMoveDown} disabled={isLast} aria-label={`Move ${itemLabel} down`}>
							<ArrowDownwardIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>

				<Tooltip title={deleteCtx?.label ?? 'Select a scene, chapter, part, or book to delete'}>
					<span>
						<IconButton
							size="small"
							onClick={() => {
								if (shouldSkipDeleteConfirm()) handleConfirmDelete()
								else setDeleteDialogOpen(true)
							}}
							disabled={!deleteCtx}
							aria-label={deleteCtx?.label ?? 'Delete'}
							color={deleteCtx ? 'primary' : 'default'}
						>
							<DeleteIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>
			</Box>

			{/* Right: Add button */}
			{label && (
				<Tooltip title={label}>
					<Button size="small" variant="outlined" startIcon={<AddIcon />} onClick={handleAdd}>
						{label}
					</Button>
				</Tooltip>
			)}

			{/* Context-sensitive add menu: Project or Book scope */}
			<Menu
				anchorEl={addMenuAnchor}
				open={Boolean(addMenuAnchor)}
				onClose={() => setAddMenuAnchor(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
				transformOrigin={{ vertical: 'top', horizontal: 'right' }}
			>
				{/* Project-scope items */}
				{isProjectContext && (
					<MenuItem dense onClick={() => { setAddMenuAnchor(null); setBookDialogOpen(true) }}>
						Add Book
					</MenuItem>
				)}
				{isProjectContext && !projectCodex && (
					<MenuItem dense onClick={() => { setAddMenuAnchor(null); createProjectCodex({ projectId: selection.projectId, data: {} }) }}>
						Add Codex
					</MenuItem>
				)}
				{/* Book-scope items */}
				{isBookContext && (
					<MenuItem dense onClick={() => { setAddMenuAnchor(null); setPartDialogOpen(true) }}>
						Add Part
					</MenuItem>
				)}
				{isBookContext && (
					<MenuItem dense onClick={() => { setAddMenuAnchor(null); setChapterDialogOpen(true) }}>
						Add Chapter
					</MenuItem>
				)}
				{isBookContext && !bookCodex && (
					<MenuItem dense onClick={() => { setAddMenuAnchor(null); createBookCodex({ bookId: selection.bookId, data: {} }) }}>
						Add Codex
					</MenuItem>
				)}
			</Menu>

			{/* Add dialogs */}
			<AddProjectDialog open={projectDialogOpen} onClose={() => setProjectDialogOpen(false)} />
			<AddBookDialog open={bookDialogOpen} onClose={() => setBookDialogOpen(false)} projectId={selection.projectId} />
			<AddChapterDialog open={chapterDialogOpen} onClose={() => setChapterDialogOpen(false)} bookId={selection.bookId} />
			<AddSceneDialog open={sceneDialogOpen} onClose={() => setSceneDialogOpen(false)} chapterId={selection.chapterId} />
			<AddCodexEntryDialog open={entryDialogOpen} onClose={() => setEntryDialogOpen(false)} chapterId={selection.chapterId} codexCategory={selection.codexCategory} />
			<AddPartDialog
				open={partDialogOpen}
				onClose={() => setPartDialogOpen(false)}
				bookId={selection.bookId}
			/>
			<AddPartChapterDialog
				open={partChapterDialogOpen}
				onClose={() => setPartChapterDialogOpen(false)}
				partId={selection.partId}
			/>

			{/* Delete confirmation dialog */}
			<DeleteConfirmDialog
				open={deleteDialogOpen}
				onClose={() => setDeleteDialogOpen(false)}
				onConfirm={handleConfirmDelete}
				itemType={deleteCtx?.itemType ?? 'item'}
				detail={deleteCtx?.detail ?? null}
				isPending={isDeleting}
			/>
		</Box>
	)
}
