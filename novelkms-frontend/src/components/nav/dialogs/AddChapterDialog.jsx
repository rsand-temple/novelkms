import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateChapter } from '../../../hooks/useChapters'

/**
 * `anchor` — { id, before } | null.
 *
 * null appends. Otherwise the new chapter (the anchor may be a part OR a chapter — both are outline items) is inserted immediately before/after the
 * anchor item, which the backend does by opening a slot at that position rather
 * than at the end of the sequence.
 */
export default function AddChapterDialog({ open, onClose, bookId, anchor = null }) {
	const [title, setTitle] = useState('')
	const createChapter = useCreateChapter()

	const handleSubmit = () => {
		if (!title.trim()) return
		createChapter.mutate(
			{
				bookId,
				data: {
					title: title.trim(),
					...(anchor && { anchorId: anchor.id, before: anchor.before }),
				},
			},
			{
				onSuccess: () => {
					setTitle('')
					onClose()
				},
			}
		)
	}

	const handleClose = () => {
		setTitle('')
		onClose()
	}

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth disableRestoreFocus>
			<DialogTitle>{anchor ? (anchor.before ? 'Insert Chapter Before' : 'Insert Chapter After') : 'New Chapter'}</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Chapter Title"
					fullWidth
					variant="outlined"
					value={title}
					onChange={(e) => setTitle(e.target.value)}
					onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
					sx={{ mt: 1 }}
				/>
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose}>Cancel</Button>
				<Button
					onClick={handleSubmit}
					variant="contained"
					disabled={!title.trim() || createChapter.isPending}
				>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	)
}