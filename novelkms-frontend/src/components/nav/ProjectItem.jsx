import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderIcon from '@mui/icons-material/Folder'
import { useBooks } from '../../hooks/useBooks'
import BookItem from './BookItem'

export default function ProjectItem({ project, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: books } = useBooks(open ? project.id : null)

	const isSelected = selection.projectId === project.id && !selection.bookId

	const handleClick = () => {
		setOpen((prev) => !prev)
		setSelection({ projectId: project.id, bookId: null, chapterId: null, sceneId: null })
	}

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				sx={{ pl: 1 }}
			>
				<ListItemIcon sx={{ minWidth: 28 }}>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<FolderIcon fontSize="small" />
				</ListItemIcon>
				<ListItemText
					primary={project.title}
					primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
				/>
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<Box>
					{books?.map((book) => (
						<BookItem
							key={book.id}
							book={book}
							selection={selection}
							setSelection={setSelection}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}