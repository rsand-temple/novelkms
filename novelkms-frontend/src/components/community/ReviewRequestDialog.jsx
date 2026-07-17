import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import { FEEDBACK_TYPES } from '../../utils/reviewFeedbackTypes'
import { useMyReviewProfile } from '../../hooks/useReviewProfile'
import {
	useReviewRequest,
	usePublishReviewRequest,
	useUpdateReviewRequest,
} from '../../hooks/useReviewRequests'

// Instant (ISO string) <-> <input type="datetime-local"> value (local wall time,
// no zone). The backend stores an Instant; the input speaks local time.
function toLocalInput(iso) {
	if (!iso) return ''
	const d = new Date(iso)
	if (Number.isNaN(d.getTime())) return ''
	const pad = (n) => String(n).padStart(2, '0')
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromLocalInput(value) {
	if (!value) return null
	const d = new Date(value)
	return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

function parseMaxReviews(raw) {
	const n = parseInt(raw, 10)
	return Number.isFinite(n) && n > 0 ? n : null
}

/**
 * Publish a chapter for human review, or edit an existing request — one field set
 * for both. Visibility is deliberately not exposed: the backend defaults to
 * PUBLIC, and INVITE would produce a package no reviewer can reach until private
 * invitations exist (Phase 2).
 *
 * Props:
 *   mode           'publish' | 'edit'
 *   chapterId      (publish) the source chapter
 *   suggestedTitle (publish) the chapter title, used as the placeholder/default
 *   requestId      (edit) the request to load and rewrite
 */
export default function ReviewRequestDialog({
	open,
	onClose,
	mode,
	chapterId,
	suggestedTitle,
	requestId,
	onPublished,
	onSaved,
}) {
	const editing = mode === 'edit'
	const { data: profile, isLoading: profileLoading } = useMyReviewProfile()

	// Edit mode loads the full request (the summary omits three fields PUT rewrites).
	const {
		data: fullRequest,
		isLoading: requestLoading,
		isError: requestError,
		error: requestErr,
	} = useReviewRequest(requestId, open && editing)

	const ready = !profileLoading && (!editing || (!requestLoading && !!fullRequest))

	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle>{editing ? 'Edit review request' : 'Publish for human review'}</DialogTitle>

			{profileLoading ? (
				<Loading />
			) : !profile ? (
				<ProfileGate onClose={onClose} />
			) : editing && requestError ? (
				<>
					<DialogContent dividers>
						<Alert severity="error">
							{requestErr?.response?.data?.message ?? 'Could not load this request.'}
						</Alert>
					</DialogContent>
					<DialogActions>
						<Button onClick={onClose}>Close</Button>
					</DialogActions>
				</>
			) : !ready ? (
				<Loading />
			) : (
				<RequestFormBody
					key={editing ? requestId : `publish:${chapterId}`}
					editing={editing}
					chapterId={chapterId}
					requestId={requestId}
					initial={editing ? fullRequest : { title: suggestedTitle ?? '' }}
					onClose={onClose}
					onPublished={onPublished}
					onSaved={onSaved}
				/>
			)}
		</Dialog>
	)
}

function Loading() {
	return (
		<DialogContent dividers>
			<Stack sx={{ alignItems: 'center', py: 4 }}>
				<CircularProgress />
			</Stack>
		</DialogContent>
	)
}

function ProfileGate({ onClose }) {
	return (
		<>
			<DialogContent dividers>
				<Typography variant="body2">
					Claim a handle before publishing for review. Your handle is your public identity in the
					review community, and it is the gate for taking part.
				</Typography>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Not now</Button>
				<Button variant="contained" onClick={() => { window.location.href = '/app/community' }}>
					Go to My Profile
				</Button>
			</DialogActions>
		</>
	)
}

/**
 * The form itself. Mounted only once its data has settled and keyed on the
 * request (or chapter) id, so the useState initializers below always see the real
 * values — they run on first mount only, and a remount is the sanctioned way to
 * reseed them.
 */
function RequestFormBody({ editing, chapterId, requestId, initial, onClose, onPublished, onSaved }) {
	const [title, setTitle] = useState(initial.title ?? '')
	const [description, setDescription] = useState(initial.description ?? '')
	const [authorQuestions, setAuthorQuestions] = useState(initial.authorQuestions ?? '')
	const [genre, setGenre] = useState(initial.genre ?? '')
	const [feedbackTypes, setFeedbackTypes] = useState(initial.feedbackTypes ?? [])
	const [contentWarnings, setContentWarnings] = useState(initial.contentWarnings ?? '')
	const [maxReviews, setMaxReviews] = useState(initial.maxReviews != null ? String(initial.maxReviews) : '')
	const [closesAt, setClosesAt] = useState(toLocalInput(initial.closesAt))
	const [error, setError] = useState(null)

	const publish = usePublishReviewRequest()
	const update = useUpdateReviewRequest()
	const pending = publish.isPending || update.isPending

	const toggleType = (key) =>
		setFeedbackTypes((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]))

	// Publish falls back to the chapter title when blank; edit rewrites everything
	// and the backend requires a title, so only edit forces one here.
	const titleRequired = editing
	const canSubmit = !pending && (!titleRequired || title.trim().length > 0)

	const submit = () => {
		setError(null)
		const body = {
			title: title.trim(),
			description: description.trim() || null,
			authorQuestions: authorQuestions.trim() || null,
			genre: genre.trim() || null,
			feedbackTypes,
			contentWarnings: contentWarnings.trim() || null,
			maxReviews: parseMaxReviews(maxReviews),
			closesAt: fromLocalInput(closesAt),
		}
		const onErr = (e) =>
			setError(e?.response?.data?.message ?? e?.message ?? 'Something went wrong.')

		if (editing) {
			update.mutate({ id: requestId, body }, {
				onSuccess: (saved) => { onSaved?.(saved); onClose() },
				onError: onErr,
			})
		} else {
			publish.mutate({ chapterId, body }, {
				onSuccess: (created) => { onPublished?.(created); onClose() },
				onError: onErr,
			})
		}
	}

	return (
		<>
			<DialogContent dividers>
				<Stack spacing={2} sx={{ mt: 0.5 }}>
					<TextField
						label="Package title"
						value={title}
						onChange={(e) => setTitle(e.target.value)}
						fullWidth
						required={titleRequired}
						placeholder={editing ? undefined : (initial.title || 'Uses the chapter title if left blank')}
						helperText={editing ? undefined : 'Leave blank to use the chapter title.'}
					/>

					<TextField
						label="What are you sharing?"
						value={description}
						onChange={(e) => setDescription(e.target.value)}
						fullWidth
						multiline
						minRows={2}
						placeholder="A short description reviewers see in the queue."
					/>

					<TextField
						label="Specific questions for reviewers"
						value={authorQuestions}
						onChange={(e) => setAuthorQuestions(e.target.value)}
						fullWidth
						multiline
						minRows={2}
						placeholder="Anything you especially want feedback on."
					/>

					<TextField
						label="Genre"
						value={genre}
						onChange={(e) => setGenre(e.target.value)}
						fullWidth
					/>

					<Box>
						<Typography variant="body2" sx={{ mb: 1, fontWeight: 600 }}>
							Feedback wanted
						</Typography>
						<Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
							{FEEDBACK_TYPES.map((t) => {
								const on = feedbackTypes.includes(t.key)
								return (
									<Chip
										key={t.key}
										label={t.label}
										size="small"
										color={on ? 'primary' : 'default'}
										variant={on ? 'filled' : 'outlined'}
										onClick={() => toggleType(t.key)}
									/>
								)
							})}
						</Box>
					</Box>

					<TextField
						label="Content warnings"
						value={contentWarnings}
						onChange={(e) => setContentWarnings(e.target.value)}
						fullWidth
						multiline
						minRows={1}
						placeholder="Optional. Shown before anyone opens the package."
					/>

					<Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
						<TextField
							label="Max reviews"
							value={maxReviews}
							onChange={(e) => setMaxReviews(e.target.value)}
							type="number"
							slotProps={{ htmlInput: { min: 1 } }}
							sx={{ maxWidth: 160 }}
							helperText="Optional cap."
						/>
						<TextField
							label="Closes at"
							value={closesAt}
							onChange={(e) => setClosesAt(e.target.value)}
							type="datetime-local"
							slotProps={{ inputLabel: { shrink: true } }}
							helperText="Optional. Advisory."
						/>
					</Stack>

					{error && <Alert severity="error">{error}</Alert>}
				</Stack>
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose} disabled={pending}>Cancel</Button>
				<Button
					variant="contained"
					onClick={submit}
					disabled={!canSubmit}
					startIcon={pending ? <CircularProgress size={16} color="inherit" /> : null}
				>
					{editing ? (pending ? 'Saving…' : 'Save') : (pending ? 'Publishing…' : 'Publish')}
				</Button>
			</DialogActions>
		</>
	)
}
