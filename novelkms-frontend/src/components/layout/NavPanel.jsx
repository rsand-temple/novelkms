import { useState } from 'react'
import { Box, Divider } from '@mui/material'
import {
	DndContext,
	DragOverlay,
	PointerSensor,
	useSensor,
	useSensors,
	closestCenter,
} from '@dnd-kit/core'
import { arrayMove } from '@dnd-kit/sortable'
import { useQueryClient } from '@tanstack/react-query'
import BookmarksIcon from '@mui/icons-material/Bookmarks'
import MenuBookIcon  from '@mui/icons-material/MenuBook'
import ArticleIcon   from '@mui/icons-material/Article'
import NavToolbar from '../nav/NavToolbar'
import NavTree    from '../nav/NavTree'
import { useReorderParts, useReorderPartChapters } from '../../hooks/useParts'
import { useReorderChapters, useMoveChapter }      from '../../hooks/useChapters'
import { useReorderScenes }                        from '../../hooks/useScenes'
import { containerIds, parseContainerId, getQueryKey, isContainerId } from '../../dnd/dndUtils'

export default function NavPanel({ selection, setSelection }) {
	const queryClient = useQueryClient()
	const [activeItem, setActiveItem] = useState(null) // { type, title }

	// Requires 8px of pointer movement before a drag activates — prevents
	// accidental drags on clicks and lets expand-arrow clicks through cleanly.
	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
	)

	const { mutate: reorderParts }        = useReorderParts()
	const { mutate: reorderChapters }     = useReorderChapters()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderScenes }       = useReorderScenes()
	const { mutate: moveChapter }         = useMoveChapter()

	// ── Capture label for the drag overlay chip ──────────────────────────
	function handleDragStart({ active }) {
		const d = active.data.current
		setActiveItem({ type: d?.type, title: d?.title ?? '…' })
	}

	// ── Resolve which container the pointer ended over ───────────────────
	// `over` can be a sortable item   → containerId lives in sortable data
	// `over` can be a droppable zone  → over.id IS the containerId string
	function resolveToContainer(over) {
		const viaSortable = over.data.current?.sortable?.containerId
		if (viaSortable) return viaSortable
		if (isContainerId(String(over.id))) return String(over.id)
		return null
	}

	// ── Main drop handler ────────────────────────────────────────────────
	function handleDragEnd({ active, over }) {
		setActiveItem(null)
		if (!over || String(active.id) === String(over.id)) return

		const fromContainer = active.data.current?.containerId
		const toContainer   = resolveToContainer(over)
		if (!fromContainer || !toContainer) return

		if (fromContainer === toContainer) {
			// ── Same list: reorder ───────────────────────────────────────
			handleSameContainerReorder(active, over, fromContainer)

		} else if (active.data.current?.type === 'chapter') {
			// ── Chapter dropped somewhere outside its current list ───────
			//
			// Key case: the user drags a chapter over a PART ROW.
			// The part row is a sortable item whose container is parts-{bookId},
			// not a chapter list — so resolveToContainer returns the parts
			// container, which is useless for a chapter move.
			//
			// Fix: when over.data.current.type === 'part', treat the drop as
			// "move into that part's chapter list, at the end."
			// This works whether the part is open or closed.
			let effectiveToContainer = toContainer
			if (over.data.current?.type === 'part') {
				effectiveToContainer = containerIds.chaptersPart(String(over.id))
			}

			if (effectiveToContainer !== fromContainer) {
				handleChapterReparent(active, over, fromContainer, effectiveToContainer)
			}
		}
		// Scenes: no cross-chapter moves supported — same-container reorder only.
	}

	// ── Reorder within the same list ─────────────────────────────────────
	// Reads current ordering from the TanStack Query cache, computes the
	// new order via arrayMove, and dispatches the correct reorder mutation.
	function handleSameContainerReorder(active, over, containerId) {
		const qk = getQueryKey(containerId)
		if (!qk) return

		const items  = queryClient.getQueryData(qk) ?? []
		const oldIdx = items.findIndex(i => String(i.id) === String(active.id))
		const newIdx = items.findIndex(i => String(i.id) === String(over.id))
		if (oldIdx === -1 || newIdx === -1 || oldIdx === newIdx) return

		const ids = arrayMove(items, oldIdx, newIdx).map(i => i.id)
		const p   = parseContainerId(containerId)

		if      (p.type === 'parts')         reorderParts       ({ bookId:    p.bookId,    ids })
		else if (p.type === 'chapters-book') reorderChapters    ({ bookId:    p.bookId,    ids })
		else if (p.type === 'chapters-part') reorderPartChapters({ partId:    p.partId,    ids })
		else if (p.type === 'scenes')        reorderScenes      ({ chapterId: p.chapterId, ids })
	}

	// ── Move chapter to a different container ────────────────────────────
	// Computes the new source list (gap closed) and target list (chapter
	// inserted), then calls the move endpoint which updates part_id and
	// renumbers both lists in a single DB transaction.
	function handleChapterReparent(active, over, fromContainer, toContainer) {
		const fromQk = getQueryKey(fromContainer)
		const toQk   = getQueryKey(toContainer)
		if (!fromQk || !toQk) return

		const sourceItems  = queryClient.getQueryData(fromQk) ?? []
		const targetItems  = queryClient.getQueryData(toQk)   ?? []
		const movedChapter = sourceItems.find(i => String(i.id) === String(active.id))
		if (!movedChapter) return

		// Remove from source
		const newSource = sourceItems.filter(i => String(i.id) !== String(active.id))

		// Determine insertion point in target:
		//   • Dropped onto a part row → insert at end of that part's chapter list
		//   • Dropped onto the ChapterListZone (droppable, not a sortable item) → end
		//   • Dropped onto a chapter item in the target list → insert before it
		const overIsPartOrContainer =
			over.data.current?.type === 'part' || isContainerId(String(over.id))
		const overIdx = overIsPartOrContainer
			? targetItems.length
			: targetItems.findIndex(i => String(i.id) === String(over.id))
		const insertIdx = overIdx === -1 ? targetItems.length : overIdx

		const newTarget = [...targetItems]
		newTarget.splice(insertIdx, 0, movedChapter)

		// newPartId is a UUID string (or null for book-direct).
		// Do NOT wrap in Number() — UUIDs are not numbers.
		const toParsed  = parseContainerId(toContainer)
		const newPartId = toParsed.type === 'chapters-part' ? toParsed.partId : null

		moveChapter({
			id:        active.id,
			partId:    newPartId,
			sourceIds: newSource.map(i => i.id),
			targetIds: newTarget.map(i => i.id),
		})
	}

	const overlayIcons = {
		part:    <BookmarksIcon sx={{ fontSize: 15, mr: 0.75 }} />,
		chapter: <MenuBookIcon  sx={{ fontSize: 15, mr: 0.75 }} />,
		scene:   <ArticleIcon   sx={{ fontSize: 15, mr: 0.75 }} />,
	}

	return (
		<DndContext
			sensors={sensors}
			collisionDetection={closestCenter}
			onDragStart={handleDragStart}
			onDragEnd={handleDragEnd}
		>
			<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
				<NavToolbar selection={selection} setSelection={setSelection} />
				<Divider />
				<Box sx={{ flex: 1, overflowY: 'auto' }}>
					<NavTree selection={selection} setSelection={setSelection} />
				</Box>
			</Box>

			{/* Floating chip that follows the cursor during a drag */}
			<DragOverlay dropAnimation={null}>
				{activeItem && (
					<Box sx={{
						display: 'flex', alignItems: 'center',
						px: 1.5, py: 0.75,
						bgcolor: 'background.paper',
						border: '1px solid', borderColor: 'primary.main',
						borderRadius: 1, boxShadow: 6,
						fontSize: 13, color: 'text.primary',
						maxWidth: 220, overflow: 'hidden',
						whiteSpace: 'nowrap', textOverflow: 'ellipsis',
					}}>
						{overlayIcons[activeItem.type]}
						{activeItem.title}
					</Box>
				)}
			</DragOverlay>
		</DndContext>
	)
}
