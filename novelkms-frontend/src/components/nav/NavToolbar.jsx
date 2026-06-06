import { useState } from 'react'
import { Box, Button, Tooltip, IconButton, Menu, MenuItem } from '@mui/material'
import AddIcon          from '@mui/icons-material/Add'
import ArrowUpwardIcon  from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import DeleteIcon       from '@mui/icons-material/Delete'
import AddProjectDialog from './dialogs/AddProjectDialog'
import AddBookDialog    from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog   from './dialogs/AddSceneDialog'
import AddPartDialog        from './dialogs/AddPartDialog'
import AddPartChapterDialog from './dialogs/AddPartChapterDialog'
import DeleteConfirmDialog  from './dialogs/DeleteConfirmDialog'
import { useScenes,   useReorderScenes,   useDeleteScene   } from '../../hooks/useScenes'
import { useChapters, useReorderChapters, useDeleteChapter } from '../../hooks/useChapters'
import { useBook,     useDeleteBook                        } from '../../hooks/useBooks'
import {
	useParts, usePartChapters,
	useReorderParts, useReorderPartChapters,
	useDeletePart,
} from '../../hooks/useParts'

// ── Add-button label ──────────────────────────────────────────────────────────

const getAddLabel = (selection) => {
	if (selection.chapterId) return 'Add Scene'
	if (selection.partId)    return 'Add Chapter'
	if (selection.bookId)    return 'Add\u2026'    // "Add…" — opens a menu
	if (selection.projectId) return 'Add Book'
	return 'Add Project'
}

// ── Delete context ────────────────────────────────────────────────────────────

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
	if (selection.partId) return {
		level:   'part',
		label:   'Delete Part',
		// ON DELETE SET NULL: chapters are kept and promoted to direct-book children.
		message: `Delete part ${q}? The part will be removed but its chapters will be preserved and moved directly under the book. This cannot be undone.`,
	}
	if (selection.bookId) return {
		level:   'book',
		label:   'Delete Book',
		message: `Delete book ${q}? This will permanently delete the book and all its parts, chapters, and scenes. This cannot be undone.`,
	}
	return null
}

// ── component ─────────────────────────────────────────────────────────────────

export default function NavToolbar({ selection, setSelection }) {

	// ── dialog / menu state ───────────────────────────────────────────────────
	const [projectDialogOpen,    setProjectDialogOpen]    = useState(false)
	const [bookDialogOpen,       setBookDialogOpen]       = useState(false)
	const [chapterDialogOpen,    setChapterDialogOpen]    = useState(false)
	const [sceneDialogOpen,      setSceneDialogOpen]      = useState(false)
	const [partDialogOpen,       setPartDialogOpen]       = useState(false)
	const [partChapterDialogOpen, setPartChapterDialogOpen] = useState(false)
	const [deleteDialogOpen,     setDeleteDialogOpen]     = useState(false)
	const [addMenuAnchor,        setAddMenuAnchor]        = useState(null)

	// ── selection context flags ───────────────────────────────────────────────
	const isSceneContext         = !!selection.sceneId
	const isChapterContext       = !selection.sceneId && !!selection.chapterId
	const isChapterInPartContext = isChapterContext && !!selection.partId
	const isDirectChapterContext = isChapterContext && !selection.partId
	const isPartContext          = !selection.chapterId && !!selection.partId
	const isBookContext          = !selection.partId && !selection.chapterId && !!selection.bookId

	// ── sibling lists (for reordering) ───────────────────────────────────────
	// Each query is enabled only for the context that needs it to avoid over-fetching.
	// These also hit the TanStack Query cache populated by the nav tree.
	const { data: scenes        } = useScenes(isSceneContext         ? selection.chapterId : null)
	const { data: chapters      } = useChapters(isDirectChapterContext ? selection.bookId    : null)
	const { data: partChapters  } = usePartChapters(isChapterInPartContext ? selection.partId : null)
	const { data: parts         } = useParts(isPartContext           ? selection.bookId    : null)
	const { data: book          } = useBook(isBookContext            ? selection.bookId    : null)

	const { mutate: reorderScenes        } = useReorderScenes()
	const { mutate: reorderChapters      } = useReorderChapters()
	const { mutate: reorderPartChapters  } = useReorderPartChapters()
	const { mutate: reorderParts         } = useReorderParts()

	// Resolve active sibling list and the selected item's index within it
	const siblings = isSceneContext        ? scenes
		: isDirectChapterContext           ? chapters
		: isChapterInPartContext           ? partChapters
		: isPartContext                    ? parts
		: null

	const selectedId = isSceneContext   ? selection.sceneId
		: isChapterContext              ? selection.chapterId
		: isPartContext                 ? selection.partId
		: null

	const index      = siblings?.findIndex(s => s.id === selectedId) ?? -1
	const canReorder = !!siblings && siblings.length > 1 && index >= 0
	const isFirst    = !canReorder || index === 0
	const isLast     = !canReorder || index === siblings.length - 1
	const itemLabel  = isSceneContext   ? 'scene'
		: isChapterContext              ? 'chapter'
		: isPartContext                 ? 'part'
		: 'item'

	// ── delete context ────────────────────────────────────────────────────────
	const deleteName = isSceneContext
		? scenes?.find(s => s.id === selection.sceneId)?.title
		: isDirectChapterContext
		? chapters?.find(c => c.id === selection.chapterId)?.title
		: isChapterInPartContext
		? partChapters?.find(c => c.id === selection.chapterId)?.title
		: isPartContext
		? parts?.find(p => p.id === selection.partId)?.title
		: isBookContext
		? book?.title
		: null

	const deleteCtx = getDeleteContext(selection, deleteName)

	const { mutate: deleteScene,   isPending: deletingScene   } = useDeleteScene()
	const { mutate: deleteChapter, isPending: deletingChapter } = useDeleteChapter()
	const { mutate: deletePart,    isPending: deletingPart    } = useDeletePart()
	const { mutate: deleteBook,    isPending: deletingBook    } = useDeleteBook()
	const isDeleting = deletingScene || deletingChapter || deletingPart || deletingBook

	// ── reorder handlers ──────────────────────────────────────────────────────

	const handleMoveUp = () => {
		if (isFirst || !siblings) return
		const ids = siblings.map(s => s.id)
		;[ids[index - 1], ids[index]] = [ids[index], ids[index - 1]]
		if      (isSceneContext)         reorderScenes({ chapterId: selection.chapterId, ids })
		else if (isDirectChapterContext) reorderChapters({ bookId: selection.bookId, ids })
		else if (isChapterInPartContext) reorderPartChapters({ partId: selection.partId, ids })
		else if (isPartContext)          reorderParts({ bookId: selection.bookId, ids })
	}

	const handleMoveDown = () => {
		if (isLast || !siblings) return
		const ids = siblings.map(s => s.id)
		;[ids[index], ids[index + 1]] = [ids[index + 1], ids[index]]
		if      (isSceneContext)         reorderScenes({ chapterId: selection.chapterId, ids })
		else if (isDirectChapterContext) reorderChapters({ bookId: selection.bookId, ids })
		else if (isChapterInPartContext) reorderPartChapters({ partId: selection.partId, ids })
		else if (isPartContext)          reorderParts({ bookId: selection.bookId, ids })
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
		}
	}

	// ── add handlers ─────────────────────────────────────────────────────────

	const label = getAddLabel(selection)

	const handleAdd = (e) => {
		if (selection.chapterId)      setSceneDialogOpen(true)
		else if (selection.partId)    setPartChapterDialogOpen(true)
		else if (selection.bookId)    setAddMenuAnchor(e.currentTarget)  // opens menu
		else if (selection.projectId) setBookDialogOpen(true)
		else                          setProjectDialogOpen(true)
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

			{/* Right: Add button */}
			<Tooltip title={label}>
				<Button size="small" variant="outlined" startIcon={<AddIcon />} onClick={handleAdd}>
					{label}
				</Button>
			</Tooltip>

			{/* Book-context add menu: Add Part / Add Chapter */}
			<Menu
				anchorEl={addMenuAnchor}
				open={Boolean(addMenuAnchor)}
				onClose={() => setAddMenuAnchor(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
				transformOrigin={{ vertical: 'top', horizontal: 'right' }}
			>
				<MenuItem dense onClick={() => { setAddMenuAnchor(null); setPartDialogOpen(true) }}>
					Add Part
				</MenuItem>
				<MenuItem dense onClick={() => { setAddMenuAnchor(null); setChapterDialogOpen(true) }}>
					Add Chapter
				</MenuItem>
			</Menu>

			{/* Add dialogs */}
			<AddProjectDialog open={projectDialogOpen} onClose={() => setProjectDialogOpen(false)} />
			<AddBookDialog    open={bookDialogOpen}    onClose={() => setBookDialogOpen(false)}    projectId={selection.projectId} />
			<AddChapterDialog open={chapterDialogOpen} onClose={() => setChapterDialogOpen(false)} bookId={selection.bookId} />
			<AddSceneDialog   open={sceneDialogOpen}   onClose={() => setSceneDialogOpen(false)}   chapterId={selection.chapterId} />
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
				title={deleteCtx?.label ?? 'Delete'}
				message={deleteCtx?.message ?? ''}
				isPending={isDeleting}
			/>
		</Box>
	)
}
