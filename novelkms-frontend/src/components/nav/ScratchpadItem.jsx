import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import EditNoteIcon from '@mui/icons-material/EditNote'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { useScratchpad } from '../../hooks/useScratchpad'
import { useScenes } from '../../hooks/useScenes'
import SceneItem from './SceneItem'
import { containerIds } from '../../dnd/dndUtils'
import { useDndState } from '../../dnd/DndStateContext'
import { useNavContextMenu } from './NavContextMenuContext'

/**
 * ScratchpadItem — fixed leaf under a book: a holding pen for scenes that are
 * not part of the manuscript. Rendered after the Codex section and before the
 * book Summary, alongside the other non-manuscript fixtures.
 *
 * The node itself is NOT sortable and NOT a member of the book outline — the
 * Scratchpad has no position in the book, and the server refuses to move,
 * rename, or trash it. Its *contents* are ordinary scenes in an ordinary
 * `scenes-{chapterId}` SortableContext, which is the whole point: NavPanel's
 * existing scene drag handlers move scenes between a chapter and the Scratchpad
 * in both directions with no changes to NavPanel or dndUtils, because both ends
 * are just scene containers keyed by a chapter id.
 *
 * The row itself is a droppable whose id IS that scene container id. Without it
 * an empty Scratchpad would be impossible to drop into — there would be no
 * sortable child to aim at, and the row is not a chapter node, so NavPanel's
 * "dropped on a chapter row" fallback does not apply. Registering the container
 * id directly means resolveToContainer picks it up through the existing
 * isContainerId branch and the drop appends, with no NavPanel change needed.
 *
 * For the same reason the scene list is fetched whenever this node is mounted
 * rather than only when it is expanded: NavPanel reads the target container's
 * contents out of the TanStack cache to build the reorder payload, and a cache
 * miss would renumber the dropped scene to 0 on top of scenes already there.
 *
 * SceneItem is given bookId={null} on purpose. That is the same signal a codex
 * entry carries, and it is what makes the context menu drop every AI action
 * (isManuscriptNode = !!menuNode.bookId): nothing parked here should be
 * reviewable, summarizable, or publishable. The server enforces the same rule
 * independently — a Scratchpad chapter has a NULL book_id, so every AI path
 * rejects it with not_manuscript.
 *
 * The Scratchpad query runs on mount too. The endpoint is get-or-create and this
 * component only mounts when its book node is already expanded, so the row is
 * written exactly when the author first looks inside a book — and the context
 * menu always has a real chapter id to hand to "Add Scene".
 */
export default function ScratchpadItem({ bookId, selection, setSelection }) {
	const [open, setOpen] = useState(false)

	const { data: scratchpad } = useScratchpad(bookId)
	const scratchpadId = scratchpad?.id ?? null

	const { data: scenes } = useScenes(scratchpadId)

	const sceneContainerId = scratchpadId ? containerIds.scenes(String(scratchpadId)) : null
	const sceneIds = (scenes ?? []).map((s) => String(s.id))

	// Selection is tracked by the owning book, not by the chapter id: the id is
	// not known until the get-or-create round trip lands, and the node has to be
	// selectable before then.
	const isSelected = selection.scratchpadBookId === bookId && !selection.sceneId

	const { openContextMenu } = useNavContextMenu()
	const dragState = useDndState()

	// A null id would register a droppable that silently swallows drops, so the
	// row only becomes a real target once the chapter id is known.
	const { setNodeRef, isOver } = useDroppable({
		id: sceneContainerId ?? `scratchpad-pending-${bookId}`,
		disabled: !sceneContainerId,
	})
	const sceneDragActive = dragState?.activeType === 'scene'

	const select = () => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			scratchpadBookId: bookId,
		}))
	}

	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		select()
	}

	const handleContextMenu = (e) => {
		select()
		// Without a resolved chapter id there is no "Add Scene" target, so the
		// menu would offer nothing actionable. Suppressing it until the fetch
		// lands is better than showing a menu whose only item is a no-op.
		if (!scratchpadId) return
		openContextMenu(e, 'scratchpad', {
			id: scratchpadId,
			title: 'Scratchpad',
			bookId,
			projectId: selection.projectId,
		})
	}

	const sceneCount = sceneIds.length

	return (
		<Box>
			<ListItemButton
				ref={setNodeRef}
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
				sx={{
					pl: 7,
					...(sceneDragActive && {
						border: '1px dashed',
						borderColor: isOver ? 'primary.main' : 'divider',
						borderRadius: 1,
					}),
					...(isOver && { bgcolor: 'action.hover' }),
				}}
			>
				<ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }} onClick={handleExpandToggle}>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<EditNoteIcon fontSize="small" sx={{ color: 'text.secondary' }} />
				</ListItemIcon>
				<ListItemText
					primary="Scratchpad"
					secondary={sceneCount > 0 ? `${sceneCount} scene${sceneCount === 1 ? '' : 's'}` : null}
					slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic', color: 'text.secondary' } } }}
				/>
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				{sceneContainerId && (
					<SortableContext
						id={sceneContainerId}
						items={sceneIds}
						strategy={verticalListSortingStrategy}
					>
						{(scenes ?? []).map((scene) => (
							<SceneItem
								key={scene.id}
								scene={scene}
								chapterId={scratchpadId}
								partId={null}
								bookId={null}
								selection={selection}
								setSelection={setSelection}
							/>
						))}
					</SortableContext>
				)}
			</Collapse>
		</Box>
	)
}
