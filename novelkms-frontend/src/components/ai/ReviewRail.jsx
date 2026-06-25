import { useCallback, useMemo, useState } from 'react'
import {
	Alert,
	Badge,
	Box,
	Button,
	Chip,
	CircularProgress,
	IconButton,
	MenuItem,
	Tab,
	Tabs,
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
import { useChapterMemoryStatus } from '../../hooks/useChapterMemory'
import { aiApi } from '../../api/ai'
import { useScenes } from '../../hooks/useScenes'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import { useReview } from '../../review/ReviewContext'
import {
	HIDDEN_STATUSES,
	STATUS,
	isResolvedStatus,
	normalizeStatus,
	originLabel,
	reviewScope,
} from './recommendationUtils'
import ReviewCard from './ReviewCard'
import ChapterMemoryEditor from './ChapterMemoryEditor'
import PreReviewMemoryDialog from './PreReviewMemoryDialog'
import { isFlagged } from './memoryStatus'

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
 * Findings are triaged bug-tracker style across three tabs:
 *
 *   • Active   — OPEN + DEFERRED (what still needs attention)
 *   • Resolved — DONE + DISMISSED + PROMOTED (handled, kept for reference)
 *   • History  — the review runs themselves; focus one or move it to Trash.
 *
 * Props:
 *   chapterId {string}        the (parent) chapter
 *   sceneId   {string|null}   the selected scene, when one is selected
 *   editor    TipTap editor instance (for scroll-to-passage highlights)
 */
export default function ReviewRail({ chapterId, sceneId, bookId, editor }) {
	const review = useReview()

	const scope = sceneId ? 'SCENE' : 'CHAPTER'
	const scopeKey = sceneId ?? 'CHAPTER'

	// When the user explicitly picks a review in History. At chapter scope the
	// default is ALL_REVIEWS (aggregate); at scene scope the default is the
	// newest review (null falls through to reviews[0]).
	const [explicit, setExplicit] = useState(null) // { id, scopeKey } | null
	const [credentialId, setCredentialId] = useState(null)
	const [runError, setRunError] = useState(null)
	const [promotingId, setPromotingId] = useState(null)
	const [tab, setTab] = useState('ACTIVE') // ACTIVE | RESOLVED | HISTORY | MEMORY
	const [memWarnOpen, setMemWarnOpen] = useState(false)

	const { data: credentials = [] } = useAiCredentials()
	const { data: scenes = [] } = useScenes(chapterId)
	const { data: reviews = [], isLoading: loadingReviews } = useChapterReviews(chapterId)

	// Per-chapter memory status for the book — used to warn before a chapter
	// review when a PRECEDING chapter's memory document is missing or behind.
	const { data: memStatus = [] } = useChapterMemoryStatus(bookId, !!bookId)
	const flaggedPreceding = useMemo(() => {
		if (scope !== 'CHAPTER' || memStatus.length === 0) return []
		const idx = memStatus.findIndex(s => s.chapterId === chapterId)
		if (idx <= 0) return []
		return memStatus.slice(0, idx).filter(s => isFlagged(s.state))
	}, [memStatus, scope, chapterId])

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

	// ── Recommendation set for the current scope/focus ──────────────────────
	// In aggregate mode each rec carries _reviewId so handlers can target the
	// right review artifact. In single-review mode they all share the same id.
	const allScopeRecs = useMemo(() => {
		if (isAggregateMode) {
			return aggregateQueries
				.filter(q => q.data?.status === 'COMPLETED')
				.flatMap(q => (q.data.recommendations ?? [])
					.filter(rec => !HIDDEN_STATUSES.has(normalizeStatus(rec.status)))
					.map(rec => ({ ...rec, _reviewId: q.data.id, _review: q.data })),
				)
		}
		if (!singleDetail) return []
		return (singleDetail.recommendations ?? [])
			.filter(rec => !HIDDEN_STATUSES.has(normalizeStatus(rec.status)))
			.map(rec => ({ ...rec, _reviewId: singleDetail.id, _review: singleDetail }))
	}, [isAggregateMode, aggregateQueries, singleDetail])

	// Active = OPEN + DEFERRED, with OPEN listed first (DEFERRED is parked).
	const activeRecs = useMemo(() => {
		const open = allScopeRecs.filter(r => normalizeStatus(r.status) === STATUS.OPEN)
		const deferred = allScopeRecs.filter(r => normalizeStatus(r.status) === STATUS.DEFERRED)
		return [...open, ...deferred]
	}, [allScopeRecs])

	const resolvedRecs = useMemo(
		() => allScopeRecs.filter(r => isResolvedStatus(r.status)),
		[allScopeRecs],
	)

	const openCount = useMemo(
		() => allScopeRecs.filter(r => normalizeStatus(r.status) === STATUS.OPEN).length,
		[allScopeRecs],
	)

	const detailLoading = isAggregateMode ? aggregateLoading : loadingSingleDetail
	const anyCompleted = completedReviews.length > 0

	// ── Handlers ────────────────────────────────────────────────────────────
	const onRunSuccess = (r) => { setExplicit({ id: r.id, scopeKey }); setTab('ACTIVE') }
	const onRunError = (e) => setRunError(errMessage(e))

	const doRunChapter = () => {
		setRunError(null)
		runChapter({ chapterId, credentialId: effectiveCredId, model: null },
			{ onSuccess: onRunSuccess, onError: onRunError })
	}

	const handleRun = () => {
		setRunError(null)
		if (scope === 'SCENE') {
			runScene({ sceneId, credentialId: effectiveCredId, model: null },
				{ onSuccess: onRunSuccess, onError: onRunError })
			return
		}
		// Chapter scope: warn first if a preceding chapter's memory is missing/stale.
		if (flaggedPreceding.length > 0) { setMemWarnOpen(true); return }
		doRunChapter()
	}

	const handleSetStatus = (rec, value) => {
		const reviewId = rec._reviewId
		if (!reviewId) return
		setRecStatus({ reviewId, recId: rec.id, status: value ?? STATUS.OPEN, chapterId })
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

	const focusRun = (id) => {
		setExplicit({ id, scopeKey })
		setTab('ACTIVE')
	}

	const handleDeleteRun = (reviewId) => {
		setRunError(null)
		deleteReview(reviewId, {
			onSuccess: () => setExplicit(null),
			onError: (e) => setRunError(errMessage(e)),
		})
	}

	// Whether a History row is the one currently focused.
	const isFocused = (id) =>
		id === ALL_REVIEWS ? isAggregateMode : (!isAggregateMode && id === selectedReviewId)

	// ── Findings renderer (shared by Active / Resolved tabs) ────────────────
	const renderFindings = (list, emptyMessage) => {
		if (loadingReviews && reviews.length === 0) {
			return <Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
		}
		if (filteredReviews.length === 0) {
			return (
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
			)
		}
		if (detailLoading) {
			return <Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
		}
		if (!isAggregateMode && singleDetail?.status === 'FAILED') {
			return <Alert severity="error">{singleDetail.errorMessage ?? 'The review failed.'}</Alert>
		}
		if (!isAggregateMode && singleDetail && singleDetail.status !== 'COMPLETED') {
			return <Typography variant="body2" color="text.secondary">Review in progress…</Typography>
		}
		if (!anyCompleted) {
			return <Typography variant="body2" color="text.secondary">No completed reviews yet.</Typography>
		}
		if (list.length === 0) {
			return <Alert severity="success">{emptyMessage}</Alert>
		}
		return list.map(rec => (
			<Box key={`${rec.id}:${rec._reviewId}`}>
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
					onHighlight={handleHighlight}
				/>
			</Box>
		))
	}

	// ── History renderer ────────────────────────────────────────────────────
	const renderHistory = () => {
		if (loadingReviews && reviews.length === 0) {
			return <Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
		}
		if (filteredReviews.length === 0) {
			return <Typography variant="body2" color="text.secondary">No reviews yet.</Typography>
		}
		return (
			<Box>
				{scope === 'CHAPTER' && (
					<Box
						onClick={() => focusRun(ALL_REVIEWS)}
						sx={{
							p: 1, mb: 0.5, borderRadius: 1, cursor: 'pointer',
							bgcolor: isFocused(ALL_REVIEWS) ? 'action.selected' : 'transparent',
							'&:hover': { bgcolor: 'action.hover' },
						}}
					>
						<Typography variant="body2">All reviews ({completedReviews.length})</Typography>
						<Typography variant="caption" color="text.secondary">
							Aggregate every finding in this chapter
						</Typography>
					</Box>
				)}

				{filteredReviews.map(r => (
					<Box
						key={r.id}
						onClick={() => focusRun(r.id)}
						sx={{
							display: 'flex', alignItems: 'center', gap: 0.5,
							p: 1, mb: 0.5, borderRadius: 1, cursor: 'pointer',
							bgcolor: isFocused(r.id) ? 'action.selected' : 'transparent',
							'&:hover': { bgcolor: 'action.hover' },
						}}
					>
						<Box sx={{ minWidth: 0, flexGrow: 1 }}>
							<Typography variant="body2" noWrap>
								{originLabel(r, scenes)} · {r.model || '—'}
							</Typography>
							<Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
								{formatTime(r.submittedAt)} · {r.status}
							</Typography>
						</Box>
						<Tooltip title="Move this review to Trash">
							<span>
								<IconButton
									size="small"
									disabled={deleting}
									aria-label="Move review to trash"
									onClick={(e) => { e.stopPropagation(); handleDeleteRun(r.id) }}
								>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</span>
						</Tooltip>
					</Box>
				))}
			</Box>
		)
	}

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

						<Tabs
							value={tab}
							onChange={(_e, val) => setTab(val)}
							variant="fullWidth"
							sx={{
								mt: 1,
								minHeight: 36,
								'& .MuiTab-root': { minHeight: 36, py: 0.5, textTransform: 'none', fontSize: '0.78rem' },
							}}
						>
							<Tab value="ACTIVE" label={`Active (${activeRecs.length})`} />
							<Tab value="RESOLVED" label={`Resolved (${resolvedRecs.length})`} />
							<Tab value="HISTORY" label="History" />
							<Tab value="MEMORY" label="Memory" />
						</Tabs>

						<Box sx={{ mt: 1.5 }}>
							{tab === 'ACTIVE' && renderFindings(activeRecs, 'Nothing active — the queue is clear.')}
							{tab === 'RESOLVED' && renderFindings(resolvedRecs, 'No resolved findings yet.')}
							{tab === 'HISTORY' && renderHistory()}
							{tab === 'MEMORY' && (
								<ChapterMemoryEditor
									chapterId={chapterId}
									bookId={bookId}
									credentialId={effectiveCredId}
									sceneScopeNote={scope === 'SCENE'}
								/>
							)}
						</Box>
					</>
				)}
			</Box>

			<PreReviewMemoryDialog
				open={memWarnOpen}
				onCancel={() => setMemWarnOpen(false)}
				onProceed={() => { setMemWarnOpen(false); doRunChapter() }}
				flagged={flaggedPreceding}
				bookId={bookId}
				credentialId={effectiveCredId}
			/>
		</Box>
	)
}
