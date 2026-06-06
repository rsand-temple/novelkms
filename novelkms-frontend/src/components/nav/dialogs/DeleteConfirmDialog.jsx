import {
	Dialog, DialogTitle, DialogContent, DialogContentText,
	DialogActions, Button,
} from '@mui/material'

/**
 * DeleteConfirmDialog
 *
 * Generic confirmation dialog for destructive actions.
 *
 * Props:
 *   open       — controls visibility
 *   onClose    — called on Cancel or backdrop click
 *   onConfirm  — called when the user clicks Delete
 *   title      — dialog heading (e.g. "Delete Chapter")
 *   message    — body text describing what will be destroyed
 *   isPending  — disables both buttons while the mutation is in-flight
 */
export default function DeleteConfirmDialog({ open, onClose, onConfirm, title, message, isPending }) {
	return (
		<Dialog open={open} onClose={!isPending ? onClose : undefined} maxWidth="xs" fullWidth>
			<DialogTitle>{title}</DialogTitle>
			<DialogContent>
				<DialogContentText>{message}</DialogContentText>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={isPending}>
					Cancel
				</Button>
				<Button
					onClick={onConfirm}
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
