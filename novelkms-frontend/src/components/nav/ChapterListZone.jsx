import { useDroppable } from '@dnd-kit/core'
import { Box } from '@mui/material'
import { useDndState } from '../../dnd/DndStateContext'

/**
 * ChapterListZone — wraps a list and registers it as a drop zone.
 *
 * Used by BookItem (for the whole book outline) and PartItem (for the chapters
 * inside a part).
 *
 * The part-level zone carries real weight now. Since the book outline merged
 * parts and direct chapters into one sortable list, dropping a chapter on a part
 * HEADER means "put it before/after this part" — so a part's chapter zone is the
 * only way to nest a chapter inside it. An 8px sliver was fine when the header
 * did the nesting; as the sole target it would be a pixel-hunt, and an empty part
 * would be nearly undroppable. So while a chapter drag is in flight the zone
 * opens up to a comfortable height and shows its edge.
 */
export default function ChapterListZone({ containerId, children }) {
	const { setNodeRef, isOver } = useDroppable({ id: containerId })
	const dragState = useDndState()

	const chapterDragActive = dragState?.activeType === 'chapter'
	const isPartZone = String(containerId).startsWith('chapters-part-')
	const armed = chapterDragActive && isPartZone

	return (
		<Box
			ref={setNodeRef}
			sx={{
				minHeight: armed ? 28 : 8,
				borderRadius: 1,
				transition: 'background-color 0.15s ease, min-height 0.15s ease',
				backgroundColor: isOver ? 'action.hover' : 'transparent',
				...(armed && {
					border: '1px dashed',
					borderColor: isOver ? 'primary.main' : 'divider',
				}),
			}}
		>
			{children}
		</Box>
	)
}
