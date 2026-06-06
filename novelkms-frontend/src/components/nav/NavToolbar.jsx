import { useState } from 'react'
import { Box, Button, Tooltip, IconButton } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import AddProjectDialog from './dialogs/AddProjectDialog'
import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import { useScenes, useReorderScenes } from '../../hooks/useScenes'
import { useChapters, useReorderChapters } from '../../hooks/useChapters'

// ── Add-button label ──────────────────────────────────────────────────────────

const getAddLabel = (selection) => {
	if (selection.chapterId) return 'Add Scene'
	if (selection.bookId)    return 'Add Chapter'
	if (selection.projectId) return 'Add Book'
	return 'Add Project'
}

// ── component ─────────────────────────────────────────────────────────────────

export default function NavToolbar({ selection }) {

	// ── dialog state ─────────────────────────────────────────────────────────
	const [projectDialogOpen, setProjectDialogOpen] = useState(false)
	const [bookDialogOpen,    setBookDialogOpen]    = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [sceneDialogOpen,   setSceneDialogOpen]   = useState(false)

	// ── reorder context ───────────────────────────────────────────────────────
	// A scene is selected  → reorder scenes within its chapter.
	// A chapter is selected (no scene) → reorder chapters within its book.
	// Anything else → arrows disabled.
	const isSceneContext   = !!selection.sceneId
	const isChapterContext = !selection.sceneId && !!selection.chapterId

	// These queries hit the TanStack Query cache because the nav tree already
	// fetched them when the user expanded the parent node.  Enabled only for
	// the active context so we never over-fetch.
	const { data: scenes   } = useScenes(isSceneContext   ? selection.chapterId : null)
	const { data: chapters } = useChapters(isChapterContext ? selection.bookId    : null)

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

			{/* Reorder arrows — left side */}
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
			</Box>

			{/* Add button — right side */}
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

			{/* Dialogs */}
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
		</Box>
	)
}
