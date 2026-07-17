import { useEffect, useState } from 'react'
import {
	Alert,
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogContentText,
	DialogTitle,
	TextField,
	Typography,
} from '@mui/material'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'

/**
 * Stern, dedicated confirmation for deleting an entire project.
 *
 * Deleting a project removes every book, part, chapter, scene, codex entry,
 * AI review, and artifact underneath it in one action, so this intentionally
 * does not reuse the generic DeleteConfirmDialog: there is no "don't show
 * this again" shortcut here, and the Delete button stays disabled until the
 * user types the project's exact title. The project still lands in Trash
 * (restorable) rather than being purged immediately, and that is stated
 * plainly rather than implied.
 */
export default function DeleteProjectDialog({
	open,
	onClose,
	onConfirm,
	projectTitle = '',
	bookCount = 0,
	isPending = false,
}) {
	const [confirmText, setConfirmText] = useState('')

	// Reset the typed confirmation whenever the dialog is (re)opened so a
	// leftover value from a previous project can't accidentally match.
	useEffect(() => {
		if (open) setConfirmText('')
	}, [open])

	const trimmedTitle = (projectTitle ?? '').trim()
	const canConfirm = confirmText.trim().length > 0 && confirmText.trim() === trimmedTitle

	const handleConfirm = () => {
		if (!canConfirm || isPending) return
		onConfirm()
	}

	return (
		<Dialog
			open={open}
			onClose={!isPending ? onClose : undefined}
			maxWidth="xs"
			fullWidth
		>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<WarningAmberIcon color="error" fontSize="small" />
				Delete Project?
			</DialogTitle>

			<DialogContent>
				<Alert severity="error" sx={{ mb: 2 }}>
					This deletes the entire project — every book{bookCount > 0 ? ` (${bookCount})` : ''},
					part, chapter, and scene, along with its codex, AI reviews, and artifacts.
				</Alert>

				<DialogContentText sx={{ mb: 2 }}>
					The project moves to Trash and can be restored from there, but this is a
					large, all-at-once action. Please make sure this is what you want.
				</DialogContentText>

				<DialogContentText sx={{ mb: 1 }}>
					Type the project title <Typography component="span" fontWeight={700}>{trimmedTitle}</Typography> to confirm:
				</DialogContentText>

				<TextField
					autoFocus
					fullWidth
					size="small"
					value={confirmText}
					onChange={(e) => setConfirmText(e.target.value)}
					onKeyDown={(e) => e.key === 'Enter' && handleConfirm()}
					disabled={isPending}
					placeholder={trimmedTitle}
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
					disabled={!canConfirm || isPending}
				>
					Delete Project
				</Button>
			</DialogActions>
		</Dialog>
	)
}
