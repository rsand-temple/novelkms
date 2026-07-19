import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Paper,
	Stack,
	Typography,
} from '@mui/material'
import { htmlToPlain } from '../../utils/reviewBody'
import ReviewCardMenu from './ReviewCardMenu'
import { useReviewsReceived, useMarkReceivedRead } from '../../hooks/useHumanReviews'

/**
 * One received review. The body is rendered as PLAIN TEXT via htmlToPlain, never as
 * live HTML: a review is authored by another user, so this is a cross-user render
 * boundary. While Phase-1 reviews are plain text, extracting the text is a complete
 * and faithful defense — a hostile reviewer's markup or script simply shows as inert
 * characters, with no sandboxed iframe required.
 */
function ReceivedCard({ row, busy, onMarkRead }) {
	const body = htmlToPlain(row.contentHtml)
	const sourceLine = [row.sourceTitle, row.bookTitle].filter(Boolean).join(' · ')

	return (
		<Paper
			variant="outlined"
			sx={{
				p: 2,
				borderColor: row.read ? 'divider' : 'primary.main',
				borderWidth: row.read ? 1 : 1.5,
			}}
		>
			<Stack spacing={1.25}>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
					<Box sx={{ flexGrow: 1, minWidth: 0 }}>
						<Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap title={row.requestTitle}>
							{row.requestTitle}
						</Typography>
						<Typography variant="caption" color="text.secondary">
							from @{row.reviewerHandle}{row.reviewerDisplayName ? ` (${row.reviewerDisplayName})` : ''}
							{sourceLine ? ` · ${sourceLine}` : ''}
						</Typography>
					</Box>
					<Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', justifyContent: 'flex-end', alignItems: 'center', rowGap: 0.5 }}>
						{!row.read && <Chip size="small" color="primary" label="New" />}
						{row.aiAssisted && <Chip size="small" variant="outlined" label="AI-assisted" />}
						<ReviewCardMenu
							handle={row.reviewerHandle}
							contentTarget={{ type: 'REVIEW', id: row.reviewId, label: 'this review' }}
						/>
					</Stack>
				</Stack>

				<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
					{body || <em>(empty review)</em>}
				</Typography>

				<Stack direction="row" spacing={1} sx={{ alignItems: 'center', rowGap: 0.5 }}>
					<Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
						{row.submittedAt ? `Submitted ${new Date(row.submittedAt).toLocaleDateString()}` : ''}
						{row.wordCount > 0 ? ` · ${row.wordCount} words` : ''}
					</Typography>
					{!row.read && (
						<Button size="small" disabled={busy} onClick={() => onMarkRead(row.reviewId)}>Mark as read</Button>
					)}
				</Stack>
			</Stack>
		</Paper>
	)
}

/**
 * The "Reviews Received" tab: submitted feedback on the author's own packages, newest
 * first. Unread reviews are highlighted and carry a "New" chip; the author clears one
 * with Mark as read, which is the whole of Phase 1's notification model.
 */
export default function ReviewsReceivedPanel() {
	const { data: rows, isLoading, isError, error } = useReviewsReceived()
	const markRead = useMarkReceivedRead()

	const needsProfile = isError && error?.response?.status === 409

	if (isLoading) {
		return <Stack sx={{ alignItems: 'center', pt: 6 }}><CircularProgress /></Stack>
	}

	if (needsProfile) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Claim a handle to publish</Typography>
					<Typography variant="body2" color="text.secondary">
						Once you've claimed a handle and published a chapter for review, feedback will appear here.
					</Typography>
				</Paper>
			</Box>
		)
	}

	if (isError) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Alert severity="error">
					{error?.response?.data?.message ?? 'Could not load your received reviews.'}
				</Alert>
			</Box>
		)
	}

	const list = rows ?? []
	const unread = list.filter(r => !r.read)

	return (
		<Box sx={{ maxWidth: 720, mx: 'auto' }}>
			{list.length === 0 ? (
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>No reviews yet</Typography>
					<Typography variant="body2" color="text.secondary">
						When another writer submits feedback on one of your published chapters, it will show up here.
					</Typography>
				</Paper>
			) : (
				<Stack spacing={1.5}>
					{unread.length > 1 && (
						<Box sx={{ textAlign: 'right' }}>
							<Button
								size="small"
								disabled={markRead.isPending}
								onClick={() => unread.forEach(r => markRead.mutate(r.reviewId))}
							>
								Mark all as read
							</Button>
						</Box>
					)}
					{list.map(row => (
						<ReceivedCard
							key={row.reviewId}
							row={row}
							busy={markRead.isPending}
							onMarkRead={(id) => markRead.mutate(id)}
						/>
					))}
				</Stack>
			)}
		</Box>
	)
}
