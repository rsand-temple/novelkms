import {
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
} from '@mui/material'
import SettingsIcon from '@mui/icons-material/Settings'
import AiCredentialsPanel from './AiCredentialsPanel'

/**
 * AiSettingsDialog
 *
 * Thin dialog wrapper around the shared AiCredentialsPanel. Kept for any caller
 * that opens AI settings directly (e.g. AiReviewDialog's onOpenSettings). The
 * global Settings dialog reuses the same panel as its AI tab.
 *
 * Props:
 *   open     {boolean}
 *   onClose  {() => void}
 */
export default function AiSettingsDialog({ open, onClose }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<SettingsIcon fontSize="small" />
				AI Settings
			</DialogTitle>

			<DialogContent>
				{/* Mount the panel fresh each time the dialog opens so its internal
				    list/form state always starts clean. */}
				{open && <AiCredentialsPanel />}
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
