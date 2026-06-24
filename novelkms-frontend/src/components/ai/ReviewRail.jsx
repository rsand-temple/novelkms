import { useCallback, useMemo, useState } from 'react'
import {
	Alert,
	Badge,
	Box,
	Button,
	Chip,
	CircularProgress,
	Divider,
	IconButton,
	MenuItem,
	TextField,
	Tooltip,
	Typography,
} from '@mui/material'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CloseIcon from '@mui/icons-material/Close'
import DeleteIcon from '@mui/icons-material/Delete'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import { useQueries } from '@tanstack/react-query'
import {
	useChapterReviews,
	useAiReview,
	useRunChapterReview,
	useRunSceneReview,
	useSetRecommendationStatus,
	usePromoteRecommendation,
	useDeleteReview,
	AI_REVIEW_KEYS,
} from '../../hooks/useAiReviews'
import { aiApi } from '../../api/ai'
import { useScenes } from '../../hooks/useScenes'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import { useReview } from '../../review/ReviewContext'
import { HIDDEN_STATUSES, normalizeCategory, originLabel, reviewScope } from './recommendationUtils'
import ReviewCard from './ReviewCard'

const RAIL_WIDTH = 332
const RAIL_COLLAPSED_WIDTH = 44

const ALL_REVIEWS = '__ALL__'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'The review could not be run.'
}

function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

/**
 * ReviewRail — the AI review surface for the selected manuscript chapter or
 * scene, rendered on the right edge of the editor area.
 *
 * Bound to exactly one chapter (mounted with key={chapterId}). The current
 * scope is driven by whether a scene is also selected:
 *
 *   • scene selected  → Run reviews that scene; show only that scene's reviews
 *                       and their recommendations.
 *   • chapter selected → Run reviews the whole chapter; show ALL reviews under
 *                       the chapter (chapter- and scene-scope) and AGGREGATE
 *                       their recommendations into one working list.
 *
 * In chapter scope the "All reviews" aggregate is the default. The dropdown
 * can optionally filter to a single review. Each recommendation card shows its
 * origin (which review/scope) so the author knows where a finding came from.
 *
 * Props:
 *   chapterId {string}        the (parent) chapter
 *   sceneId   {string|null}   the selected scene, when one is selected
 *   editor    TipTap editor instance (for scroll-to-passage highlights)
 */
export default function ReviewRail({ chapterId, sceneId, editor }) {
	const review = useReview()

	const scope = sceneId ? 'SCENE' : 'CHAPTER'
	const scopeKey = sceneId ?? 'CHAPTER'

	// When the user explicitly picks a review in the dropdown. At chapter
	// scope the default is ALL_REVIEWS (aggregate); at scene scope the
	// default is the newest review (null falls through to reviews[0]).
	const [explicit, setExplicit] = useState(null) // { id, scopeKey } | null
	const [credentialId, setCredentialId] = useState(null)
	const [runError, setRunError] = useState(null)
	const [promotingId, setPromotingId] = useState(null)
	const [skipDeleteConfirm, setSkipDeleteConfirm] = useState(
		() => window.localStorage.getItem('ai.skipDeleteConfirm') === 'true',
	)

	const persistSkipDeleteConfirm = (value) => {
		setSkipDeleteConfirm(value)
		window.localStorage.setItem('ai.skipDeleteConfirm', value ? 'true' : 'false')
	}

	const { data: credentials = [] } = useAiCredentials()
	const { data: scenes = [] } = useScenes(chapterId)
	const { data: reviews = [], isLoading: loadingReviews } = useChapterReviews(chapterId)

	// ── Scope-filtered reviews ──────────────────────────────────────────────
	const filteredReviews = useMemo(() => {
		if (scope === 'SCENE') {
			return reviews.filter(r => reviewScope(r) === 'SCENE' && r.sceneId === sceneId)
		}
		return reviews // chapter scope: all reviews (chapter + scene)
	}, [reviews, scope, sceneId])

	const completedReviews = useMemo(
		() => filteredReviews.filter(r => r.status === 'COMPLETED'),
		[filteredReviews],
	)

	// ── Selection: aggregate vs single review ───────────────────────────────
	const explicitId = explicit && explicit.scopeKey === scopeKey ? explicit.id : null
	const isAggregateMode = scope === 'CHAPTER' && (explicitId === ALL_REVIEWS || explicitId == null)
	const selectedReviewId = isAggregateMode ? null : (explicitId ?? filteredReviews[0]?.id ?? null)

	// ── Detail fetch: single review (scene scope or explicitly selected) ────
	const { data: singleDetail, isLoading: loadingSingleDetail } = useAiReview(
		selectedReviewId, !!selectedReviewId,
	)

	// ── Detail fetch: ALL completed reviews (chapter scope aggregate) ───────
	const aggregateQueries = useQueries({
		queries: isAggregateMode
			? completedReviews.map(r => ({
				queryKey: AI_REVIEW_KEYS.detail(r.id),
				queryFn: () => aiApi.getReview(r.id),
				staleTime: 30_000,
			}))
			: [],
	})
	const aggregateLoading = isAggregateMode && aggregateQueries.some(q => q.isLoading)

	// ── Mutations ───────────────────────────────────────────────────────────
	const { mutate: runChapter, isPending: runningChapter } = useRunChapterReview()
	const { mutate: runScene, isPending: runningScene } = useRunSceneReview()
	const { mutate: setRecStatus } = useSetRecommendationStatus()
	const { mutate: promote } = usePromoteRecommendation()
	const { mutate: deleteReview, isPending: deleting } = useDeleteReview()

	const running = scope === 'SCENE' ? runningScene : runningChapter

	const defaultCredId = useMemo(
		() => credentials.find(c => c.defaultCredential)?.id ?? credentials[0]?.id ?? null,
		[credentials],
	)
	const effectiveCredId = credentialId ?? defaultCredId
	const hasCredentials = credentials.length > 0

	const scopeTargetLabel = scope === 'SCENE'
		? originLabel({ scope: 'SCENE', sceneId }, scenes)
		: 'Chapter'

	// ── Recommendation list ─────────────────────────────────────────────────
	// In aggregate mode each rec carries _reviewId so handlers can target the
	// right review artifact. In single-review mode they all share the same id.
	const visibleRecs = useMemo(() => {
		if (isAggregateMode) {
			return aggregateQueries
				.filter(q => q.data?.status === 'COMPLETED')
				.flatMap(q => (q.data.recommendations ?? [])
					.filter(rec => !HIDDEN_STATUSES.has((rec.status ?? '').toUpperCase()))
					.map(rec => ({ ...rec, _reviewId: q.data.id, _review: q.data })),
				)
		}
		if (!singleDetail) return []
		return (singleDetail.recommendations ?? [])
			.filter(rec => !HIDDEN_STATUSES.has((rec.status ?? '').toUpperCase()))
			.map(rec => ({ ...rec, _reviewId: singleDetail.id, _review: singleDetail }))
	}, [isAggregateMode, aggregateQueries, singleDetail])

	const openCount = useMemo(
		() => visibleRecs.filter(rec => (rec.status ?? 'OPEN').toUpperCase() === 'OPEN').length,
		[visibleRecs],
	)

	const detailLoaded = isAggregateMode ? !aggregateLoading : !!singleDetail
	const detailLoading = isAggregateMode ? aggregateLoading : loadingSingleDetail

	// For "no findings" / "all handled" messages — which detail do we inspect?
	const anyCompleted = completedReviews.length > 0

	// ── Handlers ────────────────────────────────────────────────────────────
	const handleRun = () => {
		setRunError(null)
		const onSuccess = (r) => setExplicit({ id: r.id, scopeKey })
		const onError = (e) => setRunError(errMessage(e))
		if (scope === 'SCENE') {
			runScene({ sceneId, credentialId: effectiveCredId, model: null }, { onSuccess, onError })
		} else {
			runChapter({ chapterId, credentialId: effectiveCredId, model: null }, { onSuccess, onError })
		}
	}

	const handleSetStatus = (rec, value) => {
		const reviewId = rec._reviewId
		if (!reviewId) return
		setRecStatus({ reviewId, recId: rec.id, status: value ?? 'OPEN', chapterId })
	}

	const handlePromote = (rec, codexCategory, codexTitle) => {
		const reviewId = rec._reviewId
		if (!reviewId) return
		setPromotingId(rec.id)
		promote(
			{ reviewId, recId: rec.id, codexCategory, codexTitle, chapterId },
			{ onSettled: () => setPromotingId(null), onError: (e) => setRunError(errMessage(e)) },
		)
	}

	const handleHighlight = useCallback((anchorText) => {
		if (!anchorText || !editor) return
		editor.commands.highlightAnchor(anchorText)
	}, [editor])

	// The currently selected single review (for delete / origin chip).
	const selectedReview = useMemo(
		() => filteredReviews.find(r => r.id === selectedReviewId) ?? null,
		[filteredReviews, selectedReviewId],
	)

	// ── Collapsed strip ───────────────────────────────────────────────────
	if (review.collapsed) {
		return (
			<Box
				sx={{
					width: RAIL_COLLAPSED_WIDTH,
					flexShrink: 0,
					borderLeft: '1px solid',
					borderColor: 'divider',
					bgcolor: 'background.paper',
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
					py: 1,
					gap: 1,
				}}
			>
				<Tooltip title="Expand review" placement="left">
					<IconButton size="small" onClick={review.expand} aria-label="Expand review">
						<ChevronLeftIcon fontSize="small" />
					</IconButton>
				</Tooltip>
				<Tooltip title={`${openCount} open finding${openCount === 1 ? '' : 's'}`} placement="left">
					<Badge badgeContent={openCount} color="primary" overlap="circular">
						<CheckCircleOutlinedIcon sx={{ fontSize: 19, color: 'primary.main', mt: 0.5 }} />
					</Badge>
				</Tooltip>
			</Box>
		)
	}

	// ── Expanded rail ─────────────────────────────────────────────────────
	return (
		<Box
			sx={{
				width: RAIL_WIDTH,
				flexShrink: 0,
				minHeight: 0,
				borderLeft: '1px solid',
				borderColor: 'divider',
				bgcolor: 'background.paper',
				display: 'flex',
				flexDirection: 'column',
				overflow: 'hidden',
			}}
		>
			{/* Header */}
			<Box
				sx={{
					display: 'flex',
					alignItems: 'center',
					gap: 0.5,
					px: 1.5,
					py: 1,
					borderBottom: '1px solid',
					borderColor: 'divider',
				}}
			>
				<CheckCircleOutlinedIcon sx={{ fontSize: 18, color: 'primary.main' }} />
				<Typography variant="subtitle2" sx={{ flexGrow: 1 }}>
					AI Review{openCount > 0 ? ` · ${openCount} open` : ''}
				</Typography>
				<Tooltip title="Collapse">
					<IconButton size="small" onClick={review.collapse} aria-label="Collapse review">
						<ChevronRightIcon fontSize="small" />
					</IconButton>
				</Tooltip>
				<Tooltip title="Close review">
					<IconButton size="small" onClick={review.closeReview} aria-label="Close review">
						<CloseIcon fontSize="small" />
					</IconButton>
				</Tooltip>
			</Box>

			{/* Scrollable body */}
			<Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', px: 1.5, py: 1.5 }}>
				{!hasCredentials ? (
					<Alert severity="info">
						Add an AI key from Settings → AI to run reviews.
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
							{running ? 'Reviewing…' : (scope === 'SCENE' ? 'Review scene' : 'Review chapter')}
						</Button>

						<Typography
							variant="caption"
							color="text.secondary"
							sx={{ display: 'block', mt: 0.5, textAlign: 'center' }}
						>
							Reviewing: {scopeTargetLabel}
						</Typography>

						{runError && <Alert severity="error" sx={{ mt: 1 }}>{runError}</Alert>}

						{/* Review selector — chapter scope: aggregate "All" + individual reviews;
						    scene scope: individual reviews only */}
						{filteredReviews.length > 0 && (
							<TextField
								select label="Review" size="small" fullWidth sx={{ mt: 1.5 }}
								value={isAggregateMode ? ALL_REVIEWS : (selectedReviewId ?? '')}
								onChange={(e) => {
									const val = e.target.value
									setExplicit({ id: val, scopeKey })
								}}
							>
								{scope === 'CHAPTER' && (
									<MenuItem value={ALL_REVIEWS}>
										All reviews ({completedReviews.length})
									</MenuItem>
								)}
								{filteredReviews.map(r => (
									<MenuItem key={r.id} value={r.id}>
										{originLabel(r, scenes)} · {formatTime(r.submittedAt)} · {r.status}
									</MenuItem>
								))}
							</TextField>
						)}

						{/* Delete button — only in single-review mode */}
						{selectedReviewId && !isAggregateMode && (
							<Box sx={{ display: 'flex', alignItems: 'center', mt: 0.75 }}>
								{selectedReview && (
									<Chip
										size="small"
										variant="outlined"
										label={originLabel(selectedReview, scenes)}
										sx={{ maxWidth: '100%', '& .MuiChip-label': { overflow: 'hidden', textOverflow: 'ellipsis' } }}
									/>
								)}
								<Box sx={{ flexGrow: 1 }} />
								<Tooltip title="Move this review to trash">
									<span>
										<IconButton
											size="small"
											disabled={deleting}
											onClick={() => {
												setRunError(null)
												deleteReview(selectedReviewId, {
													onSuccess: () => setExplicit(null),
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

						{/* ── Recommendations ─────────────────────────────────────── */}
						{loadingReviews && reviews.length === 0 ? (
							<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
						) : filteredReviews.length === 0 ? (
							<>
								<Typography variant="body2" color="text.secondary">
									{scope === 'SCENE'
										? 'No reviews for this scene yet. Run one to review it.'
										: 'No reviews yet. Run one to see recommendations.'}
								</Typography>
								{scope === 'SCENE' && reviews.length > 0 && (
									<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
										This chapter has other reviews — select the chapter in the nav to see them.
									</Typography>
								)}
							</>
						) : detailLoading ? (
							<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
						) : !isAggregateMode && singleDetail?.status === 'FAILED' ? (
							<Alert severity="error">{singleDetail.errorMessage ?? 'The review failed.'}</Alert>
						) : !isAggregateMode && singleDetail?.status !== 'COMPLETED' ? (
							<Typography variant="body2" color="text.secondary">Review in progress…</Typography>
						) : !anyCompleted ? (
							<Typography variant="body2" color="text.secondary">No completed reviews yet.</Typography>
						) : visibleRecs.length === 0 ? (
							<Alert severity="success">All recommendations have been handled.</Alert>
						) : (
							visibleRecs.map(rec => (
								<Box key={`${rec.id}:${rec._reviewId}`}>
									{/* Origin chip in aggregate mode */}
									{isAggregateMode && rec._review && (
										<Chip
											size="small"
											variant="outlined"
											label={originLabel(rec._review, scenes)}
											sx={{ mb: 0.5, maxWidth: '100%', '& .MuiChip-label': { overflow: 'hidden', textOverflow: 'ellipsis' } }}
										/>
									)}
									<ReviewCard
										rec={rec}
										onSetStatus={handleSetStatus}
										onPromote={handlePromote}
										promoting={promotingId === rec.id}
										skipDeleteConfirm={skipDeleteConfirm}
										setSkipDeleteConfirm={persistSkipDeleteConfirm}
										onHighlight={handleHighlight}
									/>
								</Box>
							))
						)}
					</>
				)}
			</Box>
		</Box>
	)
}
