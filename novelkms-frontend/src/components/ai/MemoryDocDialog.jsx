import {
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
} from '@mui/material'
import ChapterMemoryEditor from './ChapterMemoryEditor'

/**
 * MemoryDocDialog
 *
 * Standalone editor for a chapter's memory document, opened from the nav context
 * menu. Works for any chapter (not just the one open in the editor), which is why
 * the nav uses this rather than the ReviewRail's Memory tab. The editor body is
 * the shared ChapterMemoryEditor, mounted fresh each open.
 *
 * Props:
 *   open       {boolean}
 *   onClose    {() => void}
 *   chapterId  {string}
 *   bookId     {string|undefined}
 *   title      {string|undefined}  chapter title, for the dialog heading
 */
export default function MemoryDocDialog({ open, onClose, chapterId, bookId, title }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle>
				Memory document{title?.trim() ? ` — ${title.trim()}` : ''}
			</DialogTitle>
			<DialogContent dividers>
				{open && chapterId && (
					<ChapterMemoryEditor chapterId={chapterId} bookId={bookId} credentialId={null} />
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
