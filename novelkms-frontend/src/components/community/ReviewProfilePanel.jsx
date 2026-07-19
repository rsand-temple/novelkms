import { useEffect, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Divider,
	FormControlLabel,
	InputAdornment,
	Paper,
	Stack,
	Switch,
	TextField,
	Typography,
} from '@mui/material'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import {
	useMyReviewProfile,
	useMyReviewProfileMetrics,
	useHandleAvailability,
	useCreateReviewProfile,
	useUpdateReviewProfile,
} from '../../hooks/useReviewProfile'
import { useMyBlocks, useUnblockUser } from '../../hooks/useReviewSafety'

const MAX_BIO = 2000

// The genre columns are one comma-separated string on the wire's far side, so a
// comma inside a value would silently split it in two. The backend rejects that;
// splitting on commas here means the user can never produce one.
const splitGenres = (raw) =>
	raw.split(',').map(g => g.trim()).filter(Boolean).slice(0, 12)

const joinGenres = (genres) => (genres ?? []).join(', ')

const numberFormat = new Intl.NumberFormat()
const formatCount = (n) => numberFormat.format(Number(n ?? 0))

const monthYearFormat = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' })
function formatMemberSince(iso) {
	if (!iso) return null
	const date = new Date(iso)
	return Number.isNaN(date.getTime()) ? null : monthYearFormat.format(date)
}

/**
 * The signed-in user's contribution figures (§13). Shown only for an existing
 * profile — there is nothing to report before a handle is claimed. The figures
 * are objective totals, not a ranking, so the copy says so plainly and avoids
 * anything leaderboard-shaped.
 */
function ContributionMetrics() {
	const { data, isPending, isError } = useMyReviewProfileMetrics()

	// Absent quietly on first load or error: metrics are supplementary, and a
	// spinner or alert here would distract from the profile form below.
	if (isPending || isError || !data) return null

	const stats = [
		{ label: 'Words reviewed', value: data.wordsReviewed },
		{ label: 'Review words written', value: data.reviewWordsWritten },
		{ label: 'Reviews completed', value: data.reviewsCompleted },
		{ label: 'Reviews received', value: data.reviewsReceived },
	]
	const memberSince = formatMemberSince(data.memberSince)

	return (
		<Paper variant="outlined" sx={{ p: 2.5 }}>
			<Stack spacing={2}>
				<Box>
					<Typography variant="overline" color="text.secondary">
						Contribution
					</Typography>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
						From reviews you have submitted and received. These show participation, not a ranking.
					</Typography>
				</Box>

				<Box
					sx={{
						display: 'grid',
						gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(4, 1fr)' },
						gap: 2,
					}}
				>
					{stats.map(s => (
						<Box key={s.label}>
							<Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
								{formatCount(s.value)}
							</Typography>
							<Typography variant="caption" color="text.secondary">
								{s.label}
							</Typography>
						</Box>
					))}
				</Box>

				{memberSince && (
					<Typography variant="caption" color="text.secondary">
						Member since {memberSince}
					</Typography>
				)}
			</Stack>
		</Paper>
	)
}

/** Trailing-edge debounce. A timer is a real side effect, not derived state. */
function useDebounced(value, delay = 350) {
	const [debounced, setDebounced] = useState(value)

	useEffect(() => {
		const timer = setTimeout(() => setDebounced(value), delay)
		return () => clearTimeout(timer)
	}, [value, delay])

	return debounced
}

/**
 * The form body. Mounted only once the profile query has settled, and keyed on
 * the profile id, so the useState initializers below always see the real row —
 * they run on first mount only, and a remount is the sanctioned way to reseed
 * them.
 */
function ProfileForm({ profile }) {
	const isNew = !profile

	const [handle, setHandle] = useState(profile?.handle ?? '')
	const [displayName, setDisplayName] = useState(profile?.displayName ?? '')
	const [bio, setBio] = useState(profile?.bio ?? '')
	const [genresWritten, setGenresWritten] = useState(joinGenres(profile?.genresWritten))
	const [genresReviewed, setGenresReviewed] = useState(joinGenres(profile?.genresReviewed))
	const [isPublic, setIsPublic] = useState((profile?.visibility ?? 'PUBLIC') === 'PUBLIC')
	const [saved, setSaved] = useState(false)

	const create = useCreateReviewProfile()
	const update = useUpdateReviewProfile()
	const saving = create.isPending || update.isPending

	const debouncedHandle = useDebounced(handle.trim())
	const unchangedHandle = !isNew && debouncedHandle === profile.handle
	const availability = useHandleAvailability(
		debouncedHandle,
		debouncedHandle.length > 0 && !unchangedHandle
	)

	const handleState = (() => {
		if (!debouncedHandle) return null
		if (unchangedHandle) return { ok: true, message: 'This is your current handle.' }
		if (availability.isPending || availability.isFetching) return { pending: true }
		if (availability.isError) return null
		const data = availability.data
		if (!data) return null
		return data.available
			? { ok: true, message: `${debouncedHandle} is available.` }
			: { ok: false, message: data.message || 'That handle cannot be used.' }
	})()

	const canSave =
		!saving &&
		handle.trim().length > 0 &&
		bio.length <= MAX_BIO &&
		handleState?.ok === true

	const mutationError = create.error ?? update.error
	const errorMessage = mutationError
		? (mutationError.response?.data?.message ?? 'Could not save your profile.')
		: null

	function handleSave() {
		setSaved(false)

		const body = {
			handle: handle.trim(),
			displayName: displayName.trim() || null,
			bio: bio.trim() || null,
			genresWritten: splitGenres(genresWritten),
			genresReviewed: splitGenres(genresReviewed),
			visibility: isPublic ? 'PUBLIC' : 'HIDDEN',
		}

		const mutation = isNew ? create : update
		mutation.mutate(body, { onSuccess: () => setSaved(true) })
	}

	return (
		<Stack spacing={2.5} sx={{ maxWidth: 720 }}>
			<Box>
				<Typography variant="h6" sx={{ fontWeight: 700 }}>
					{isNew ? 'Claim your handle' : 'Your reviewer profile'}
				</Typography>
				<Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
					{isNew
						? 'A handle is how other writers see you in the review network. You need one before you can request a review or write one.'
						: 'This is what other writers see. Your email address and login identity are never shown.'}
				</Typography>
			</Box>

			{!isNew && <ContributionMetrics />}

			{errorMessage && <Alert severity="error">{errorMessage}</Alert>}
			{saved && !errorMessage && (
				<Alert severity="success" onClose={() => setSaved(false)}>
					{isNew ? 'Profile created.' : 'Profile saved.'}
				</Alert>
			)}

			<Paper variant="outlined" sx={{ p: 2.5 }}>
				<Stack spacing={2.25}>
					<TextField
						label="Handle"
						value={handle}
						onChange={(e) => setHandle(e.target.value)}
						fullWidth
						required
						autoComplete="off"
						error={handleState?.ok === false}
						helperText={
							handleState?.pending
								? 'Checking…'
								: handleState?.message
									?? '3–24 characters. Starts with a letter; letters, numbers, and underscores only.'
						}
						slotProps={{
							input: {
								startAdornment: <InputAdornment position="start">@</InputAdornment>,
								endAdornment: handleState?.pending
									? <InputAdornment position="end"><CircularProgress size={16} /></InputAdornment>
									: handleState?.ok === true
										? <InputAdornment position="end"><CheckCircleOutlinedIcon color="success" fontSize="small" /></InputAdornment>
										: handleState?.ok === false
											? <InputAdornment position="end"><WarningAmberIcon color="error" fontSize="small" /></InputAdornment>
											: null,
							},
						}}
					/>

					<TextField
						label="Display name (optional)"
						value={displayName}
						onChange={(e) => setDisplayName(e.target.value)}
						fullWidth
						helperText="Shown alongside your handle. Leave blank to go by your handle alone."
					/>

					<TextField
						label="Short bio (optional)"
						value={bio}
						onChange={(e) => setBio(e.target.value)}
						fullWidth
						multiline
						minRows={3}
						error={bio.length > MAX_BIO}
						helperText={`${bio.length} / ${MAX_BIO}`}
					/>
				</Stack>
			</Paper>

			<Paper variant="outlined" sx={{ p: 2.5 }}>
				<Stack spacing={2.25}>
					<Typography variant="overline" color="text.secondary">
						Genres
					</Typography>

					<TextField
						label="Genres I write"
						value={genresWritten}
						onChange={(e) => setGenresWritten(e.target.value)}
						fullWidth
						helperText="Comma-separated. For example: literary, historical, mystery"
					/>
					<GenrePreview raw={genresWritten} />

					<TextField
						label="Genres I'm happy to review"
						value={genresReviewed}
						onChange={(e) => setGenresReviewed(e.target.value)}
						fullWidth
						helperText="Comma-separated. This helps other writers find a reviewer who fits their book."
					/>
					<GenrePreview raw={genresReviewed} />
				</Stack>
			</Paper>

			<Paper variant="outlined" sx={{ p: 2.5 }}>
				<FormControlLabel
					control={
						<Switch
							checked={isPublic}
							onChange={(e) => setIsPublic(e.target.checked)}
						/>
					}
					label={
						<Box>
							<Typography variant="body2" sx={{ fontWeight: 600 }}>
								Visible to other writers
							</Typography>
							<Typography variant="caption" color="text.secondary">
								When this is off, your profile is hidden. You can still read the queue, but
								other writers cannot look you up.
							</Typography>
						</Box>
					}
				/>
			</Paper>

			<Divider />

			<Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
				<Button
					variant="contained"
					onClick={handleSave}
					disabled={!canSave}
					startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
				>
					{isNew ? 'Claim handle' : 'Save profile'}
				</Button>
				{!isNew && (
					<Typography variant="caption" color="text.secondary">
						Changing your handle updates it everywhere. Existing reviews stay attributed to you.
					</Typography>
				)}
			</Stack>

			{!isNew && <Divider />}
			{!isNew && <BlockedUsers />}
		</Stack>
	)
}

/**
 * The signed-in user's block list (slice 1F), self only. A blocked writer's
 * requests drop out of your queue and their reviews out of your received/writing
 * lists, in both directions — this section is where you see who that is and lift a
 * block. Only mounted for an existing profile, so the underlying endpoint never hits
 * its handle-less 409.
 */
function BlockedUsers() {
	const { data: blocks, isPending, isError, error } = useMyBlocks(true)
	const unblock = useUnblockUser()

	const title = (
		<Typography variant="overline" color="text.secondary">Blocked writers</Typography>
	)

	if (isPending) {
		return (
			<Paper variant="outlined" sx={{ p: 2.5 }}>
				<Stack spacing={1.5}>
					{title}
					<Stack sx={{ alignItems: 'center', py: 1 }}><CircularProgress size={22} /></Stack>
				</Stack>
			</Paper>
		)
	}

	if (isError) {
		return (
			<Paper variant="outlined" sx={{ p: 2.5 }}>
				<Stack spacing={1.5}>
					{title}
					<Alert severity="error">
						{error?.response?.data?.message ?? 'Could not load your blocked writers.'}
					</Alert>
				</Stack>
			</Paper>
		)
	}

	const list = blocks ?? []

	return (
		<Paper variant="outlined" sx={{ p: 2.5 }}>
			<Stack spacing={1.5}>
				{title}
				{list.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						You haven't blocked anyone. Blocking a writer hides their requests and reviews
						from you, both ways. You can block someone from the ⋯ menu on their card.
					</Typography>
				) : (
					<Stack spacing={1}>
						{list.map(b => (
							<Stack
								key={b.handle}
								direction="row"
								spacing={1}
								sx={{ alignItems: 'center' }}
							>
								<Box sx={{ flexGrow: 1, minWidth: 0 }}>
									<Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
										@{b.handle}{b.displayName ? ` (${b.displayName})` : ''}
									</Typography>
									{b.blockedAt && (
										<Typography variant="caption" color="text.secondary">
											Blocked {new Date(b.blockedAt).toLocaleDateString()}
										</Typography>
									)}
								</Box>
								<Button
									size="small"
									disabled={unblock.isPending}
									onClick={() => unblock.mutate(b.handle)}
								>
									Unblock
								</Button>
							</Stack>
						))}
					</Stack>
				)}
			</Stack>
		</Paper>
	)
}

function GenrePreview({ raw }) {
	const genres = splitGenres(raw)
	if (genres.length === 0) return null

	return (
		<Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', gap: 0.75, mt: -1 }}>
			{genres.map(genre => (
				<Chip key={genre} label={genre} size="small" variant="outlined" />
			))}
		</Stack>
	)
}

export default function ReviewProfilePanel() {
	const { data: profile, isPending, isError, error } = useMyReviewProfile()

	if (isPending) {
		return (
			<Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
				<CircularProgress />
			</Box>
		)
	}

	if (isError) {
		return (
			<Alert severity="error">
				{error?.response?.data?.message ?? 'Could not load your reviewer profile.'}
			</Alert>
		)
	}

	// Remount on identity change so the form's useState initializers reseed.
	return <ProfileForm key={profile?.id ?? 'new'} profile={profile ?? null} />
}
