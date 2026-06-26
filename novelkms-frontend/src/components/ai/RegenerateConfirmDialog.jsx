import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'

/**
 * RegenerateConfirmDialog — warns that regenerating a memory document or
 * summary discards whatever is currently there, including any hand-applied
 * formatting (these are rich-text documents, not plain paragraphs). Shown
 * only when content already exists; a first-ever Generate skips this gate
 * entirely (there is nothing to lose yet).
 *
 * Props:
 *   open      {boolean}
 *   onCancel  {() => void}
 *   onConfirm {() => void}
 */
export default function RegenerateConfirmDialog({ open, onCancel, onConfirm }) {
	return (
		<Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
			<DialogTitle>Regenerate this document?</DialogTitle>
			<DialogContent>
				<Typography variant="body2">
					Regenerating will discard all current content and formatting in this
					document. This can’t be undone.
				</Typography>
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel}>Cancel</Button>
				<Button color="warning" variant="contained" onClick={onConfirm}>
					Regenerate
				</Button>
			</DialogActions>
		</Dialog>
	)
}
