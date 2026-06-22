import { useEffect, useMemo, useState } from 'react'
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
	Divider,
	MenuItem,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Typography,
} from '@mui/material'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import {
	useChapterReviews,
	useAiReview,
	useRunChapterReview,
	useSetRecommendationStatus,
} from '../../hooks/useAiReviews'
import { useAiCredentials } from '../../hooks/useAiCredentials'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'The review could not be run.'
}

function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

function severityColor(severity) {
	switch ((severity ?? '').toUpperCase()) {
		case 'HIGH':   return 'error'
		case 'MEDIUM': return 'warning'
		case 'LOW':    return 'info'
		default:       return 'default'
	}
}

function StatusIcon({ status }) {
	if (status === 'COMPLETED') return <CheckCircleOutlinedIcon fontSize="small" color="success" />
	if (status === 'FAILED')    return <ErrorOutlineIcon fontSize="small" color="error" />
	return <HourglassEmptyIcon fontSize="small" color="disabled" />
}

/**
 * AiReviewDialog
 *
 * Props:
 *   open           {boolean}
 *   onClose        {() => void}
 *   chapterId      {string|null}
 *   chapterLabel   {string}    — display name for the title bar
 *   onOpenSettings {() => void} — routes to AI Settings when no key is configured
 */
export default function AiReviewDialog({ open, onClose, chapterId, chapterLabel, onOpenSettings }) {
	const [selectedReviewId, setSelectedReviewId] = useState(null)
	const [credentialId, setCredentialId] = useState(null)
	const [model, setModel] = useState('')
	const [runError, setRunError] = useState(null)

	const { data: credentials = [] } = useAiCredentials()
	const { data: reviews = [], isLoading: loadingReviews } = useChapterReviews(chapterId, open)
	const { data: detail, isLoading: loadingDetail } = useAiReview(selectedReviewId, open && !!selectedReviewId)
	const { mutate: runReview, isPending: running } = useRunChapterReview()
	const { mutate: setRecStatus } = useSetRecommendationStatus()

	const defaultCredId = useMemo(
		() => credentials.find(c => c.defaultCredential)?.id ?? credentials[0]?.id ?? null,
		[credentials],
	)
	const effectiveCredId = credentialId ?? defaultCredId

	// Reset transient state whenever the dialog opens or the chapter changes.
	useEffect(() => {
		if (open) {
			setSelectedReviewId(null)
			setRunError(null)
			setModel('')
			setCredentialId(null)
		}
	}, [open, chapterId])

	const hasCredentials = credentials.length > 0

	const handleClose = () => {
		if (running) return
		onClose()
	}

	const handleRun = () => {
		setRunError(null)
		runReview(
			{ chapterId, credentialId: effectiveCredId, model: model.trim() || null },
			{
				onSuccess: (review) => setSelectedReviewId(review.id),
				onError: (e) => setRunError(errMessage(e)),
			},
		)
	}

	const handleSetStatus = (rec, value) => {
		if (!detail) return
		setRecStatus({ reviewId: detail.id, recId: rec.id, status: value ?? 'OPEN', chapterId })
	}

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<AutoAwesomeOutlinedIcon fontSize="small" />
				AI Review
				{chapterLabel && (
					<Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 0.5 }}>
						— {chapterLabel}
					</Typography>
				)}
			</DialogTitle>

			<DialogContent>
				{!chapterId ? (
					<Alert severity="info">Select a chapter to review.</Alert>
				) : !hasCredentials ? (
					<Box>
						<Alert severity="info" sx={{ mb: 2 }}>
							No AI key is configured yet. Add one to run chapter reviews.
						</Alert>
						<Button variant="contained" onClick={onOpenSettings}>Open AI Settings</Button>
					</Box>
				) : (
					<>
						{/* ── Run control ──────────────────────────────────────── */}
						<Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, flexWrap: 'wrap', mb: 1 }}>
							{credentials.length > 1 && (
								<TextField
									select label="Key" size="small"
									value={effectiveCredId ?? ''}
									onChange={(e) => setCredentialId(e.target.value)}
									sx={{ minWidth: 160 }}
								>
									{credentials.map(c => (
										<MenuItem key={c.id} value={c.id}>
											{c.label}{c.defaultCredential ? ' (default)' : ''}
										</MenuItem>
									))}
								</TextField>
							)}
							<TextField
								label="Model (optional)" size="small"
								value={model}
								onChange={(e) => setModel(e.target.value)}
								placeholder="gpt-5.4"
								sx={{ minWidth: 160 }}
							/>
							<Button
								variant="contained"
								startIcon={running ? <CircularProgress size={16} color="inherit" /> : <AutoAwesomeOutlinedIcon />}
								onClick={handleRun}
								disabled={running}
								sx={{ mt: 0.25 }}
							>
								{running ? 'Reviewing…' : 'Run Review'}
							</Button>
						</Box>

						{runError && <Alert severity="error" sx={{ my: 1 }}>{runError}</Alert>}

						<Divider sx={{ my: 1.5 }} />

						<Box sx={{ display: 'flex', gap: 2, minHeight: 360 }}>
							{/* ── History list ──────────────────────────────────── */}
							<Box sx={{ width: 200, flexShrink: 0, borderRight: '1px solid', borderColor: 'divider', pr: 1, overflowY: 'auto', maxHeight: 460 }}>
								<Typography variant="caption" color="text.secondary" sx={{ px: 0.5 }}>
									Past reviews
								</Typography>
								{loadingReviews ? (
									<Box sx={{ p: 1 }}><CircularProgress size={18} /></Box>
								) : reviews.length === 0 ? (
									<Typography variant="caption" color="text.secondary" sx={{ display: 'block', p: 1 }}>
										None yet.
									</Typography>
								) : (
									reviews.map(r => (
										<Box
											key={r.id}
											onClick={() => setSelectedReviewId(r.id)}
											sx={{
												display: 'flex', alignItems: 'center', gap: 1, p: 1, borderRadius: 1, cursor: 'pointer',
												bgcolor: selectedReviewId === r.id ? 'action.selected' : 'transparent',
												'&:hover': { bgcolor: 'action.hover' },
											}}
										>
											<StatusIcon status={r.status} />
											<Box sx={{ minWidth: 0 }}>
												<Typography variant="body2" noWrap>{r.model}</Typography>
												<Typography variant="caption" color="text.secondary" noWrap>
													{formatTime(r.submittedAt)}
												</Typography>
											</Box>
										</Box>
									))
								)}
							</Box>

							{/* ── Detail ────────────────────────────────────────── */}
							<Box sx={{ flexGrow: 1, minWidth: 0, overflowY: 'auto', maxHeight: 460 }}>
								{!selectedReviewId ? (
									<Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>
										Run a new review, or select one from the list to see its recommendations.
									</Typography>
								) : loadingDetail || !detail ? (
									<Box sx={{ p: 1 }}><CircularProgress size={20} /></Box>
								) : detail.status === 'FAILED' ? (
									<Alert severity="error">{detail.errorMessage ?? 'The review failed.'}</Alert>
								) : detail.status !== 'COMPLETED' ? (
									<Typography variant="body2" color="text.secondary" sx={{ p: 1 }}>Review in progress…</Typography>
								) : (detail.recommendations?.length ?? 0) === 0 ? (
									<Alert severity="success">No notes — the model had no substantive recommendations on this chapter.</Alert>
								) : (
									detail.recommendations.map(rec => {
										const value = rec.status === 'ACCEPTED' ? 'ACCEPTED' : rec.status === 'REJECTED' ? 'REJECTED' : null
										return (
											<Box key={rec.id} sx={{ mb: 1.5, pb: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
												<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
													<Typography variant="caption" color="text.secondary">#{rec.seq}</Typography>
													{rec.category && <Chip label={rec.category} size="small" variant="outlined" />}
													{rec.severity && <Chip label={rec.severity} size="small" color={severityColor(rec.severity)} />}
												</Box>
												<Typography variant="body2" sx={{ mb: rec.location ? 0.25 : 1 }}>
													{rec.recommendation}
												</Typography>
												{rec.location && (
													<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1, fontStyle: 'italic' }}>
														{rec.location}
													</Typography>
												)}
												<ToggleButtonGroup
													exclusive size="small" value={value}
													onChange={(_e, val) => handleSetStatus(rec, val)}
												>
													<ToggleButton value="ACCEPTED" color="success">
														<CheckIcon fontSize="small" sx={{ mr: 0.5 }} /> Accept
													</ToggleButton>
													<ToggleButton value="REJECTED" color="error">
														<CloseIcon fontSize="small" sx={{ mr: 0.5 }} /> Reject
													</ToggleButton>
												</ToggleButtonGroup>
											</Box>
										)
									})
								)}
							</Box>
						</Box>
					</>
				)}
			</DialogContent>

			<DialogActions>
				<Button onClick={handleClose} disabled={running}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
