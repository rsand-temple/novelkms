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
import {
	useChapterReviews,
	useAiReview,
	useRunChapterReview,
	useRunSceneReview,
	useSetRecommendationStatus,
	usePromoteRecommendation,
	useDeleteReview,
} from '../../hooks/useAiReviews'
import { useScenes } from '../../hooks/useScenes'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import { useReview } from '../../review/ReviewContext'
import { HIDDEN_STATUSES, normalizeCategory, originLabel, reviewScope } from './recommendationUtils'
import ReviewCard from './ReviewCard'

const RAIL_WIDTH = 332
const RAIL_COLLAPSED_WIDTH = 44

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
 * scene, rendered on the right edge of the editor area (not the global
 * inspector).
 *
 * A chapter review and a scene review are the same artifact differing only in
 * scope, and a scene review is filed under its parent chapter — so the rail is
 * bound to the chapter (mounted with key={chapterId}) and the current scope is
 * driven by whether a scene is also selected. Switching scenes within a chapter
 * updates the scope/filter WITHOUT remounting, preserving rail state:
 *
 *   • scene selected  → Run reviews that scene; history is filtered to that
 *                       scene's reviews.
 *   • chapter selected → Run reviews the whole chapter; history shows every
 *                       review under the chapter (chapter- and scene-scope),
 *                       each tagged with its origin.
 *
 * Props:
 *   chapterId {string}        the (parent) chapter
 *   sceneId   {string|null}   the selected scene, when one is selected
 *   editor    TipTap editor instance (for scroll-to-passage highlights)
 */
export default function ReviewRail({ chapterId, sceneId, editor }) {
	const review = useReview()

	const scope = sceneId ? 'SCENE' : 'CHAPTER'
	// Identity of the current scope target. An explicit review selection is only
	// honored while this matches, so changing scene/scope falls back to the
	// newest review in the new filter without a setState-in-effect.
	const scopeKey = sceneId ?? 'CHAPTER'

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

	// Filter the chapter's review history by the current selection.
	const filteredReviews = useMemo(() => {
		if (scope === 'SCENE') {
			return reviews.filter(r => reviewScope(r) === 'SCENE' && r.sceneId === sceneId)
		}
		return reviews
	}, [reviews, scope, sceneId])

	const explicitId = explicit && explicit.scopeKey === scopeKey ? explicit.id : null
	const selectedReviewId = explicitId ?? filteredReviews[0]?.id ?? null
	const selectedReview = useMemo(
		() => filteredReviews.find(r => r.id === selectedReviewId) ?? null,
		[filteredReviews, selectedReviewId],
	)

	const { data: detail, isLoading: loadingDetail } = useAiReview(selectedReviewId, !!selectedReviewId)
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

	// The label of whatever Run will review right now.
	const scopeTargetLabel = scope === 'SCENE'
		? originLabel({ scope: 'SCENE', sceneId }, scenes)
		: 'Chapter'

	// Visible (active) recommendations and the count of still-undecided ones.
	const visibleRecs = useMemo(
		() => (detail?.recommendations ?? []).filter(
			rec => !HIDDEN_STATUSES.has((rec.status ?? '').toUpperCase()),
		),
		[detail],
	)
	const openCount = useMemo(
		() => visibleRecs.filter(rec => (rec.status ?? 'OPEN').toUpperCase() === 'OPEN').length,
		[visibleRecs],
	)

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

	const handleHighlight = useCallback((anchorText) => {
		if (!anchorText || !editor) return
		editor.commands.highlightAnchor(anchorText)
	}, [editor])

	// ── Collapsed strip ───────────────────────────────────────────────────────
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

	// ── Expanded rail ─────────────────────────────────────────────────────────
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

						{filteredReviews.length > 1 && (
							<TextField
								select label="Review" size="small" fullWidth sx={{ mt: 1.5 }}
								value={selectedReviewId ?? ''}
								onChange={(e) => setExplicit({ id: e.target.value, scopeKey })}
							>
								{filteredReviews.map(r => (
									<MenuItem key={r.id} value={r.id}>
										{originLabel(r, scenes)} · {formatTime(r.submittedAt)} · {r.status}
									</MenuItem>
								))}
							</TextField>
						)}

						{selectedReviewId && (
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

						{loadingReviews && reviews.length === 0 ? (
							<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
						) : !selectedReviewId ? (
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
						) : loadingDetail || !detail ? (
							<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
						) : detail.status === 'FAILED' ? (
							<Alert severity="error">{detail.errorMessage ?? 'The review failed.'}</Alert>
						) : detail.status !== 'COMPLETED' ? (
							<Typography variant="body2" color="text.secondary">Review in progress…</Typography>
						) : (detail.recommendations ?? []).length === 0 ? (
							<Alert severity="success">
								No notes — the model had no substantive recommendations on this {scope === 'SCENE' ? 'scene' : 'chapter'}.
							</Alert>
						) : visibleRecs.length === 0 ? (
							<Alert severity="success">All recommendations have been handled.</Alert>
						) : (
							visibleRecs.map(rec => (
								<ReviewCard
									key={`${rec.id}:${normalizeCategory(rec.codexCategory)}`}
									rec={rec}
									onSetStatus={handleSetStatus}
									onPromote={handlePromote}
									promoting={promotingId === rec.id}
									skipDeleteConfirm={skipDeleteConfirm}
									setSkipDeleteConfirm={persistSkipDeleteConfirm}
									onHighlight={handleHighlight}
								/>
							))
						)}
					</>
				)}
			</Box>
		</Box>
	)
}
