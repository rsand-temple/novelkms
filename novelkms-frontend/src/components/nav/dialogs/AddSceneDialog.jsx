import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	TextField, Button
} from '@mui/material'
import { useCreateScene } from '../../../hooks/useScenes'

function generateDefaultSceneTitle() {
	const suffix = globalThis.crypto?.randomUUID
		? globalThis.crypto.randomUUID().substring(0, 4)
		: Math.floor(Math.random() * 0x10000)
			.toString(16)
			.padStart(4, '0')

	return `New Scene [${suffix}]`
}

function isDefaultSceneTitle(title) {
	return /^New Scene \[[0-9a-f]{4}\]$/i.test(title)
}

export default function AddSceneDialog({ open, onClose, chapterId }) {
	const [title, setTitle] = useState(generateDefaultSceneTitle)
	const createScene = useCreateScene()

	const resetTitle = () => {
		setTitle(generateDefaultSceneTitle())
	}

	const handleSubmit = () => {
		if (!title.trim()) return

		createScene.mutate(
			{ chapterId, data: { title: title.trim() } },
			{
				onSuccess: () => {
					resetTitle()
					onClose()
				},
			}
		)
	}

	const handleClose = () => {
		resetTitle()
		onClose()
	}

	const handleTitleFocus = (event) => {
		// Make replacing the generated title effortless while preserving
		// normal cursor behavior after the user begins editing it.
		if (isDefaultSceneTitle(title)) {
			event.target.select()
		}
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
					onChange={(event) => setTitle(event.target.value)}
					onFocus={handleTitleFocus}
					onKeyDown={(event) => event.key === 'Enter' && handleSubmit()}
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