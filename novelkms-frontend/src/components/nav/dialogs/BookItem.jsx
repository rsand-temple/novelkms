import { useState, useRef } from 'react'
import { Box, Collapse, Divider, InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import MenuBookIcon     from '@mui/icons-material/MenuBook'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { useChapters }      from '../../hooks/useChapters'
import { useParts }         from '../../hooks/useParts'
import { useUpdateBook }    from '../../hooks/useBooks'
import PartItem             from './PartItem'
import ChapterItem          from './ChapterItem'
import ChapterListZone      from './ChapterListZone'
import { containerIds }     from '../../dnd/dndUtils'
import { useNavContextMenu } from './NavContextMenu'

export default function BookItem({ book, selection, setSelection }) {
	const [open, setOpen] = useState(false)

	// Auto-open when this book is externally navigated to (e.g. from ProjectShelf).
	const [prevSelBookId, setPrevSelBookId] = useState(selection.bookId)
	if (prevSelBookId !== selection.bookId) {
		setPrevSelBookId(selection.bookId)
		if (selection.bookId === book.id) setOpen(true)
	}

	const { data: parts }    = useParts(open ? book.id : null)
	const { data: chapters } = useChapters(open ? book.id : null)

	const isSelected = selection.bookId === book.id && !selection.partId && !selection.chapterId

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(book.id)
	// Uncontrolled input: defaultValue initialises when InputBase mounts
	// (i.e. when isRenaming first becomes true). Read via ref at commit time.
	const renameInputRef = useRef(null)
	const { mutate: updateBook } = useUpdateBook()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		if (newTitle && newTitle !== book.title) {
			// Pass all book fields so the PUT doesn't clear page-layout settings
			// or other metadata. BookDao.update null-guards the NOT NULL margin cols.
			updateBook({
				id:                 book.id,
				projectId:          book.projectId,
				title:              newTitle,
				subtitle:           book.subtitle,
				shortTitle:         book.shortTitle,
				notes:              book.notes,
				pageLayoutEnabled:  book.pageLayoutEnabled  ?? false,
				pageSizePreset:     book.pageSizePreset     ?? 'LETTER',
				pageWidthIn:        book.pageWidthIn,
				pageHeightIn:       book.pageHeightIn,
				pageMarginTopIn:    book.pageMarginTopIn,
				pageMarginBottomIn: book.pageMarginBottomIn,
				pageMarginInnerIn:  book.pageMarginInnerIn,
				pageMarginOuterIn:  book.pageMarginOuterIn,
			})
		}
		endRename()
	}

	const handleRenameKeyDown = (e) => {
		e.stopPropagation()
		if (e.key === 'Enter')  handleRenameCommit()
		if (e.key === 'Escape') endRename()
	}

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection((prev) => ({ ...prev, bookId: book.id, partId: null, chapterId: null, sceneId: null }))
	}

	const handleContextMenu = (e) => {
		setSelection((prev) => ({ ...prev, bookId: book.id, partId: null, chapterId: null, sceneId: null }))
		openContextMenu(e, 'book', {
			id:        book.id,
			title:     book.title,
			projectId: book.projectId ?? selection.projectId,
		})
	}

	// ── DnD container IDs ─────────────────────────────────────────────────────
	const partsContainerId    = containerIds.parts(String(book.id))
	const chapBookContainerId = containerIds.chaptersBook(String(book.id))
	const partIds             = (parts    ?? []).map(p => String(p.id))
	const chapterIds          = (chapters ?? []).map(c => String(c.id))

	const hasParts    = parts?.length    > 0
	const hasChapters = chapters?.length > 0

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
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

				{isRenaming ? (
					<InputBase
						inputRef={renameInputRef}
						defaultValue={book.title ?? ''}
						onBlur={handleRenameCommit}
						onKeyDown={handleRenameKeyDown}
						onClick={e => e.stopPropagation()}
						autoFocus
						fullWidth
						sx={{
							fontSize: '0.875rem',
							fontWeight: 500,
							borderBottom: '1px solid',
							borderColor: 'primary.main',
							'& .MuiInputBase-input': { p: 0 },
						}}
					/>
				) : (
					<ListItemText
						primary={book.title}
						slotProps={{ primary: { variant: 'body2', sx: { fontWeight: 500 } } }}
					/>
				)}
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

					{hasParts && hasChapters && <Divider sx={{ mx: 2, my: 0.25 }} />}

					{/* ── Direct-book chapters (part_id IS NULL) ───────────────── */}
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
