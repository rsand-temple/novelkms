import { useState } from 'react'
import {
	Alert,
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	MenuItem,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import { REPORT_REASONS } from '../../utils/reviewReportReasons'
import { useReportContent } from '../../hooks/useReviewSafety'

const MAX_DETAIL = 2000

/**
 * The shared content-report dialog (slice 1F). It reports exactly one target,
 * described by the `target` prop:
 *
 *   { type: 'REQUEST' | 'REVIEW' | 'PROFILE', id?, handle?, label? }
 *
 * A PROFILE target is reported by handle (the backend resolves it to a profile id);
 * a REQUEST or REVIEW target is reported by id. `label` is a short human phrase for
 * the heading ("this request", "@handle's profile") and is presentation only.
 *
 * Mount this fresh per open (conditional render, not a persistent `open` toggle) so
 * its useState initializers reseed each time — the house rule for clean form state.
 * Reporting is file-and-forget: on success the dialog shows a brief confirmation and
 * closes; the reporter never sees a queue of their own reports in Phase 1.
 */
export default function ReportDialog({ open, onClose, target }) {
	const [reason, setReason] = useState('')
	const [detail, setDetail] = useState('')
	const report = useReportContent()

	const label = target?.label || 'this'
	const tooLong = detail.length > MAX_DETAIL
	const canSubmit = !report.isPending && !!reason && !tooLong

	const errorMessage = report.isError
		? (report.error?.response?.data?.message ?? 'Could not file your report.')
		: null

	const submit = () => {
		if (!target) return
		const body = {
			targetType: target.type,
			reason,
			detail: detail.trim() || null,
		}
		if (target.type === 'PROFILE') body.targetHandle = target.handle
		else body.targetId = target.id

		report.mutate(body, { onSuccess: () => onClose({ reported: true }) })
	}

	return (
		<Dialog open={open} onClose={() => onClose()} maxWidth="xs" fullWidth>
			<DialogTitle>Report {label}</DialogTitle>
			<DialogContent dividers>
				<Stack spacing={2}>
					<Typography variant="body2" color="text.secondary">
						Reports go to the NovelKMS moderators. Please tell us what's wrong so we can
						look into it. This does not notify the other writer.
					</Typography>

					{errorMessage && <Alert severity="error">{errorMessage}</Alert>}

					<TextField
						label="Reason"
						value={reason}
						onChange={(e) => setReason(e.target.value)}
						select
						fullWidth
						required
						size="small"
					>
						{REPORT_REASONS.map(r => (
							<MenuItem key={r.key} value={r.key}>{r.label}</MenuItem>
						))}
					</TextField>

					<TextField
						label="Details (optional)"
						value={detail}
						onChange={(e) => setDetail(e.target.value)}
						fullWidth
						multiline
						minRows={3}
						error={tooLong}
						helperText={`${detail.length} / ${MAX_DETAIL}`}
						placeholder="Anything that helps a moderator understand the problem."
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={() => onClose()}>Cancel</Button>
				<Button
					variant="contained"
					color="error"
					onClick={submit}
					disabled={!canSubmit}
					startIcon={report.isPending ? <CircularProgress size={16} color="inherit" /> : null}
				>
					Submit report
				</Button>
			</DialogActions>
		</Dialog>
	)
}
