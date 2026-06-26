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
import { useGenerateChapterSummary } from '../../hooks/useSummary'
import { stateColor, stateExplanation, stateLabel } from './summaryStatus'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Generation failed.'
}

/**
 * PreBookSummaryDialog
 *
 * Shown before generating the BOOK summary when one or more chapters have a
 * summary that is missing or stale — meaning the book summary would be built
 * from incomplete or out-of-date input.
 *
 * The author can: cancel; generate the book summary anyway (use what exists); or
 * fill the gaps first — generate the flagged chapter summaries in book order,
 * then proceed to the book summary. Filling runs sequentially with progress and
 * proceeds only if all succeed.
 *
 * Props:
 *   open          {boolean}
 *   onCancel      {() => void}
 *   onProceed     {() => void}        runs the book-summary generation
 *   flagged       {Array<{chapterId, chapterNumber, title, state}>} in book order
 *   bookId        {string}
 *   credentialId  {string|null}
 */
export default function PreBookSummaryDialog({ open, onCancel, onProceed, flagged = [], bookId, credentialId = null }) {
	const { mutateAsync: generateAsync } = useGenerateChapterSummary()
	const [working, setWorking] = useState(false)
	const [doneCount, setDoneCount] = useState(0)
	const [errorMsg, setErrorMsg] = useState(null)

	const total = flagged.length

	const handleFillAndContinue = async () => {
		setErrorMsg(null)
		setWorking(true)
		setDoneCount(0)
		try {
			for (let i = 0; i < flagged.length; i++) {
				await generateAsync({ chapterId: flagged[i].chapterId, bookId, credentialId })
				setDoneCount(i + 1)
			}
			setWorking(false)
			onProceed()
		} catch (e) {
			setWorking(false)
			setErrorMsg(errMessage(e))
		}
	}

	const chapterName = (f) =>
		f.title?.trim() ? `Chapter ${f.chapterNumber}: ${f.title.trim()}` : `Chapter ${f.chapterNumber}`

	return (
		<Dialog open={open} onClose={() => { if (!working) onCancel() }} maxWidth="sm" fullWidth>
			<DialogTitle>Some chapter summaries are missing or out of date</DialogTitle>
			<DialogContent dividers>
				<Typography variant="body2" sx={{ mb: 1 }}>
					The book summary is built entirely from the chapter summaries.{` ${total} `}
					of them {total === 1 ? 'is' : 'are'} missing or behind:
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

				{working && (
					<Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
						<CircularProgress size={16} />
						<Typography variant="body2">Generating {Math.min(doneCount + 1, total)} of {total}…</Typography>
					</Stack>
				)}

				{errorMsg && <Alert severity="error">{errorMsg}</Alert>}
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel} disabled={working}>Cancel</Button>
				<Button onClick={onProceed} disabled={working}>Generate anyway</Button>
				<Button
					variant="contained"
					onClick={handleFillAndContinue}
					disabled={working}
					startIcon={working ? <CircularProgress size={16} color="inherit" /> : null}
				>
					{working ? 'Working…' : 'Generate missing & continue'}
				</Button>
			</DialogActions>
		</Dialog>
	)
}
