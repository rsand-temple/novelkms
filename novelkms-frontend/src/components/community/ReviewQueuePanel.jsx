import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	CircularProgress,
	MenuItem,
	Paper,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import QueueEntryCard from './QueueEntryCard'
import ReviewPackageDialog from './ReviewPackageDialog'
import { useReviewQueue } from '../../hooks/useReviewQueue'

const SORTS = [
	{ value: 'newest', label: 'Newest' },
	{ value: 'oldest', label: 'Oldest' },
	{ value: 'fewest', label: 'Fewest reviews' },
]

const EMPTY = { genre: '', minWords: '', maxWords: '', sort: 'newest' }

// The draft filters the reviewer edits become "applied" only on Apply, so the queue
// does not re-query on every keystroke and the reviewer stays in control of when the
// list changes. Blank numeric fields become undefined so they drop off the query.
function toApplied(draft) {
	return {
		genre:    draft.genre.trim() || undefined,
		minWords: draft.minWords === '' ? undefined : Number(draft.minWords),
		maxWords: draft.maxWords === '' ? undefined : Number(draft.maxWords),
		sort:     draft.sort,
	}
}

/**
 * The Review Queue tab (spec §12): a transparent, chronological list of open review
 * requests from other writers, with basic filters and no automated ranking. Opening
 * an entry shows its package; reading and writing a review is slice 1D.
 *
 * <p>The queue read returns 409 when the viewer has not claimed a handle — the one
 * expected non-404 status — which becomes a claim-a-handle prompt rather than a raw
 * error.
 */
export default function ReviewQueuePanel({ onGoToProfile }) {
	const [draft, setDraft] = useState(EMPTY)
	const [applied, setApplied] = useState(toApplied(EMPTY))
	const [openId, setOpenId] = useState(null)

	const query = useReviewQueue(applied)
	const set = (key) => (e) => setDraft(prev => ({ ...prev, [key]: e.target.value }))

	const needsProfile = query.isError && query.error?.response?.status === 409
	const entries = query.data?.pages.flat() ?? []

	if (needsProfile) {
		return (
			<Box sx={{ maxWidth: 720, mx: 'auto' }}>
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Claim a handle to browse</Typography>
					<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
						Your handle is your identity in the review community, and it's the gate for taking part.
					</Typography>
					{onGoToProfile && (
						<Button variant="contained" onClick={onGoToProfile}>Go to My Profile</Button>
					)}
				</Paper>
			</Box>
		)
	}

	return (
		<Box sx={{ maxWidth: 820, mx: 'auto' }}>
			<Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
				<Stack
					direction={{ xs: 'column', sm: 'row' }}
					spacing={1.5}
					sx={{ alignItems: { sm: 'flex-end' } }}
				>
					<TextField label="Genre" value={draft.genre} onChange={set('genre')} size="small" sx={{ flexGrow: 1 }} />
					<TextField
						label="Min words"
						value={draft.minWords}
						onChange={set('minWords')}
						size="small"
						type="number"
						slotProps={{ htmlInput: { min: 0 } }}
						sx={{ maxWidth: 120 }}
					/>
					<TextField
						label="Max words"
						value={draft.maxWords}
						onChange={set('maxWords')}
						size="small"
						type="number"
						slotProps={{ htmlInput: { min: 0 } }}
						sx={{ maxWidth: 120 }}
					/>
					<TextField
						label="Sort"
						value={draft.sort}
						onChange={set('sort')}
						size="small"
						select
						sx={{ minWidth: 150 }}
					>
						{SORTS.map(s => <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>)}
					</TextField>
					<Stack direction="row" spacing={1}>
						<Button variant="contained" onClick={() => setApplied(toApplied(draft))}>Apply</Button>
						<Button onClick={() => { setDraft(EMPTY); setApplied(toApplied(EMPTY)) }}>Clear</Button>
					</Stack>
				</Stack>
			</Paper>

			{query.isLoading ? (
				<Stack sx={{ alignItems: 'center', pt: 4 }}><CircularProgress /></Stack>
			) : query.isError ? (
				<Alert severity="error">
					{query.error?.response?.data?.message ?? 'Could not load the queue.'}
				</Alert>
			) : entries.length === 0 ? (
				<Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Nothing in the queue right now</Typography>
					<Typography variant="body2" color="text.secondary">
						No open requests match. Try clearing the filters, or check back later.
					</Typography>
				</Paper>
			) : (
				<Stack spacing={1.5}>
					{entries.map(e => (
						<QueueEntryCard key={e.id} entry={e} onOpen={(x) => setOpenId(x.id)} />
					))}
					{query.hasNextPage && (
						<Box sx={{ textAlign: 'center', pt: 1 }}>
							<Button onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
								{query.isFetchingNextPage ? 'Loading…' : 'Load more'}
							</Button>
						</Box>
					)}
				</Stack>
			)}

			<ReviewPackageDialog open={!!openId} requestId={openId} onClose={() => setOpenId(null)} />
		</Box>
	)
}
