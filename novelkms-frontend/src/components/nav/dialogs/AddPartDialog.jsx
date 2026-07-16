import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button,
} from '@mui/material'
import { useCreatePart } from '../../../hooks/useParts'

/**
 * `anchor` — { id, before } | null.
 *
 * null appends. Otherwise the new part (the anchor may be a part OR a direct-book chapter) is inserted immediately before/after the
 * anchor item, which the backend does by opening a slot at that position rather
 * than at the end of the sequence.
 */
export default function AddPartDialog({ open, onClose, bookId, anchor = null }) {
	const [title, setTitle] = useState('')
	const { mutate: createPart, isPending } = useCreatePart()

	const handleCreate = () => {
		if (!title.trim()) return
		createPart(
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
		<Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
			<DialogTitle>{anchor ? (anchor.before ? 'Insert Part Before' : 'Insert Part After') : 'New Part'}</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Part Title"
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
