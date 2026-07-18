import {
	Dialog,
	DialogActions,
	DialogContent,
	DialogContentText,
	DialogTitle,
	TextField,
	Button,
} from '@mui/material'
import { isDefaultSceneTitle } from '../../../utils/sceneTitles'

export default function SplitSceneDialog({
	open,
	title,
	onTitleChange,
	onConfirm,
	onClose,
	isPending = false,
}) {
	const handleClose = () => {
		if (!isPending) onClose?.()
	}

	const handleSubmit = () => {
		if (!isPending && title.trim()) onConfirm?.()
	}

	return (
		<Dialog
			open={open}
			onClose={handleClose}
			disableEscapeKeyDown={isPending}
			maxWidth="sm"
			fullWidth
			disableRestoreFocus
		>
			<DialogTitle>Split Scene</DialogTitle>
			<DialogContent>
				<DialogContentText sx={{ mb: 2 }}>
					Do you want to split the scene here?
				</DialogContentText>
				<TextField
					autoFocus
					label="New Title"
					fullWidth
					variant="outlined"
					value={title}
					onChange={(event) => onTitleChange?.(event.target.value)}
					onFocus={(event) => {
						if (isDefaultSceneTitle(title)) event.target.select()
					}}
					onKeyDown={(event) => {
						if (event.key === 'Enter') {
							event.preventDefault()
							handleSubmit()
						}
					}}
				/>
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose} disabled={isPending}>
					Cancel
				</Button>
				<Button
					onClick={handleSubmit}
					variant="contained"
					disabled={!title.trim() || isPending}
				>
					OK
				</Button>
			</DialogActions>
		</Dialog>
	)
}
