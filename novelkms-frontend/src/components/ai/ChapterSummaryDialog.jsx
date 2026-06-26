import {
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
} from '@mui/material'
import ChapterSummaryEditor from './ChapterSummaryEditor'

/**
 * ChapterSummaryDialog
 *
 * Standalone editor for one chapter's summary, opened from the nav context menu.
 * Works for any chapter. The body is the shared ChapterSummaryEditor, mounted
 * fresh each open.
 *
 * Props:
 *   open          {boolean}
 *   onClose       {() => void}
 *   chapterId     {string}
 *   bookId        {string|undefined}
 *   title         {string|undefined}  chapter title, for the heading
 *   credentialId  {string|null}
 */
export default function ChapterSummaryDialog({ open, onClose, chapterId, bookId, title, credentialId = null }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle>
				Chapter summary{title?.trim() ? ` — ${title.trim()}` : ''}
			</DialogTitle>
			<DialogContent dividers>
				{open && chapterId && (
					<ChapterSummaryEditor chapterId={chapterId} bookId={bookId} credentialId={credentialId} />
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
