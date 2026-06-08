import { ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import TheatersIcon from '@mui/icons-material/Theaters'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { containerIds } from '../../dnd/dndUtils'
import { useDndState } from '../../dnd/DndStateContext'

/**
 * SceneItem — nav tree leaf node for a Scene.
 *
 * Sortable within its chapter. Also supports cross-chapter drops:
 * when another scene is being dragged over this item from a different
 * chapter, a 2px primary-color line renders above or below (before/after)
 * based on the pointer's vertical position relative to this item's midpoint.
 *
 * The indicator state comes from DndStateContext (set by NavPanel's
 * onDragOver handler) rather than props, avoiding 5-level prop drilling.
 *
 * handleClick sets partId + chapterId in addition to sceneId so NavToolbar
 * always has correct context for the ↑↓ arrows even when the user opens
 * a chapter via the expand arrow without clicking the chapter row first.
 */
export default function SceneItem({ scene, chapterId, partId, selection, setSelection, depth = 0 }) {
	const isSelected = selection.sceneId === scene.id

	// ── Drop indicator ───────────────────────────────────────────────────
	const dragState     = useDndState()
	const isDropTarget  = dragState?.activeType === 'scene' && dragState.overId === String(scene.id)
	const showTopLine   = isDropTarget && dragState.insertBefore
	const showBottomLine= isDropTarget && !dragState.insertBefore

	// ── Sortable ─────────────────────────────────────────────────────────
	const {
		attributes,
		listeners,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: String(scene.id),
		data: {
			type:        'scene',
			title:       scene.title || 'Untitled Scene',
			containerId: containerIds.scenes(String(chapterId)),
			chapterId:   String(chapterId),
		},
	})

	const handleClick = () => {
		setSelection((prev) => ({
			...prev,
			partId:    partId ?? null,
			chapterId,
			sceneId:   scene.id,
		}))
	}

	return (
		<ListItemButton
			ref={setNodeRef}
			style={{
				transform:  CSS.Transform.toString(transform),
				transition,
				opacity:    isDragging ? 0.4 : 1,
			}}
			selected={isSelected}
			onClick={handleClick}
			sx={{
				pl: 10 + depth * 3,
				// Before/after indicator: inset box-shadow draws a flush 2px line
				// on the top or bottom edge without shifting the item's layout.
				...(showTopLine    && { boxShadow: theme => `inset 0  2px 0 ${theme.palette.primary.main}` }),
				...(showBottomLine && { boxShadow: theme => `inset 0 -2px 0 ${theme.palette.primary.main}` }),
			}}
			{...attributes}
			{...listeners}
		>
			<ListItemIcon sx={{ minWidth: 28 }}>
				<TheatersIcon fontSize="small" />
			</ListItemIcon>
			<ListItemText
				primary={scene.title}
				slotProps={{ primary: { variant: 'body2' } }}
			/>
		</ListItemButton>
	)
}
