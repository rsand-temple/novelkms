import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Paper,
	Snackbar,
	Stack,
	Typography,
} from '@mui/material'
import ReviewPackageDialog from './ReviewPackageDialog'
import ReviewCardMenu from './ReviewCardMenu'
import { useReviewsWriting, useWithdrawReview } from '../../hooks/useHumanReviews'

const REVIEW_STATUS_CHIP = {
	DRAFT:     { color: 'default', label: 'Draft' },
	SUBMITTED: { color: 'success', label: 'Submitted' },
}

// The package's own lifecycle, shown only when it has moved off OPEN so the reviewer
// understands why Continue is unavailable.
const REQUEST_STATE_NOTE = {
	PAUSED:    'The author paused this request.',
	CLOSED:    'The author closed this request.',
	WITHDRAWN: 'The author withdrew this request.',
	REMOVED:   'This request was removed.',
}

function WritingCard({ row, busy, onContinue, onWithdraw }) {
	const statusChip = REVIEW_STATUS_CHIP[row.status] ?? { color: 'default', label: row.status }
	const requestOpen = row.requestStatus === 'OPEN'
	const stateNote = requestOpen ? null : REQUEST_STATE_NOTE[row.requestStatus]

	const sourceLine = [
		row.sourceTitle,
		row.bookTitle,
		`${(row.snapshotWordCount ?? 0).toLocaleString()} words`,
	].filter(Boolean).join(' · ')

	return (
		<Paper variant="outlined" sx={{ p: 2 }}>
			<Stack spacing={1.25}>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
					<Box sx={{ flexGrow: 1, minWidth: 0 }}>
						<Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap title={row.requestTitle}>
							{row.requestTitle}
						</Typography>
						<Typography variant="caption" color="text.secondary">
							by @{row.authorHandle} · {sourceLine}
						</Typography>
					</Box>
					<Chip size="small" color={statusChip.color} label={statusChip.label} />
					<ReviewCardMenu
						handle={row.authorHandle}
						contentTarget={{ type: 'REQUEST', id: row.requestId, label: 'this request' }}
					/>
				</Stack>

				{stateNote && (
					<Typography variant="caption" color="text.secondary">{stateNote}</Typography>
				)}

				<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center', rowGap: 0.5 }}>
					<Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
						{row.status === 'SUBMITTED' && row.submittedAt
							? `Submitted ${new Date(row.submittedAt).toLocaleDateString()}`
							: row.updatedAt ? `Edited ${new Date(row.updatedAt).toLocaleDateString()}` : ''}
						{row.wordCount > 0 ? ` · ${row.wordCount} review words` : ''}
					</Typography>
					<Button size="small" variant="contained" disabled={!requestOpen} onClick={() => onContinue(row)}>
						{row.status === 'SUBMITTED' ? 'Open' : 'Continue'}
					</Button>
					<Button size="small" color="error" disabled={busy} onClick={() => onWithdraw(row)}>Withdraw</Button>
				</Stack>
			</Stack>
		</Paper>
	)
}

/**
 * The "Reviews I'm Writing" tab: the reviewer's own active reviews — drafts they can
 * continue and submissions they can withdraw — newest activity first.
 *
 * Continue opens the same package dialog the queue uses. It is available only while
 * the source request is still OPEN, because the package read is gated to open,
 * public requests; a reviewer whose request has since been paused or closed can still
 * withdraw their review here, which the server allows in any request state.
 */
export default function MyWritingPanel() {
	const { data: rows, isLoading, isError, error } = useReviewsWriting()
	const withdraw = useWithdrawReview()

	const [openId, setOpenId] = useState(null)
	const [withdrawing, setWithdrawing] = useState(null)
	const [snack, setSnack] = useState(null)

	const needsProfile = isError && error?.response?.status === 409

	const confirmWithdraw = () => {
		const row = withdrawing
		setWithdrawing(null)
		if (!row) return
		withdraw.mutate(
			{ requestId: row.requestId },
			{
				onSuccess: () => setSnack({ severity: 'success', message: 'Review withdrawn.' }),
				onError: (e) => setSnack({ severity: 'error', message: e?.response?.data?.message ?? 'Could not withdraw the review.' }),
			},
		)
	}

	if (isLoading) {
		return <Stack sx={{ alignItems: 'center', pt: 6 }}><CircularProgress /></Stack>
	}

	if (needsProfile) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Claim a handle to review</Typography>
					<Typography variant="body2" color="text.secondary">
						Your handle is the gate for taking part in the review community.
					</Typography>
				</Paper>
			</Box>
		)
	}

	if (isError) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Alert severity="error">
					{error?.response?.data?.message ?? 'Could not load your reviews.'}
				</Alert>
			</Box>
		)
	}

	const list = rows ?? []

	return (
		<Box sx={{ maxWidth: 720, mx: 'auto' }}>
			{list.length === 0 ? (
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>You haven't started any reviews</Typography>
					<Typography variant="body2" color="text.secondary">
						Find a chapter to review in the Review Queue. Drafts you begin will show up here.
					</Typography>
				</Paper>
			) : (
				<Stack spacing={1.5}>
					{list.map(row => (
						<WritingCard
							key={row.reviewId}
							row={row}
							busy={withdraw.isPending}
							onContinue={(r) => setOpenId(r.requestId)}
							onWithdraw={setWithdrawing}
						/>
					))}
				</Stack>
			)}

			<ReviewPackageDialog open={!!openId} requestId={openId} onClose={() => setOpenId(null)} />

			<Dialog open={!!withdrawing} onClose={() => setWithdrawing(null)} maxWidth="xs" fullWidth>
				<DialogTitle>Withdraw review</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Withdraw your review of {withdrawing?.requestTitle ? `“${withdrawing.requestTitle}”` : 'this package'}?
						It is removed from the author's view. You can rewrite it later if you change your mind.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setWithdrawing(null)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={confirmWithdraw} disabled={withdraw.isPending}>
						Withdraw
					</Button>
				</DialogActions>
			</Dialog>

			<Snackbar
				open={!!snack}
				autoHideDuration={4000}
				onClose={() => setSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{snack ? (
					<Alert severity={snack.severity} onClose={() => setSnack(null)} sx={{ width: '100%' }}>
						{snack.message}
					</Alert>
				) : undefined}
			</Snackbar>
		</Box>
	)
}
