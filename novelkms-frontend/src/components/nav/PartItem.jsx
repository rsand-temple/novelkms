import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import BookmarksIcon    from '@mui/icons-material/Bookmarks'
import { useSortable, SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { usePartChapters } from '../../hooks/useParts'
import ChapterItem     from './ChapterItem'
import ChapterListZone from './ChapterListZone'
import { containerIds } from '../../dnd/dndUtils'

/**
 * PartItem — nav tree node for a Part.
 *
 * The part row itself is sortable (drag to reorder parts within the book).
 * Its chapter list is wrapped in a SortableContext + ChapterListZone so
 * that chapters can be dragged into it — including when it is empty, which
 * is the primary use case for new parts.
 *
 * bookId is required so the part can register itself with the correct
 * `parts-{bookId}` container, and so its chapters can reference
 * `chapters-part-{partId}` consistently.
 */
export default function PartItem({ part, bookId, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: chapters } = usePartChapters(open ? part.id : null)

	const isSelected = selection.partId === part.id && !selection.chapterId

	const chapterContainerId = containerIds.chaptersPart(String(part.id))
	const chapterIds = (chapters ?? []).map(c => String(c.id))

	// ── This part node is itself sortable within its book's parts list ───
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

	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection((prev) => ({ ...prev, partId: part.id, chapterId: null, sceneId: null }))
	}

	return (
		// setNodeRef on the outer div so that the entire row + collapsed
		// children move together during a drag.
		// {…attributes} carries aria-* and role for accessibility.
		// {…listeners} goes on the ListItemButton so that pointer events
		// for drag initiation are scoped to the clickable row area.
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
				sx={{ pl: 7 }}
				{...listeners}
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
				<ListItemText
					primary={part.title}
					slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic' } } }}
				/>
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				{/* ChapterListZone makes the chapter area droppable even
				    when the part has no chapters yet (the primary use case). */}
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
