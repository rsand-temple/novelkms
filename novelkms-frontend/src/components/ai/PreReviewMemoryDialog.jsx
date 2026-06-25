import { useState } from 'react'
import {
	Alert,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	List,
	ListItem,
	ListItemText,
	Stack,
	Typography,
} from '@mui/material'
import { useGenerateChapterMemory } from '../../hooks/useChapterMemory'
import { stateColor, stateExplanation, stateLabel } from './memoryStatus'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Generation failed.'
}

/**
 * PreReviewMemoryDialog
 *
 * Shown before a CHAPTER review when one or more PRECEDING chapters have a
 * memory document that is missing, stale, or out of sequence — meaning the
 * "story so far" context for this review would be incomplete or behind.
 *
 * The author can: cancel; review anyway (use the context as-is); or regenerate
 * the flagged preceding documents in book order and then review. Regeneration
 * runs sequentially with progress, and proceeds to the review only if all
 * succeed.
 *
 * Props:
 *   open          {boolean}
 *   onCancel      {() => void}
 *   onProceed     {() => void}        runs the review (parent closes + runs)
 *   flagged       {Array<{chapterId, chapterNumber, title, state}>} in book order
 *   bookId        {string}
 *   credentialId  {string|null}
 */
export default function PreReviewMemoryDialog({
	open, onCancel, onProceed, flagged = [], bookId, credentialId = null,
	title = 'Some earlier chapters’ memory is out of date',
	intro = 'This review uses the preceding chapters’ memory documents as “story so far” context.',
	proceedLabel = 'Review anyway',
	regenerateLabel = 'Regenerate flagged & review',
}) {
	const { mutateAsync: generateAsync } = useGenerateChapterMemory()
	const [regenerating, setRegenerating] = useState(false)
	const [doneCount, setDoneCount] = useState(0)
	const [errorMsg, setErrorMsg] = useState(null)

	const total = flagged.length

	const handleRegenerateAndReview = async () => {
		setErrorMsg(null)
		setRegenerating(true)
		setDoneCount(0)
		try {
			for (let i = 0; i < flagged.length; i++) {
				await generateAsync({ chapterId: flagged[i].chapterId, bookId, credentialId })
				setDoneCount(i + 1)
			}
			setRegenerating(false)
			onProceed()
		} catch (e) {
			setRegenerating(false)
			setErrorMsg(errMessage(e))
		}
	}

	const chapterName = (f) =>
		f.title?.trim() ? `Chapter ${f.chapterNumber}: ${f.title.trim()}` : `Chapter ${f.chapterNumber}`

	return (
		<Dialog open={open} onClose={() => { if (!regenerating) onCancel() }} maxWidth="sm" fullWidth>
			<DialogTitle>{title}</DialogTitle>
			<DialogContent dividers>
				<Typography variant="body2" sx={{ mb: 1 }}>
					{intro}{` ${total} `}of them {total === 1 ? 'is' : 'are'} missing or behind:
				</Typography>

				<List dense sx={{ mb: 1 }}>
					{flagged.map((f) => (
						<ListItem key={f.chapterId} disableGutters
							secondaryAction={
								<Chip size="small" color={stateColor(f.state)} variant="outlined" label={stateLabel(f.state)} />
							}
						>
							<ListItemText
								primary={chapterName(f)}
								secondary={stateExplanation(f.state)}
								primaryTypographyProps={{ variant: 'body2' }}
								secondaryTypographyProps={{ variant: 'caption' }}
							/>
						</ListItem>
					))}
				</List>

				{regenerating && (
					<Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
						<CircularProgress size={16} />
						<Typography variant="body2">Regenerating {Math.min(doneCount + 1, total)} of {total}…</Typography>
					</Stack>
				)}

				{errorMsg && <Alert severity="error">{errorMsg}</Alert>}
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel} disabled={regenerating}>Cancel</Button>
				<Button onClick={onProceed} disabled={regenerating}>{proceedLabel}</Button>
				<Button
					variant="contained"
					onClick={handleRegenerateAndReview}
					disabled={regenerating}
					startIcon={regenerating ? <CircularProgress size={16} color="inherit" /> : null}
				>
					{regenerating ? 'Working…' : regenerateLabel}
				</Button>
			</DialogActions>
		</Dialog>
	)
}
