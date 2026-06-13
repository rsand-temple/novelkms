import { useState, useRef } from 'react'
import { Box, Collapse, InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import BookmarksIcon    from '@mui/icons-material/Bookmarks'
import { useSortable, SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { usePartChapters, useUpdatePart } from '../../hooks/useParts'
import ChapterItem          from './ChapterItem'
import ChapterListZone      from './ChapterListZone'
import { containerIds }     from '../../dnd/dndUtils'
import { useNavContextMenu } from './NavContextMenu'

/**
 * PartItem — nav tree node for a Part.
 *
 * The part row is sortable (drag to reorder parts within the book).
 * DnD listeners are disabled while the inline rename InputBase is active.
 */
export default function PartItem({ part, bookId, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: chapters } = usePartChapters(open ? part.id : null)

	const isSelected = selection.partId === part.id && !selection.chapterId

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(part.id)
	// Uncontrolled input: defaultValue initialises when InputBase mounts.
	const renameInputRef = useRef(null)
	const { mutate: updatePart } = useUpdatePart()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		if (newTitle && newTitle !== part.title) {
			updatePart({
				id:       part.id,
				bookId,
				title:    newTitle,
				subtitle: part.subtitle,
				notes:    part.notes,
			})
		}
		endRename()
	}

	const handleRenameKeyDown = (e) => {
		e.stopPropagation()
		if (e.key === 'Enter')  handleRenameCommit()
		if (e.key === 'Escape') endRename()
	}

	// ── DnD ───────────────────────────────────────────────────────────────────
	const chapterContainerId = containerIds.chaptersPart(String(part.id))
	const chapterIds = (chapters ?? []).map(c => String(c.id))

	const {
		attributes,
		listeners,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: String(part.id),
		data: {
			type:        'part',
			title:       part.title || 'Untitled Part',
			containerId: containerIds.parts(String(bookId)),
			bookId:      String(bookId),
		},
	})

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection((prev) => ({ ...prev, partId: part.id, chapterId: null, sceneId: null }))
	}

	const handleContextMenu = (e) => {
		setSelection((prev) => ({ ...prev, partId: part.id, chapterId: null, sceneId: null }))
		openContextMenu(e, 'part', {
			id:        part.id,
			title:     part.title,
			bookId,
			projectId: selection.projectId,
		})
	}

	return (
		<div
			ref={setNodeRef}
			style={{
				transform:  CSS.Transform.toString(transform),
				transition,
				opacity:    isDragging ? 0.4 : 1,
			}}
			{...attributes}
		>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
				sx={{ pl: 7 }}
				// Gate DnD listeners off during rename to prevent drag initiation
				// while the user is typing in the InputBase.
				{...(isRenaming ? {} : listeners)}
			>
				<ListItemIcon
					sx={{ minWidth: 28, cursor: 'pointer' }}
					onClick={handleExpandToggle}
				>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<BookmarksIcon fontSize="small" />
				</ListItemIcon>

				{isRenaming ? (
					<InputBase
						inputRef={renameInputRef}
						defaultValue={part.title ?? ''}
						onBlur={handleRenameCommit}
						onKeyDown={handleRenameKeyDown}
						onClick={e => e.stopPropagation()}
						autoFocus
						fullWidth
						sx={{
							fontSize:  '0.875rem',
							fontStyle: 'italic',
							borderBottom: '1px solid',
							borderColor:  'primary.main',
							'& .MuiInputBase-input': { p: 0 },
						}}
					/>
				) : (
					<ListItemText
						primary={part.title}
						slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic' } } }}
					/>
				)}
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<SortableContext
					id={chapterContainerId}
					items={chapterIds}
					strategy={verticalListSortingStrategy}
				>
					<ChapterListZone containerId={chapterContainerId}>
						{(chapters ?? []).map((chapter) => (
							<ChapterItem
								key={chapter.id}
								chapter={chapter}
								bookId={bookId}
								partId={part.id}
								depth={1}
								selection={selection}
								setSelection={setSelection}
							/>
						))}
					</ChapterListZone>
				</SortableContext>
			</Collapse>
		</div>
	)
}
