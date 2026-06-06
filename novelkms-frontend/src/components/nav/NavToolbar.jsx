import { useState } from 'react'
import { Box, Button, Tooltip, IconButton } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import DeleteIcon from '@mui/icons-material/Delete'
import AddProjectDialog from './dialogs/AddProjectDialog'
import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'
import { useScenes, useReorderScenes, useDeleteScene } from '../../hooks/useScenes'
import { useChapters, useReorderChapters, useDeleteChapter } from '../../hooks/useChapters'
import { useBook, useDeleteBook } from '../../hooks/useBooks'

// ── Add-button label ──────────────────────────────────────────────────────────

const getAddLabel = (selection) => {
	if (selection.chapterId) return 'Add Scene'
	if (selection.bookId)    return 'Add Chapter'
	if (selection.projectId) return 'Add Book'
	return 'Add Project'
}

// ── Delete context ────────────────────────────────────────────────────────────

/**
 * Returns the delete context for the current selection, or null if nothing
 * deletable is selected.  Priority: scene > chapter > book.
 *
 * `name` is the display title of the item to be deleted; it is embedded in
 * the confirmation message so the user can verify they clicked the right thing.
 */
function getDeleteContext(selection, name) {
	const q = name ? `\u201c${name}\u201d` : ''
	if (selection.sceneId) return {
		level:   'scene',
		label:   'Delete Scene',
		message: `Delete scene ${q}? This will permanently delete the scene and all its content. This cannot be undone.`,
	}
	if (selection.chapterId) return {
		level:   'chapter',
		label:   'Delete Chapter',
		message: `Delete chapter ${q}? This will permanently delete the chapter and all its scenes. This cannot be undone.`,
	}
	if (selection.bookId) return {
		level:   'book',
		label:   'Delete Book',
		message: `Delete book ${q}? This will permanently delete the book and all its chapters and scenes. This cannot be undone.`,
	}
	return null
}

// ── component ─────────────────────────────────────────────────────────────────

export default function NavToolbar({ selection, setSelection }) {

	// ── dialog state ─────────────────────────────────────────────────────────
	const [projectDialogOpen, setProjectDialogOpen] = useState(false)
	const [bookDialogOpen,    setBookDialogOpen]    = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [sceneDialogOpen,   setSceneDialogOpen]   = useState(false)
	const [deleteDialogOpen,  setDeleteDialogOpen]  = useState(false)

	// ── reorder context ───────────────────────────────────────────────────────
	// A scene is selected  → reorder scenes within its chapter.
	// A chapter is selected (no scene) → reorder chapters within its book.
	// Anything else → arrows disabled.
	const isSceneContext   = !!selection.sceneId
	const isChapterContext = !selection.sceneId && !!selection.chapterId
	const isBookContext    = !selection.chapterId && !!selection.bookId

	// These queries hit the TanStack Query cache because the nav tree already
	// fetched them when the user expanded the parent node.  Enabled only for
	// the active context so we never over-fetch.
	const { data: scenes   } = useScenes(isSceneContext   ? selection.chapterId : null)
	const { data: chapters } = useChapters(isChapterContext ? selection.bookId    : null)
	// Book name is resolved via useBook rather than a list — it's a single
	// detail fetch, enabled only when the book-delete context is active.
	const { data: book     } = useBook(isBookContext ? selection.bookId : null)

	const { mutate: reorderScenes   } = useReorderScenes()
	const { mutate: reorderChapters } = useReorderChapters()

	// Resolve the active sibling list and the selected item's index within it
	const siblings   = isSceneContext ? scenes : isChapterContext ? chapters : null
	const selectedId = isSceneContext ? selection.sceneId : selection.chapterId
	const index      = siblings?.findIndex(s => s.id === selectedId) ?? -1

	// Buttons are enabled only when we have a real sibling list with >1 item
	// and the selected item is found within it.
	const canReorder = !!siblings && siblings.length > 1 && index >= 0
	const isFirst    = !canReorder || index === 0
	const isLast     = !canReorder || index === siblings.length - 1

	// Tooltip labels reflect the current context
	const itemLabel  = isSceneContext ? 'scene' : isChapterContext ? 'chapter' : 'item'

	// ── delete context ────────────────────────────────────────────────────────
	// Resolve the display name of the item to be deleted.  Scene and chapter
	// names come from the already-fetched sibling lists; book name comes from
	// the useBook fetch above.
	const deleteName = isSceneContext
		? scenes?.find(s => s.id === selection.sceneId)?.title
		: isChapterContext
		? chapters?.find(c => c.id === selection.chapterId)?.title
		: isBookContext
		? book?.title
		: null

	const deleteCtx = getDeleteContext(selection, deleteName)

	const { mutate: deleteScene,   isPending: deletingScene   } = useDeleteScene()
	const { mutate: deleteChapter, isPending: deletingChapter } = useDeleteChapter()
	const { mutate: deleteBook,    isPending: deletingBook    } = useDeleteBook()
	const isDeleting = deletingScene || deletingChapter || deletingBook

	// ── reorder handlers ──────────────────────────────────────────────────────

	const handleMoveUp = () => {
		if (isFirst || !siblings) return
		const ids = siblings.map(s => s.id)
		;[ids[index - 1], ids[index]] = [ids[index], ids[index - 1]]
		if (isSceneContext) reorderScenes({ chapterId: selection.chapterId, ids })
		else                reorderChapters({ bookId: selection.bookId, ids })
	}

	const handleMoveDown = () => {
		if (isLast || !siblings) return
		const ids = siblings.map(s => s.id)
		;[ids[index], ids[index + 1]] = [ids[index + 1], ids[index]]
		if (isSceneContext) reorderScenes({ chapterId: selection.chapterId, ids })
		else                reorderChapters({ bookId: selection.bookId, ids })
	}

	// ── delete handlers ───────────────────────────────────────────────────────

	const handleConfirmDelete = () => {
		if (!deleteCtx) return

		const { level } = deleteCtx

		if (level === 'scene') {
			deleteScene(
				{ id: selection.sceneId, chapterId: selection.chapterId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, sceneId: null }))
						setDeleteDialogOpen(false)
					},
				}
			)
		} else if (level === 'chapter') {
			deleteChapter(
				{ id: selection.chapterId, bookId: selection.bookId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					},
				}
			)
		} else if (level === 'book') {
			deleteBook(
				{ id: selection.bookId, projectId: selection.projectId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, bookId: null, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					},
				}
			)
		}
	}

	// ── add handler ───────────────────────────────────────────────────────────

	const label = getAddLabel(selection)

	const handleAdd = () => {
		if (selection.chapterId)      setSceneDialogOpen(true)
		else if (selection.bookId)    setChapterDialogOpen(true)
		else if (selection.projectId) setBookDialogOpen(true)
		else                          setProjectDialogOpen(true)
	}

	// ── render ────────────────────────────────────────────────────────────────

	return (
		<Box sx={{ px: 1, minHeight: 38, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>

			{/* Left side: reorder arrows + delete */}
			<Box sx={{ display: 'flex', gap: 0.5 }}>
				{/*
				  * MUI Tooltip requires a focusable child. When a button is
				  * disabled it won't receive pointer events, so we wrap each
				  * in a <span> so the tooltip still renders on hover.
				  */}
				<Tooltip title={`Move ${itemLabel} up`}>
					<span>
						<IconButton
							size="small"
							onClick={handleMoveUp}
							disabled={isFirst}
							aria-label={`Move ${itemLabel} up`}
						>
							<ArrowUpwardIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>

				<Tooltip title={`Move ${itemLabel} down`}>
					<span>
						<IconButton
							size="small"
							onClick={handleMoveDown}
							disabled={isLast}
							aria-label={`Move ${itemLabel} down`}
						>
							<ArrowDownwardIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>

				<Tooltip title={deleteCtx?.label ?? 'Select a scene, chapter, or book to delete'}>
					<span>
						<IconButton
							size="small"
							onClick={() => setDeleteDialogOpen(true)}
							disabled={!deleteCtx}
							aria-label={deleteCtx?.label ?? 'Delete'}
							color={deleteCtx ? 'error' : 'default'}
						>
							<DeleteIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>
			</Box>

			{/* Right side: Add button */}
			<Tooltip title={label}>
				<Button
					size="small"
					variant="outlined"
					startIcon={<AddIcon />}
					onClick={handleAdd}
				>
					{label}
				</Button>
			</Tooltip>

			{/* Add dialogs */}
			<AddProjectDialog
				open={projectDialogOpen}
				onClose={() => setProjectDialogOpen(false)}
			/>
			<AddBookDialog
				open={bookDialogOpen}
				onClose={() => setBookDialogOpen(false)}
				projectId={selection.projectId}
			/>
			<AddChapterDialog
				open={chapterDialogOpen}
				onClose={() => setChapterDialogOpen(false)}
				bookId={selection.bookId}
			/>
			<AddSceneDialog
				open={sceneDialogOpen}
				onClose={() => setSceneDialogOpen(false)}
				chapterId={selection.chapterId}
			/>

			{/* Delete confirmation dialog */}
			<DeleteConfirmDialog
				open={deleteDialogOpen}
				onClose={() => setDeleteDialogOpen(false)}
				onConfirm={handleConfirmDelete}
				title={deleteCtx?.label ?? 'Delete'}
				message={deleteCtx?.message ?? ''}
				isPending={isDeleting}
			/>
		</Box>
	)
}
