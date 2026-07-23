import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined'

/**
 * BookSummaryItem — fixed bottom leaf under a book for its whole-book Summary
 * (selection.aiDocType = 'bookSummary'). Opens the synopsis for rich-text
 * editing in EditorPanel. There is no book-level Memory document — memory is
 * per chapter only.
 *
 * Not draggable, no rename, no context menu of its own. Rendered last among
 * the book's children, after the Codex section.
 */
export default function BookSummaryItem({ bookId, selection, setSelection }) {
	const isSelected = selection.bookId === bookId
		&& selection.aiDocType === 'bookSummary'
		&& !selection.partId && !selection.chapterId

	const handleClick = () => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: 'bookSummary',
		}))
	}

	return (
		<ListItemButton selected={isSelected} onClick={handleClick} sx={{ pl: 10 }}>
			<ListItemIcon sx={{ minWidth: 28 }}>
				<SummarizeOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
			</ListItemIcon>
			<ListItemText
				primary="Book Summary"
				slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic', color: 'text.secondary' } } }}
			/>
		</ListItemButton>
	)
}
