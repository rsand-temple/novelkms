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
import MenuBookIcon from '@mui/icons-material/MenuBook'
import ArticleIcon from '@mui/icons-material/Article'
import NavToolbar from '../nav/NavToolbar'
import NavTree from '../nav/NavTree'
import { useReorderParts, useReorderPartChapters } from '../../hooks/useParts'
import { useReorderChapters, useMoveChapter } from '../../hooks/useChapters'
import { useReorderScenes, useMoveScene } from '../../hooks/useScenes'
import { DndStateContext } from '../../dnd/DndStateContext'
import { containerIds, parseContainerId, getQueryKey, isContainerId } from '../../dnd/dndUtils'

export default function NavPanel({ selection, setSelection }) {
	const queryClient = useQueryClient()
	const [activeItem, setActiveItem] = useState(null)  // { type, title } — drives DragOverlay
	const [dragState, setDragState] = useState(null)  // { activeType, overId, insertBefore } — drives indicator

	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
	)

	const { mutate: reorderParts } = useReorderParts()
	const { mutate: reorderChapters } = useReorderChapters()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderScenes } = useReorderScenes()
	const { mutate: moveChapter } = useMoveChapter()
	const { mutate: moveScene } = useMoveScene()

	// ── Drag start ───────────────────────────────────────────────────────
	function handleDragStart({ active }) {
		const d = active.data.current
		setActiveItem({ type: d?.type, title: d?.title ?? '…' })
	}

	// ── Drag over: track before/after for cross-chapter scene indicator ──
	// Fires continuously while dragging. We only care about cross-container
	// scene drags (same-container reorders are handled by @dnd-kit visually).
	function handleDragOver({ active, over }) {
		if (!over || active.data.current?.type !== 'scene') {
			setDragState(null)
			return
		}

		const fromContainer = active.data.current?.containerId
		// Only sortable items carry sortable.containerId — skip if over a droppable zone
		const toContainer = over.data.current?.sortable?.containerId

		if (!toContainer || fromContainer === toContainer) {
			setDragState(null)
			return
		}

		// Determine before/after from the vertical midpoints of the active ghost
		// and the hovered item. This drives the indicator line in SceneItem.
		const overRect = over.rect
		const activeRect = active.rect.current?.translated
		let insertBefore = true
		if (overRect && activeRect) {
			const activeMidY = activeRect.top + activeRect.height / 2
			const overMidY = overRect.top + overRect.height / 2
			insertBefore = activeMidY < overMidY
		}

		setDragState({ activeType: 'scene', overId: String(over.id), insertBefore })
	}

	// ── Drag cancel ──────────────────────────────────────────────────────
	function handleDragCancel() {
		setActiveItem(null)
		setDragState(null)
	}

	// ── Resolve target container ─────────────────────────────────────────
	function resolveToContainer(over) {
		const viaSortable = over.data.current?.sortable?.containerId
		if (viaSortable) return viaSortable
		if (isContainerId(String(over.id))) return String(over.id)
		return null
	}

	// ── Drop ─────────────────────────────────────────────────────────────
	function handleDragEnd({ active, over }) {
		// Capture before clearing — handleSceneReparent needs insertBefore
		const savedDragState = dragState
		setActiveItem(null)
		setDragState(null)

		if (!over || String(active.id) === String(over.id)) return

		const activeType = active.data.current?.type
		const fromContainer = active.data.current?.containerId
		const toContainer = resolveToContainer(over)
		if (!fromContainer || !toContainer) return

		if (fromContainer === toContainer) {
			// ── Same list: reorder ───────────────────────────────────────
			handleSameContainerReorder(active, over, fromContainer)

		} else if (activeType === 'chapter') {
			// ── Chapter: reorder or reparent ─────────────────────────────
			//
			// If the user dragged onto a part row, redirect to that part's
			// chapter list (works whether the part is open or closed).
			let effectiveToContainer = toContainer
			if (over.data.current?.type === 'part') {
				effectiveToContainer = containerIds.chaptersPart(String(over.id))
			}
			if (effectiveToContainer !== fromContainer) {
				handleChapterReparent(active, over, fromContainer, effectiveToContainer)
			}

		} else if (activeType === 'scene') {
			// ── Scene: cross-chapter move ─────────────────────────────────
			//
			// If dropped on a chapter row, append to end of that chapter.
			// If dropped on a scene in another chapter, use before/after from
			// savedDragState (computed in handleDragOver).
			let effectiveToContainer = toContainer
			if (over.data.current?.type === 'chapter') {
				effectiveToContainer = containerIds.scenes(String(over.id))
			}
			if (effectiveToContainer !== fromContainer) {
				handleSceneReparent(active, over, fromContainer, effectiveToContainer, savedDragState)
			}
		}
	}

	// ── Same-container reorder ───────────────────────────────────────────
	function handleSameContainerReorder(active, over, containerId) {
		const qk = getQueryKey(containerId)
		if (!qk) return

		const items = queryClient.getQueryData(qk) ?? []
		const oldIdx = items.findIndex(i => String(i.id) === String(active.id))
		const newIdx = items.findIndex(i => String(i.id) === String(over.id))
		if (oldIdx === -1 || newIdx === -1 || oldIdx === newIdx) return

		const ids = arrayMove(items, oldIdx, newIdx).map(i => i.id)
		const p = parseContainerId(containerId)

		if (p.type === 'parts') reorderParts({ bookId: p.bookId, ids })
		else if (p.type === 'chapters-book') reorderChapters({ bookId: p.bookId, ids })
		else if (p.type === 'chapters-part') reorderPartChapters({ partId: p.partId, ids })
		else if (p.type === 'scenes') reorderScenes({ chapterId: p.chapterId, ids })
	}

	// ── Chapter reparent (cross-container) ───────────────────────────────
	function handleChapterReparent(active, over, fromContainer, toContainer) {
		const fromQk = getQueryKey(fromContainer)
		const toQk = getQueryKey(toContainer)
		if (!fromQk || !toQk) return

		const sourceItems = queryClient.getQueryData(fromQk) ?? []
		const targetItems = queryClient.getQueryData(toQk) ?? []
		const movedChapter = sourceItems.find(i => String(i.id) === String(active.id))
		if (!movedChapter) return

		const newSource = sourceItems.filter(i => String(i.id) !== String(active.id))

		const overIsPartOrContainer =
			over.data.current?.type === 'part' || isContainerId(String(over.id))
		const overIdx = overIsPartOrContainer
			? targetItems.length
			: targetItems.findIndex(i => String(i.id) === String(over.id))
		const insertIdx = overIdx === -1 ? targetItems.length : overIdx

		const newTarget = [...targetItems]
		newTarget.splice(insertIdx, 0, movedChapter)

		const toParsed = parseContainerId(toContainer)
		const newPartId = toParsed.type === 'chapters-part' ? toParsed.partId : null

		moveChapter({
			id: active.id,
			partId: newPartId,
			sourceIds: newSource.map(i => i.id),
			targetIds: newTarget.map(i => i.id),
		})
	}

	// ── Scene reparent (cross-chapter) ───────────────────────────────────
	function handleSceneReparent(active, over, fromContainer, toContainer, savedDragState) {
		const fromQk = getQueryKey(fromContainer)
		const toQk = getQueryKey(toContainer)
		if (!fromQk || !toQk) return

		const sourceItems = queryClient.getQueryData(fromQk) ?? []
		const targetItems = queryClient.getQueryData(toQk) ?? []
		const movedScene = sourceItems.find(i => String(i.id) === String(active.id))
		if (!movedScene) return

		const newSource = sourceItems.filter(i => String(i.id) !== String(active.id))

		// Determine precise insertion point using the before/after value
		// captured during onDragOver. Falls back to "append" when dropped
		// on a chapter row (overIdx === -1) or a droppable zone.
		const overIdx = targetItems.findIndex(i => String(i.id) === String(over.id))
		let insertIdx
		if (overIdx === -1) {
			insertIdx = targetItems.length                             // dropped on chapter row → end
		} else {
			const insertBefore = savedDragState?.insertBefore ?? true
			insertIdx = insertBefore ? overIdx : overIdx + 1
		}

		const newTarget = [...targetItems]
		newTarget.splice(insertIdx, 0, movedScene)

		const toParsed = parseContainerId(toContainer)

		moveScene({
			id: active.id,
			chapterId: toParsed.chapterId,
			sourceIds: newSource.map(i => i.id),
			targetIds: newTarget.map(i => i.id),
		})
	}

	// ── Overlay chip ─────────────────────────────────────────────────────
	const overlayIcons = {
		part: <BookmarksIcon sx={{ fontSize: 15, mr: 0.75 }} />,
		chapter: <MenuBookIcon sx={{ fontSize: 15, mr: 0.75 }} />,
		scene: <ArticleIcon sx={{ fontSize: 15, mr: 0.75 }} />,
	}

	return (
		<DndContext
			sensors={sensors}
			collisionDetection={closestCenter}
			onDragStart={handleDragStart}
			onDragOver={handleDragOver}
			onDragEnd={handleDragEnd}
			onDragCancel={handleDragCancel}
		>
			<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
				<NavToolbar selection={selection} setSelection={setSelection} />
				<Divider />
				{/* DndStateContext makes live drag state available to SceneItem
				    for the before/after indicator line, without prop-drilling. */}
				<DndStateContext.Provider value={dragState}>
					<Box sx={{ flex: 1, overflowY: 'auto' }}>
						<NavTree selection={selection} setSelection={setSelection} />
					</Box>
				</DndStateContext.Provider>
			</Box>

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
