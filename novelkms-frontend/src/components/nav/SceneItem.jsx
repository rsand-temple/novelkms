import { ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import TheatersIcon from '@mui/icons-material/Theaters'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { containerIds } from '../../dnd/dndUtils'

/**
 * SceneItem — nav tree leaf node for a Scene.
 *
 * Scenes are sortable within their chapter's scene list. Cross-chapter
 * scene moves are not supported in this phase.
 *
 * handleClick sets chapterId AND partId in addition to sceneId so that
 * NavToolbar always has the correct context for the ↑↓ arrows — even
 * when the user expands a chapter via the arrow icon without clicking
 * the chapter row first (which would leave chapterId stale).
 */
export default function SceneItem({ scene, chapterId, partId, selection, setSelection, depth = 0 }) {
	const isSelected = selection.sceneId === scene.id

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
			sx={{ pl: 10 + depth * 3 }}
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
