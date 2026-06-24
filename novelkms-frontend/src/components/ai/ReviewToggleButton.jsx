import { Button, Tooltip } from '@mui/material'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import { useReview } from '../../review/ReviewContext'

/**
 * ReviewToggleButton — AppBar entry point for editor Review Mode.
 *
 * Enabled for a manuscript chapter OR a manuscript scene (anything inside a
 * book that is not a codex category/entry). Toggles the review rail open/closed;
 * opening always expands it. The rail itself decides scope from the selection,
 * so this button only gates on whether the selection is reviewable. Must be
 * rendered inside <ReviewProvider> so useReview() resolves.
 *
 * Props:
 *   selection  current app selection
 *   sx         AppBar button styling (shared topBarButtonSx)
 */
export default function ReviewToggleButton({ selection, sx }) {
	const review = useReview()

	// A manuscript scene selection carries both chapterId and sceneId (codex
	// entries set sceneId null), so this is true for chapters and scenes alike.
	const isReviewable = !!(selection?.chapterId && selection?.bookId && !selection?.codexId)
	const isScene = isReviewable && !!selection?.sceneId

	const tip = isReviewable
		? (review.open
			? 'Hide AI review'
			: (isScene ? 'Review this scene with AI' : 'Review this chapter with AI'))
		: 'Select a chapter or scene to review'

	const handleClick = () => {
		if (!isReviewable) return
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
					disabled={!isReviewable}
					onClick={handleClick}
					sx={{
						...sx,
						...(review.open && isReviewable ? { bgcolor: 'rgba(255,255,255,0.14)', color: '#fff' } : null),
					}}
				>
					AI Review
				</Button>
			</span>
		</Tooltip>
	)
}
