import { useMemo, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	CircularProgress,
	Divider,
	IconButton,
	MenuItem,
	TextField,
	Tooltip,
	Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {
	useChapterReviews,
	useAiReview,
	useRunChapterReview,
	useSetRecommendationStatus,
	usePromoteRecommendation,
	useDeleteReview,
} from '../../hooks/useAiReviews'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import RecommendationList from './RecommendationList'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'The review could not be run.'
}

function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

/**
 * ChapterReviewPanel — the AI review surface inside the context (Properties)
 * panel. It is bound to the selected chapter, so there is exactly one per
 * chapter. Run a review, browse this chapter's past reviews, accept/reject
 * recommendations, and one-click promote them into the Codex.
 *
 * Props:
 *   chapterId {string}
 */
export default function ChapterReviewPanel({ chapterId }) {
	const [explicitReviewId, setExplicitReviewId] = useState(null)
	const [credentialId, setCredentialId] = useState(null)
	const [runError, setRunError] = useState(null)
	const [promotingId, setPromotingId] = useState(null)

	const { data: credentials = [] } = useAiCredentials()
	const { data: reviews = [], isLoading: loadingReviews } = useChapterReviews(chapterId)
	const selectedReviewId = explicitReviewId ?? reviews[0]?.id ?? null
	const { data: detail, isLoading: loadingDetail } = useAiReview(selectedReviewId, !!selectedReviewId)
	const { mutate: runReview, isPending: running } = useRunChapterReview()
	const { mutate: setRecStatus } = useSetRecommendationStatus()
	const { mutate: promote } = usePromoteRecommendation()
	const { mutate: deleteReview, isPending: deleting } = useDeleteReview()

	const defaultCredId = useMemo(
		() => credentials.find(c => c.defaultCredential)?.id ?? credentials[0]?.id ?? null,
		[credentials],
	)
	const effectiveCredId = credentialId ?? defaultCredId
	const hasCredentials = credentials.length > 0

	const handleRun = () => {
		setRunError(null)
		runReview(
			{ chapterId, credentialId: effectiveCredId, model: null },
			{
				onSuccess: (review) => setExplicitReviewId(review.id),
				onError: (e) => setRunError(errMessage(e)),
			},
		)
	}

	const handleSetStatus = (rec, value) => {
		if (!detail) return
		setRecStatus({ reviewId: detail.id, recId: rec.id, status: value ?? 'OPEN', chapterId })
	}

	const handlePromote = (rec, codexCategory, codexTitle) => {
		if (!detail) return
		setPromotingId(rec.id)
		promote(
			{ reviewId: detail.id, recId: rec.id, codexCategory, codexTitle, chapterId },
			{ onSettled: () => setPromotingId(null), onError: (e) => setRunError(errMessage(e)) },
		)
	}

	return (
		<Box sx={{ mt: 1 }}>
			<Typography variant="subtitle2" sx={{ mb: 1 }}>AI Review</Typography>

			{!hasCredentials ? (
				<Alert severity="info" sx={{ mb: 1 }}>
					Add an AI key from the AI menu → AI&nbsp;Settings to run chapter reviews.
				</Alert>
			) : (
				<>
					{credentials.length > 1 && (
						<TextField
							select label="Key" size="small" fullWidth sx={{ mb: 1 }}
							value={effectiveCredId ?? ''}
							onChange={(e) => setCredentialId(e.target.value)}
						>
							{credentials.map(c => (
								<MenuItem key={c.id} value={c.id}>
									{c.label}{c.defaultCredential ? ' (default)' : ''}
								</MenuItem>
							))}
						</TextField>
					)}

					<Button
						variant="contained" size="small" fullWidth
						onClick={handleRun} disabled={running}
						startIcon={running ? <CircularProgress size={16} color="inherit" /> : null}
					>
						{running ? 'Reviewing…' : 'Run Review'}
					</Button>

					{runError && <Alert severity="error" sx={{ mt: 1 }}>{runError}</Alert>}

					{/* Past-review selector (only when more than one exists) */}
					{reviews.length > 1 && (
						<TextField
							select label="Review" size="small" fullWidth sx={{ mt: 1.5 }}
							value={selectedReviewId ?? ''}
							onChange={(e) => setExplicitReviewId(e.target.value)}
						>
							{reviews.map(r => (
								<MenuItem key={r.id} value={r.id}>
									{formatTime(r.submittedAt)} · {r.status}
								</MenuItem>
							))}
						</TextField>
					)}

					{/* Delete selected review to trash */}
					{selectedReviewId && (
						<Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.75 }}>
							<Tooltip title="Move this review to trash">
								<span>
									<IconButton
										size="small"
										disabled={deleting}
										onClick={() => {
											setRunError(null)
											deleteReview(selectedReviewId, {
												onSuccess: () => setExplicitReviewId(null),
												onError: (e) => setRunError(errMessage(e)),
											})
										}}
									>
										<DeleteIcon fontSize="small" />
									</IconButton>
								</span>
							</Tooltip>
						</Box>
					)}

					<Divider sx={{ my: 1.5 }} />

					{loadingReviews && reviews.length === 0 ? (
						<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
					) : !selectedReviewId ? (
						<Typography variant="body2" color="text.secondary">
							No reviews yet. Run one to see recommendations.
						</Typography>
					) : loadingDetail || !detail ? (
						<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
					) : (
						<RecommendationList
							review={detail}
							onSetStatus={handleSetStatus}
							onPromote={handlePromote}
							promotingId={promotingId}
						/>
					)}
				</>
			)}
		</Box>
	)
}
