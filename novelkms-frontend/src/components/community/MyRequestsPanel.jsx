import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
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
import RequestCard from './RequestCard'
import ReviewRequestDialog from './ReviewRequestDialog'
import SnapshotDialog from './SnapshotDialog'
import {
	useMyReviewRequests,
	usePauseReviewRequest,
	useResumeReviewRequest,
	useCloseReviewRequest,
	useWithdrawReviewRequest,
} from '../../hooks/useReviewRequests'

/**
 * The My Requests tab: the author's own published packages, newest first, with
 * per-request edit, snapshot viewing, and lifecycle actions.
 *
 * Publishing itself does not live here — a request is cut from a chapter in the
 * manuscript workspace (right-click → Publish for Human Review), because the
 * summary rows this panel lists carry no source id to republish from. The empty
 * state points there.
 */
export default function MyRequestsPanel() {
	const { data: requests, isLoading, isError, error } = useMyReviewRequests()

	const pause = usePauseReviewRequest()
	const resume = useResumeReviewRequest()
	const closeReq = useCloseReviewRequest()
	const withdraw = useWithdrawReviewRequest()
	const busy = pause.isPending || resume.isPending || closeReq.isPending || withdraw.isPending

	const [editing, setEditing] = useState(null)         // request being edited | null
	const [snapshotFor, setSnapshotFor] = useState(null) // request whose snapshot is shown | null
	const [withdrawing, setWithdrawing] = useState(null) // request pending withdraw confirmation | null
	const [snack, setSnack] = useState(null)             // { severity, message } | null

	const runLifecycle = (mutation, request, verb) =>
		mutation.mutate(request.id, {
			onSuccess: () => setSnack({ severity: 'success', message: `Request ${verb}.` }),
			onError: (e) =>
				setSnack({
					severity: 'error',
					message: e?.response?.data?.message ?? `Could not ${verb === 'paused' ? 'pause' : verb === 'resumed' ? 'resume' : verb === 'closed' ? 'close' : 'withdraw'} the request.`,
				}),
		})

	const confirmWithdraw = () => {
		const request = withdrawing
		setWithdrawing(null)
		if (request) runLifecycle(withdraw, request, 'withdrawn')
	}

	if (isLoading) {
		return (
			<Stack sx={{ alignItems: 'center', pt: 6 }}>
				<CircularProgress />
			</Stack>
		)
	}

	if (isError) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Alert severity="error">
					{error?.response?.data?.message ?? 'Could not load your review requests.'}
				</Alert>
			</Box>
		)
	}

	const rows = requests ?? []

	return (
		<Box sx={{ maxWidth: 720, mx: 'auto' }}>
			{rows.length === 0 ? (
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
						No review requests yet
					</Typography>
					<Typography variant="body2" color="text.secondary">
						To publish a chapter for human review, open it in your manuscript, right-click it in the
						nav tree, and choose “Publish for Human Review.”
					</Typography>
				</Paper>
			) : (
				<Stack spacing={1.5}>
					{rows.map((request) => (
						<RequestCard
							key={request.id}
							request={request}
							busy={busy}
							onEdit={setEditing}
							onViewSnapshot={setSnapshotFor}
							onPause={(r) => runLifecycle(pause, r, 'paused')}
							onResume={(r) => runLifecycle(resume, r, 'resumed')}
							onClose={(r) => runLifecycle(closeReq, r, 'closed')}
							onWithdraw={setWithdrawing}
						/>
					))}
				</Stack>
			)}

			<ReviewRequestDialog
				open={!!editing}
				mode="edit"
				requestId={editing?.id}
				onClose={() => setEditing(null)}
				onSaved={() => setSnack({ severity: 'success', message: 'Request updated.' })}
			/>

			<SnapshotDialog
				open={!!snapshotFor}
				requestId={snapshotFor?.id}
				requestTitle={snapshotFor?.title}
				onClose={() => setSnapshotFor(null)}
			/>

			{/* Withdraw is the one destructive lifecycle move: it pulls the package from
			    every reviewer view, yet submitted reviews are retained for dispute
			    handling, so it gets an explicit confirmation. */}
			<Dialog open={!!withdrawing} onClose={() => setWithdrawing(null)} maxWidth="xs" fullWidth>
				<DialogTitle>Withdraw request</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Withdraw {withdrawing?.title ? `“${withdrawing.title}”` : 'this request'}? It leaves the
						review queue and every reviewer’s view. Any reviews already submitted are kept.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setWithdrawing(null)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={confirmWithdraw} disabled={busy}>
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
