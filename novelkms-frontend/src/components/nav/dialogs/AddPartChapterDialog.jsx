import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button,
} from '@mui/material'
import { useCreatePartChapter } from '../../../hooks/useParts'

/**
 * `anchor` — { id, before } | null.
 *
 * null appends. Otherwise the new chapter (the anchor is a sibling chapter in the same part) is inserted immediately before/after the
 * anchor item, which the backend does by opening a slot at that position rather
 * than at the end of the sequence.
 */
export default function AddPartChapterDialog({ open, onClose, partId, anchor = null }) {
	const [title, setTitle] = useState('')
	const { mutate: createChapter, isPending } = useCreatePartChapter()

	const handleCreate = () => {
		if (!title.trim()) return
		createChapter(
			{
				partId,
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
		<Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
			<DialogTitle>{anchor ? (anchor.before ? 'Insert Chapter Before' : 'Insert Chapter After') : 'New Chapter'}</DialogTitle>
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
