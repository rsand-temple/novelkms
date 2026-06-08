import { useDroppable } from '@dnd-kit/core'
import { Box } from '@mui/material'

/**
 * ChapterListZone — wraps a chapter list and registers it as a drop zone.
 *
 * Even when the list is empty (e.g. a freshly created Part) the zone has
 * enough minimum height to accept a drop, and highlights with action.hover
 * while a draggable chapter is over it.
 *
 * Used by BookItem (for the direct-book chapter area) and PartItem (for
 * the chapters-inside-a-part area).
 */
export default function ChapterListZone({ containerId, children }) {
	const { setNodeRef, isOver } = useDroppable({ id: containerId })

	return (
		<Box
			ref={setNodeRef}
			sx={{
				minHeight: 8,
				borderRadius: 1,
				transition: 'background-color 0.15s ease',
				backgroundColor: isOver ? 'action.hover' : 'transparent',
			}}
		>
			{children}
		</Box>
	)
}
