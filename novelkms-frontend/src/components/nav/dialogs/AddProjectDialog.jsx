import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateProject } from '../../../hooks/useProjects'

export default function AddProjectDialog({ open, onClose }) {
	const [title, setTitle] = useState('')
	const createProject = useCreateProject()

	const handleSubmit = () => {
		if (!title.trim()) return
		createProject.mutate(
			{ title: title.trim() },
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
		<Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
			<DialogTitle>New Project</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					label="Project Title"
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
					disabled={!title.trim() || createProject.isPending}
				>
					Create
				</Button>
			</DialogActions>
		</Dialog>
	)
}