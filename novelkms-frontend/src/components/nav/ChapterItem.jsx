import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ArticleIcon from '@mui/icons-material/Article'
import { useScenes } from '../../hooks/useScenes'
import SceneItem from './SceneItem'

export default function ChapterItem({ chapter, selection, setSelection, depth = 0 }) {
	const [open, setOpen] = useState(false)
	const { data: scenes } = useScenes(open ? chapter.id : null)

	const isSelected = selection.chapterId === chapter.id && !selection.sceneId

	const handleClick = () => {
		setOpen((prev) => !prev)
		// Carry the chapter's own partId into selection so the toolbar and
		// PropertiesPanel know whether this chapter lives inside a part.
		// chapter.partId is null for direct-book chapters, which clears any
		// stale partId left from a previous part selection.
		setSelection((prev) => ({
			...prev,
			partId: chapter.partId ?? null,
			chapterId: chapter.id,
			sceneId: null,
		}))
	}

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				sx={{ pl: 7 + depth * 3 }}
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
							depth={depth}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}
