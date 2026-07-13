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
	useHandleAvailability,
	useCreateReviewProfile,
	useUpdateReviewProfile,
} from '../../hooks/useReviewProfile'

const MAX_BIO = 2000

// The genre columns are one comma-separated string on the wire's far side, so a
// comma inside a value would silently split it in two. The backend rejects that;
// splitting on commas here means the user can never produce one.
const splitGenres = (raw) =>
	raw.split(',').map(g => g.trim()).filter(Boolean).slice(0, 12)

const joinGenres = (genres) => (genres ?? []).join(', ')

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
		</Stack>
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
