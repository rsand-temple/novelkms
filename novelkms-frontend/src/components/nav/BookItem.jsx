import { useState } from 'react'
import { Box, Collapse, Divider, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon  from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import MenuBookIcon    from '@mui/icons-material/MenuBook'
import { useChapters } from '../../hooks/useChapters'
import { useParts }    from '../../hooks/useParts'
import PartItem    from './PartItem'
import ChapterItem from './ChapterItem'

export default function BookItem({ book, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: parts    } = useParts(open ? book.id : null)
	const { data: chapters } = useChapters(open ? book.id : null)

	// Book is selected only when no part and no chapter are active
	const isSelected = selection.bookId === book.id && !selection.partId && !selection.chapterId

	const handleClick = () => {
		setOpen((prev) => !prev)
		setSelection((prev) => ({ ...prev, bookId: book.id, partId: null, chapterId: null, sceneId: null }))
	}

	const hasParts    = parts?.length    > 0
	const hasChapters = chapters?.length > 0

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
					{/* Parts first */}
					{parts?.map((part) => (
						<PartItem
							key={part.id}
							part={part}
							selection={selection}
							setSelection={setSelection}
						/>
					))}

					{/* Thin divider between parts and direct chapters when both exist */}
					{hasParts && hasChapters && (
						<Divider sx={{ mx: 2, my: 0.25 }} />
					)}

					{/* Direct-book chapters (part_id IS NULL) */}
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
