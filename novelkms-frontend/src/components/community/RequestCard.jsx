import {
	Box,
	Button,
	Chip,
	Paper,
	Stack,
	Typography,
} from '@mui/material'
import { feedbackTypeLabel } from '../../utils/reviewFeedbackTypes'

// status -> chip. OPEN reads as active (green); terminal states are neutral, and
// an administrative REMOVED is called out in error color.
const STATUS_CHIP = {
	DRAFT:     { color: 'default', label: 'Draft' },
	OPEN:      { color: 'success', label: 'Open' },
	PAUSED:    { color: 'warning', label: 'Paused' },
	CLOSED:    { color: 'default', label: 'Closed' },
	WITHDRAWN: { color: 'default', label: 'Withdrawn' },
	REMOVED:   { color: 'error',   label: 'Removed' },
}

// Only the states the author can act on get a chip; CURRENT is the quiet default.
const SOURCE_CHIP = {
	CHANGED: { color: 'warning', label: 'Source edited since publish' },
	DELETED: { color: 'error',   label: 'Source chapter deleted' },
}

/**
 * One row of My Requests: a request summary with its status and source-state,
 * and the lifecycle actions that are legal from its current status. The action
 * set is kept in lockstep with the backend's legal transitions so the UI never
 * offers a move the server would reject with 409.
 */
export default function RequestCard({
	request,
	busy,
	onEdit,
	onViewSnapshot,
	onPause,
	onResume,
	onClose,
	onWithdraw,
}) {
	const status = request.status
	const statusChip = STATUS_CHIP[status] ?? { color: 'default', label: status }
	const sourceChip = SOURCE_CHIP[request.sourceState]

	const isTerminal = status === 'WITHDRAWN' || status === 'REMOVED'
	const canEdit = !isTerminal
	const canPause = status === 'OPEN'
	const canResume = status === 'PAUSED'
	const canCloseReq = status === 'OPEN' || status === 'PAUSED'
	const canWithdraw = status === 'OPEN' || status === 'PAUSED' || status === 'CLOSED'

	const sourceLine = [
		request.sourceTitle,
		request.bookTitle,
		`${(request.wordCount ?? 0).toLocaleString()} words`,
	].filter(Boolean).join(' · ')

	const footLine = [
		request.publishedAt ? `Published ${new Date(request.publishedAt).toLocaleDateString()}` : null,
		request.reviewCount > 0 ? `${request.reviewCount} review${request.reviewCount === 1 ? '' : 's'}` : null,
	].filter(Boolean).join(' · ')

	return (
		<Paper variant="outlined" sx={{ p: 2 }}>
			<Stack spacing={1.25}>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
					<Box sx={{ flexGrow: 1, minWidth: 0 }}>
						<Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap title={request.title}>
							{request.title}
						</Typography>
						<Typography variant="caption" color="text.secondary">
							{sourceLine}
						</Typography>
					</Box>
					<Stack
						direction="row"
						spacing={0.75}
						sx={{ flexWrap: 'wrap', justifyContent: 'flex-end', rowGap: 0.5 }}
					>
						<Chip size="small" color={statusChip.color} label={statusChip.label} />
						{sourceChip && (
							<Chip size="small" variant="outlined" color={sourceChip.color} label={sourceChip.label} />
						)}
					</Stack>
				</Stack>

				{request.description && (
					<Typography
						variant="body2"
						color="text.secondary"
						sx={{
							display: '-webkit-box',
							WebkitLineClamp: 2,
							WebkitBoxOrient: 'vertical',
							overflow: 'hidden',
						}}
					>
						{request.description}
					</Typography>
				)}

				{request.feedbackTypes?.length > 0 && (
					<Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
						{request.feedbackTypes.map((k) => (
							<Chip key={k} size="small" variant="outlined" label={feedbackTypeLabel(k)} />
						))}
					</Stack>
				)}

				<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center', rowGap: 0.5 }}>
					<Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
						{footLine}
					</Typography>
					<Button size="small" onClick={() => onViewSnapshot(request)}>View snapshot</Button>
					{canEdit && <Button size="small" onClick={() => onEdit(request)}>Edit</Button>}
					{canPause && <Button size="small" disabled={busy} onClick={() => onPause(request)}>Pause</Button>}
					{canResume && <Button size="small" disabled={busy} onClick={() => onResume(request)}>Resume</Button>}
					{canCloseReq && <Button size="small" disabled={busy} onClick={() => onClose(request)}>Close</Button>}
					{canWithdraw && (
						<Button size="small" color="error" disabled={busy} onClick={() => onWithdraw(request)}>
							Withdraw
						</Button>
					)}
				</Stack>
			</Stack>
		</Paper>
	)
}
