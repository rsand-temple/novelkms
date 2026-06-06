import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import { useChapters } from '../../hooks/useChapters'
import ChapterItem from './ChapterItem'

export default function BookItem({ book, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: chapters } = useChapters(open ? book.id : null)

	const isSelected = selection.bookId === book.id && !selection.chapterId

	const handleClick = () => {
		setOpen((prev) => !prev)
		setSelection((prev) => ({ ...prev, bookId: book.id, chapterId: null, sceneId: null }))
	}

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				sx={{ pl: 4 }}
			>
				<ListItemIcon sx={{ minWidth: 28 }}>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<MenuBookIcon fontSize="small" />
				</ListItemIcon>
				<ListItemText
					primary={book.title}
					slotProps={{ primary: { variant: 'body2', sx: { fontWeight: 500 } } }}
				/>
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<Box>
					{chapters?.map((chapter) => (
						<ChapterItem
							key={chapter.id}
							chapter={chapter}
							selection={selection}
							setSelection={setSelection}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}
