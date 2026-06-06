import { ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import TheatersIcon from '@mui/icons-material/Theaters'

export default function SceneItem({ scene, selection, setSelection, depth = 0 }) {
	const isSelected = selection.sceneId === scene.id

	const handleClick = () => {
		setSelection((prev) => ({ ...prev, sceneId: scene.id }))
	}

	return (
		<ListItemButton
			selected={isSelected}
			onClick={handleClick}
			sx={{ pl: 10 + depth * 3 }}
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
