import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button,
} from '@mui/material'
import { useCreatePartChapter } from '../../../hooks/useParts'

export default function AddPartChapterDialog({ open, onClose, partId }) {
	const [title, setTitle] = useState('')
	const { mutate: createChapter, isPending } = useCreatePartChapter()

	const handleCreate = () => {
		if (!title.trim()) return
		createChapter(
			{ partId, data: { title: title.trim() } },
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
		<Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
			<DialogTitle>New Chapter</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Chapter Title"
					size="small"
					fullWidth
					value={title}
					onChange={(e) => setTitle(e.target.value)}
					onKeyDown={(e) => { if (e.key === 'Enter') handleCreate() }}
					sx={{ mt: 1 }}
				/>
			</DialogContent>
			<DialogActions>
				<Button size="small" onClick={handleClose}>Cancel</Button>
				<Button
					size="small"
					variant="contained"
					onClick={handleCreate}
					disabled={isPending || !title.trim()}
				>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	)
}
