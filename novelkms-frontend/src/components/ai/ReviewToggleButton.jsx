import { Button, Tooltip } from '@mui/material'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import { useReview } from '../../review/ReviewContext'

/**
 * ReviewToggleButton — AppBar entry point for editor Review Mode.
 *
 * Enabled only for a manuscript chapter (a chapter inside a book, not a codex
 * category). Toggles the review rail open/closed; opening always expands it.
 * Must be rendered inside <ReviewProvider> so useReview() resolves.
 *
 * Props:
 *   selection  current app selection
 *   sx         AppBar button styling (shared topBarButtonSx)
 */
export default function ReviewToggleButton({ selection, sx }) {
	const review = useReview()

	const isChapter = !!(selection?.chapterId && selection?.bookId && !selection?.codexId)
	const tip = isChapter
		? (review.open ? 'Hide AI review' : 'Review this chapter with AI')
		: 'Select a chapter to review'

	const handleClick = () => {
		if (!isChapter) return
		if (review.open) review.closeReview()
		else review.openReview()
	}

	return (
		<Tooltip title={tip}>
			<span>
				<Button
					color="inherit"
					size="small"
					startIcon={<CheckCircleOutlinedIcon fontSize="small" />}
					disabled={!isChapter}
					onClick={handleClick}
					sx={{
						...sx,
						...(review.open && isChapter ? { bgcolor: 'rgba(255,255,255,0.14)', color: '#fff' } : null),
					}}
				>
					AI Review
				</Button>
			</span>
		</Tooltip>
	)
}
