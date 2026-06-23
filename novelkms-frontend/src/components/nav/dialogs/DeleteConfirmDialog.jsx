import { useState } from 'react'
import {
	Dialog, DialogTitle, DialogContent, DialogActions,
	Button, FormControlLabel, Checkbox,
} from '@mui/material'
import { saveSkipDeleteConfirm } from '../../../utils/deleteConfirmPrefs'

export default function DeleteConfirmDialog({
	open,
	onClose,
	onConfirm,
	itemType = 'item',
	isPending,
}) {
	const [dontShowAgain, setDontShowAgain] = useState(false)

	const handleConfirm = () => {
		saveSkipDeleteConfirm(dontShowAgain)
		onConfirm()
	}

	return (
		<Dialog open={open} onClose={!isPending ? onClose : undefined} maxWidth="xs" fullWidth>
			<DialogTitle>{`Delete ${itemType}?`}</DialogTitle>

			<DialogContent sx={{ pt: 0 }}>
				<FormControlLabel
					control={
						<Checkbox
							checked={dontShowAgain}
							onChange={(e) => setDontShowAgain(e.target.checked)}
							disabled={isPending}
						/>
					}
					label="Don’t show this again"
				/>
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose} disabled={isPending}>
					Cancel
				</Button>
				<Button
					onClick={handleConfirm}
					color="error"
					variant="contained"
					disabled={isPending}
					autoFocus
				>
					Delete
				</Button>
			</DialogActions>
		</Dialog>
	)
}
