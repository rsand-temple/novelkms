import { useState } from 'react'
import { Box, Collapse, Divider, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { useChapters } from '../../hooks/useChapters'
import { useParts } from '../../hooks/useParts'
import PartItem from './PartItem'
import ChapterItem from './ChapterItem'
import ChapterListZone from './ChapterListZone'
import { containerIds } from '../../dnd/dndUtils'

export default function BookItem({ book, selection, setSelection }) {
	const [open, setOpen] = useState(false)

	// React-sanctioned pattern for responding to prop changes without useEffect:
	// track the previous selection.bookId in state and call setOpen during render
	// when we detect a transition INTO this book.  This auto-opens the nav tree
	// when the user clicks a book thumbnail in ProjectShelf (external navigation)
	// while still allowing manual collapse via the expand arrow.
	const [prevSelBookId, setPrevSelBookId] = useState(selection.bookId)
	if (prevSelBookId !== selection.bookId) {
		setPrevSelBookId(selection.bookId)
		if (selection.bookId === book.id) {
			setOpen(true)
		}
	}

	const { data: parts } = useParts(open ? book.id : null)
	const { data: chapters } = useChapters(open ? book.id : null)

	const isSelected = selection.bookId === book.id && !selection.partId && !selection.chapterId

	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection((prev) => ({ ...prev, bookId: book.id, partId: null, chapterId: null, sceneId: null }))
	}

	const hasParts = parts?.length > 0
	const hasChapters = chapters?.length > 0

	// Container IDs — must match the keys produced by dndUtils.containerIds
	const partsContainerId = containerIds.parts(String(book.id))
	const chapBookContainerId = containerIds.chaptersBook(String(book.id))

	// Item ID arrays must use the same type as useSortable's `id` prop (strings)
	const partIds = (parts ?? []).map(p => String(p.id))
	const chapterIds = (chapters ?? []).map(c => String(c.id))

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				sx={{ pl: 4 }}
			>
				<ListItemIcon
					sx={{ minWidth: 28, cursor: 'pointer' }}
					onClick={handleExpandToggle}
				>
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
					{/* ── Parts ────────────────────────────────────────────────── */}
					<SortableContext
						id={partsContainerId}
						items={partIds}
						strategy={verticalListSortingStrategy}
					>
						{(parts ?? []).map((part) => (
							<PartItem
								key={part.id}
								part={part}
								bookId={book.id}
								selection={selection}
								setSelection={setSelection}
							/>
						))}
					</SortableContext>

					{/* Thin divider between parts and direct chapters */}
					{hasParts && hasChapters && (
						<Divider sx={{ mx: 2, my: 0.25 }} />
					)}

					{/* ── Direct-book chapters (part_id IS NULL) ───────────────── */}
					{/* ChapterListZone makes this area droppable even when empty,
					    so a chapter can be dragged out of a part back to book-direct. */}
					<SortableContext
						id={chapBookContainerId}
						items={chapterIds}
						strategy={verticalListSortingStrategy}
					>
						<ChapterListZone containerId={chapBookContainerId}>
							{(chapters ?? []).map((chapter) => (
								<ChapterItem
									key={chapter.id}
									chapter={chapter}
									bookId={book.id}
									partId={null}
									selection={selection}
									setSelection={setSelection}
								/>
							))}
						</ChapterListZone>
					</SortableContext>
				</Box>
			</Collapse>
		</Box>
	)
}