import { useState, useRef } from 'react'
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
import TrashFooter from '../nav/TrashFooter'
import { useReorderPartChapters } from '../../hooks/useParts'
import { useMoveChapter } from '../../hooks/useChapters'
import { useReorderOutline } from '../../hooks/useOutline'
import { useReorderScenes, useMoveScene } from '../../hooks/useScenes'
import { DndStateContext } from '../../dnd/DndStateContext'
import {
	containerIds,
	parseContainerId,
	isContainerId,
	readContainerItems,
	toOutlineRefs,
} from '../../dnd/dndUtils'
import { NavContextMenuProvider } from '../nav/NavContextMenu'

export default function NavPanel({ selection, setSelection }) {
	const queryClient = useQueryClient()
	const [activeItem, setActiveItem] = useState(null)  // { type, title } — drives DragOverlay
	const [dragState, setDragState] = useState(null)    // { activeType, overId, insertBefore }

	// Ref attached to the scrollable nav tree Box.
	// NavContextMenuProvider uses it to scope the F2 key listener so it
	// doesn't fire while the editor pane is focused.
	const navTreeRef = useRef(null)

	const sensors = useSensors(
		useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
	)

	const { mutate: reorderOutline } = useReorderOutline()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderScenes } = useReorderScenes()
	const { mutate: moveChapter } = useMoveChapter()
	const { mutate: moveScene } = useMoveScene()

	// ── Drag start ───────────────────────────────────────────────────────
	function handleDragStart({ active }) {
		const d = active.data.current
		setActiveItem({ type: d?.type, title: d?.title ?? '…' })
	}

	// ── Drag over ────────────────────────────────────────────────────────
	// Two jobs:
	//   - scenes:   before/after indicator for a cross-chapter move
	//   - chapters: which part header is being hovered, so PartItem can spring
	//               open and its chapter zone can arm itself. The header itself
	//               is a positional target now (the outline is one sortable list),
	//               so the zone inside is the only way to nest — it has to be
	//               reachable without dropping first.
	function handleDragOver({ active, over }) {
		const activeType = active.data.current?.type

		if (!over) {
			setDragState(null)
			return
		}

		if (activeType === 'chapter') {
			const hoverPartId = over.data.current?.type === 'part' ? String(over.id) : null
			setDragState({ activeType: 'chapter', overId: String(over.id), insertBefore: true, hoverPartId })
			return
		}

		if (activeType !== 'scene') {
			// A part drag needs no live state, but the zones still want to know a
			// chapter ISN'T being dragged, so clear rather than leave it stale.
			setDragState(null)
			return
		}

		const fromContainer = active.data.current?.containerId
		const toContainer = over.data.current?.sortable?.containerId

		if (!toContainer || fromContainer === toContainer) {
			setDragState(null)
			return
		}

		const overRect = over.rect
		const activeRect = active.rect.current?.translated
		let insertBefore = true
		if (overRect && activeRect) {
			const activeMidY = activeRect.top + activeRect.height / 2
			const overMidY = overRect.top + overRect.height / 2
			insertBefore = activeMidY < overMidY
		}

		setDragState({ activeType: 'scene', overId: String(over.id), insertBefore, hoverPartId: null })
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
		const savedDragState = dragState
		setActiveItem(null)
		setDragState(null)

		if (!over || String(active.id) === String(over.id)) return

		const activeType = active.data.current?.type
		const fromContainer = active.data.current?.containerId
		const toContainer = resolveToContainer(over)
		if (!fromContainer || !toContainer) return

		if (fromContainer === toContainer) {
			// Includes a chapter dropped on a PART header: both now live in the book
			// outline, so that is a plain reposition — the prologue-above-Part-I case.
			// Nesting into a part happens by dropping into the part's chapter zone,
			// which is a different container and falls through below.
			handleSameContainerReorder(active, over, fromContainer)
		} else if (activeType === 'chapter') {
			handleChapterReparent(active, over, fromContainer, toContainer)
		} else if (activeType === 'scene') {
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
		const items = readContainerItems(queryClient, containerId)
		if (items.length === 0) return

		const oldIdx = items.findIndex(i => String(i.id) === String(active.id))
		const newIdx = items.findIndex(i => String(i.id) === String(over.id))
		if (oldIdx === -1 || newIdx === -1 || oldIdx === newIdx) return

		const moved = arrayMove(items, oldIdx, newIdx)
		const p = parseContainerId(containerId)

		if (p.type === 'outline') {
			// One list, two tables. The typed refs tell the server which row each
			// entry is, so it knows whether to renumber a part or a chapter.
			reorderOutline({ bookId: p.bookId, items: toOutlineRefs(moved) })
		} else if (p.type === 'chapters-part') {
			reorderPartChapters({ partId: p.partId, ids: moved.map(i => i.id) })
		} else if (p.type === 'scenes') {
			reorderScenes({ chapterId: p.chapterId, ids: moved.map(i => i.id) })
		}
	}

	// ── Chapter reparent (cross-container) ───────────────────────────────
	// Either end may be the book outline (parts + direct chapters, two tables) or
	// a part's own chapter list (one table). Both ends are therefore named and
	// sent as TYPED refs — a bare UUID cannot tell the server which table a row
	// lives in, and it has to know which one to renumber.
	function handleChapterReparent(active, over, fromContainer, toContainer) {
		const sourceItems = readContainerItems(queryClient, fromContainer)
		const targetItems = readContainerItems(queryClient, toContainer)

		const movedChapter = sourceItems.find(i => String(i.id) === String(active.id))
		if (!movedChapter) return

		const newSource = sourceItems.filter(i => String(i.id) !== String(active.id))

		// Dropping on the zone itself (rather than on a row) means "append".
		const overIdx = isContainerId(String(over.id))
			? targetItems.length
			: targetItems.findIndex(i => String(i.id) === String(over.id))
		const insertIdx = overIdx === -1 ? targetItems.length : overIdx

		const newTarget = [...targetItems]
		newTarget.splice(insertIdx, 0, movedChapter)

		const fromParsed = parseContainerId(fromContainer)
		const toParsed = parseContainerId(toContainer)

		moveChapter({
			id: active.id,
			partId:       toParsed.type   === 'chapters-part' ? toParsed.partId   : null,
			sourcePartId: fromParsed.type === 'chapters-part' ? fromParsed.partId : null,
			sourceItems:  toOutlineRefs(newSource),
			targetItems:  toOutlineRefs(newTarget),
		})
	}

	// ── Scene reparent (cross-chapter) ───────────────────────────────────
	function handleSceneReparent(active, over, fromContainer, toContainer, savedDragState) {
		const sourceItems = readContainerItems(queryClient, fromContainer)
		const targetItems = readContainerItems(queryClient, toContainer)
		const movedScene = sourceItems.find(i => String(i.id) === String(active.id))
		if (!movedScene) return

		const newSource = sourceItems.filter(i => String(i.id) !== String(active.id))

		const overIdx = targetItems.findIndex(i => String(i.id) === String(over.id))
		let insertIdx
		if (overIdx === -1) {
			insertIdx = targetItems.length
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
		part:    <BookmarksIcon sx={{ fontSize: 15, mr: 0.75 }} />,
		chapter: <MenuBookIcon  sx={{ fontSize: 15, mr: 0.75 }} />,
		scene:   <ArticleIcon   sx={{ fontSize: 15, mr: 0.75 }} />,
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
			{/* NavContextMenuProvider wraps the nav contents and renders the
			    context menu + all add/delete dialogs as MUI portals.
			    navTreeRef scopes the F2 listener to the scrollable tree Box. */}
			<NavContextMenuProvider
				selection={selection}
				setSelection={setSelection}
				navRef={navTreeRef}
			>
				<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
					<NavToolbar selection={selection} setSelection={setSelection} />
					<Divider />
					<DndStateContext.Provider value={dragState}>
						{/* navTreeRef goes here so F2 fires only when focus is in the tree */}
						<Box
							ref={navTreeRef}
							sx={{
								flex: 1,
								minHeight: 0,
								overflowY: 'auto',
								overflowX: 'hidden',
							}}
						>
							<NavTree selection={selection} setSelection={setSelection} />
						</Box>
					</DndStateContext.Provider>
					<Divider />
					<TrashFooter selection={selection} setSelection={setSelection} />
				</Box>
			</NavContextMenuProvider>

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
