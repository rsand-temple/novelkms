import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateScene } from '../../../hooks/useScenes'

export default function AddSceneDialog({ open, onClose, chapterId }) {
	const [title, setTitle] = useState('')
	const createScene = useCreateScene()

	const handleSubmit = () => {
		if (!title.trim()) return
		createScene.mutate(
			{ chapterId, data: { title: title.trim() } },
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
			<DialogTitle>New Scene</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Scene Title"
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
					disabled={!title.trim() || createScene.isPending}
				>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	)
}