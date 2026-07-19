import { Box, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined'
import { feedbackTypeLabel } from '../../utils/reviewFeedbackTypes'
import ReviewCardMenu from './ReviewCardMenu'

// A coarse "published N ago" without pulling in a date library — the queue only
// needs a sense of freshness, not a precise timestamp.
function publishedAgo(iso) {
	if (!iso) return ''
	const then = new Date(iso).getTime()
	if (Number.isNaN(then)) return ''
	const days = Math.floor((Date.now() - then) / 86400000)
	if (days <= 0) return 'today'
	if (days === 1) return 'yesterday'
	if (days < 30) return `${days} days ago`
	const months = Math.floor(days / 30)
	return months === 1 ? 'a month ago' : `${months} months ago`
}

/**
 * One row of the review queue (spec §12): enough to decide whether to open the
 * package, and nothing that identifies the manuscript. The author is shown by
 * handle only — the entry never carries a raw user id or a chapter id.
 */
export default function QueueEntryCard({ entry, onOpen }) {
	return (
		<Paper variant="outlined" sx={{ p: 2 }}>
			<Stack spacing={1.25}>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
					<Box sx={{ flexGrow: 1, minWidth: 0 }}>
						<Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap title={entry.title}>
							{entry.title}
						</Typography>
						<Typography variant="caption" color="text.secondary">
							by @{entry.authorHandle}
							{entry.genre ? ` · ${entry.genre}` : ''} · {(entry.wordCount ?? 0).toLocaleString()} words
						</Typography>
					</Box>
					{entry.contentWarnings && (
						<Chip
							size="small"
							color="warning"
							variant="outlined"
							icon={<WarningAmberOutlinedIcon />}
							label="Content warning"
						/>
					)}
					<ReviewCardMenu
						handle={entry.authorHandle}
						contentTarget={{ type: 'REQUEST', id: entry.id, label: 'this request' }}
					/>
				</Stack>

				{entry.description && (
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
						{entry.description}
					</Typography>
				)}

				{entry.feedbackTypes?.length > 0 && (
					<Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
						{entry.feedbackTypes.map(k => (
							<Chip key={k} size="small" variant="outlined" label={feedbackTypeLabel(k)} />
						))}
					</Stack>
				)}

				<Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
					<Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
						{publishedAgo(entry.publishedAt)}
						{entry.reviewCount > 0
							? ` · ${entry.reviewCount} review${entry.reviewCount === 1 ? '' : 's'}`
							: ''}
						{entry.maxReviews ? ` · up to ${entry.maxReviews}` : ''}
					</Typography>
					<Button size="small" variant="contained" onClick={() => onOpen(entry)}>Open</Button>
				</Stack>
			</Stack>
		</Paper>
	)
}
