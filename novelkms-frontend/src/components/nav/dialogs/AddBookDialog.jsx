import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateBook } from '../../../hooks/useBooks'

export default function AddBookDialog({ open, onClose, projectId }) {
	const [title, setTitle] = useState('')
	const createBook = useCreateBook()

	const handleSubmit = () => {
		if (!title.trim()) return
		createBook.mutate(
			{ projectId, data: { title: title.trim() } },
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
			<DialogTitle>New Book</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Book Title"
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
					disabled={!title.trim() || createBook.isPending}
				>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	)
}