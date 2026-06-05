import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateChapter } from '../../../hooks/useChapters'

export default function AddChapterDialog({ open, onClose, bookId }) {
	const [title, setTitle] = useState('')
	const createChapter = useCreateChapter()

	const handleSubmit = () => {
		if (!title.trim()) return
		createChapter.mutate(
			{ bookId, data: { title: title.trim() } },
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
			<DialogTitle>New Chapter</DialogTitle>
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