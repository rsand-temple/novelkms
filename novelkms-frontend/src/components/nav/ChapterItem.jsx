import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ArticleIcon from '@mui/icons-material/Article'
import { useScenes } from '../../hooks/useScenes'
import SceneItem from './SceneItem'

export default function ChapterItem({ chapter, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: scenes } = useScenes(open ? chapter.id : null)

	const isSelected = selection.chapterId === chapter.id && !selection.sceneId

	const handleClick = () => {
		setOpen((prev) => !prev)
		setSelection((prev) => ({ ...prev, chapterId: chapter.id, sceneId: null }))
	}

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				sx={{ pl: 7 }}
			>
				<ListItemIcon sx={{ minWidth: 28 }}>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<ArticleIcon fontSize="small" />
				</ListItemIcon>
				<ListItemText
					primary={chapter.title}
					slotProps={{ primary: { variant: 'body2' } }}
				/>
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<Box>
					{scenes?.map((scene) => (
						<SceneItem
							key={scene.id}
							scene={scene}
							selection={selection}
							setSelection={setSelection}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}