import { useState } from 'react'
import {
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	IconButton,
	Tooltip,
	Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { useChapterReviews, useDeleteReview } from '../../hooks/useAiReviews'
import { useScenes } from '../../hooks/useScenes'
import { originLabel } from './recommendationUtils'

function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

/**
 * ChapterReviewHistoryDialog — shows the list of AI review runs for a chapter,
 * with the ability to move individual runs to Trash.
 *
 * This is the home for functionality that was previously on the ReviewRail
 * History tab. It is opened from the chapter nav context menu via
 * "Review history…".
 *
 * Props:
 *   open        {boolean}
 *   onClose     {() => void}
 *   chapterId   {string|null}
 */
export default function ChapterReviewHistoryDialog({ open, onClose, chapterId }) {
	const [error, setError] = useState(null)

	const { data: reviews = [], isLoading } = useChapterReviews(chapterId, open && !!chapterId)
	const { data: scenes = [] } = useScenes(chapterId)
	const { mutate: deleteReview, isPending: deleting } = useDeleteReview()

	const handleDelete = (reviewId) => {
		setError(null)
		deleteReview(reviewId, {
			onError: (e) => setError(
				e?.response?.data?.message ?? e?.message ?? 'Could not move review to trash.'
			),
		})
	}

	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle>AI Review history</DialogTitle>

			<DialogContent dividers sx={{ p: 0 }}>
				{isLoading && (
					<Box sx={{ p: 2 }}>
						<CircularProgress size={20} />
					</Box>
				)}

				{!isLoading && reviews.length === 0 && (
					<Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
						No reviews yet for this chapter.
					</Typography>
				)}

				{error && (
					<Typography variant="caption" color="error" sx={{ px: 2, pt: 1, display: 'block' }}>
						{error}
					</Typography>
				)}

				{reviews.map((r) => (
					<Box
						key={r.id}
						sx={{
							display: 'flex',
							alignItems: 'center',
							gap: 1,
							px: 2,
							py: 1,
							borderBottom: '1px solid',
							borderColor: 'divider',
							'&:last-of-type': { borderBottom: 'none' },
						}}
					>
						<Box sx={{ minWidth: 0, flexGrow: 1 }}>
							<Typography variant="body2" noWrap>
								{originLabel(r, scenes)} · {r.model || '—'}
							</Typography>
							<Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
								{formatTime(r.submittedAt)}
							</Typography>
						</Box>

						<Chip
							size="small"
							label={r.status}
							sx={{ fontSize: '0.7rem', height: 20, flexShrink: 0 }}
						/>

						<Tooltip title="Move this review to Trash">
							<span>
								<IconButton
									size="small"
									disabled={deleting}
									aria-label="Move review to trash"
									onClick={() => handleDelete(r.id)}
								>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</span>
						</Tooltip>
					</Box>
				))}
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
