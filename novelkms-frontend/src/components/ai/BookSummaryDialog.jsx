import { useMemo, useState } from 'react'
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
	Divider,
	Stack,
	TextField,
	Tooltip,
	Typography,
} from '@mui/material'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import {
	useBookSummary,
	useBookSummaryStatus,
	useBookChapterSummaries,
	useGenerateBookSummary,
	useSaveBookSummary,
} from '../../hooks/useSummary'
import { isFlagged, stateColor, stateExplanation, stateLabel, formatTime, flaggedChapters } from './summaryStatus'
import PreBookSummaryDialog from './PreBookSummaryDialog'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * BookSummaryDialog
 *
 * Opened from the book nav context menu ("View chapter summaries"). Two stacked
 * sections:
 *
 *  1. Book summary — the whole-book synopsis (<= ~1000 words) built entirely from
 *     the chapter summaries. View / Generate / Regenerate / hand-edit, with a
 *     staleness chip and coverage line. Generating is gated by
 *     PreBookSummaryDialog when chapters are missing or stale.
 *  2. Chapter summaries — the read-only aggregate, every chapter in book order
 *     with its summary paragraph and a per-chapter state chip.
 *
 * Props:
 *   open     {boolean}
 *   onClose  {() => void}
 *   bookId   {string}
 *   title    {string|undefined}  book title, for the heading
 */
export default function BookSummaryDialog({ open, onClose, bookId, title }) {
	const { data: credentials = [] } = useAiCredentials()
	const defaultCredentialId = useMemo(
		() => credentials.find(c => c.defaultCredential)?.id ?? credentials[0]?.id ?? null,
		[credentials],
	)
	const hasCredentials = credentials.length > 0

	const { data: book, isLoading: bookLoading } = useBookSummary(bookId, open && !!bookId)
	const { data: status } = useBookSummaryStatus(bookId, open && !!bookId)
	const { data: rows = [], isLoading: rowsLoading } = useBookChapterSummaries(bookId, open && !!bookId)

	const { mutate: generate, isPending: generating } = useGenerateBookSummary()
	const { mutate: save, isPending: saving } = useSaveBookSummary()

	const [editing, setEditing] = useState(false)
	const [text, setText] = useState('')
	const [errorMsg, setErrorMsg] = useState(null)
	const [gateOpen, setGateOpen] = useState(false)

	const flagged = flaggedChapters(rows)
	const busy = generating || saving

	const doGenerate = () => {
		setErrorMsg(null)
		setEditing(false)
		generate(
			{ bookId, credentialId: defaultCredentialId },
			{ onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	// Warn first if any chapter summary is missing or stale (the book summary
	// would otherwise be built from incomplete input).
	const handleGenerate = () => {
		if (flagged.length > 0) { setGateOpen(true); return }
		doGenerate()
	}

	const startEdit = () => {
		setErrorMsg(null)
		setText(book?.content ?? '')
		setEditing(true)
	}

	const handleSave = () => {
		setErrorMsg(null)
		if (!text.trim()) {
			setErrorMsg('The book summary must not be blank.')
			return
		}
		save(
			{ bookId, content: text.trim() },
			{ onSuccess: () => setEditing(false), onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	const coverage = status
		? `${status.summarizedCount} of ${status.chapterCount} chapters summarized`
		+ (status.staleChapterCount > 0 ? ` · ${status.staleChapterCount} stale` : '')
		: null

	const bookState = !status ? null
		: !status.hasDoc ? 'MISSING'
		: status.stale ? 'STALE_CONTENT'
		: 'OK'

	const chapterName = (r) =>
		r.title?.trim() ? `Chapter ${r.chapterNumber}: ${r.title.trim()}` : `Chapter ${r.chapterNumber}`

	return (
		<Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
			<DialogTitle>
				Summary{title?.trim() ? ` — ${title.trim()}` : ''}
			</DialogTitle>
			<DialogContent dividers>
				{/* ── Book summary ─────────────────────────────────────────────── */}
				<Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
					<Typography variant="subtitle1">Book summary</Typography>
					{bookState && (
						<Tooltip title={
							bookState === 'OK' ? 'The book summary reflects the current chapter summaries.'
							: bookState === 'STALE_CONTENT' ? 'A chapter summary changed or is missing since the book summary was generated.'
							: 'No book summary yet.'
						}>
							<Chip
								size="small"
								color={stateColor(bookState)}
								variant={isFlagged(bookState) ? 'filled' : 'outlined'}
								label={bookState === 'MISSING' ? 'Not generated' : stateLabel(bookState)}
							/>
						</Tooltip>
					)}
				</Stack>

				<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
					A synopsis of the whole book (up to ~1000 words), generated entirely from the chapter
					summaries below.{coverage ? ` ${coverage}.` : ''}
				</Typography>

				{!hasCredentials && (
					<Alert severity="info" sx={{ mb: 1 }}>
						Add an AI key from Settings → AI to generate a book summary.
					</Alert>
				)}

				{errorMsg && <Alert severity="error" sx={{ mb: 1 }}>{errorMsg}</Alert>}

				{bookLoading ? (
					<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
				) : !book ? (
					<Box sx={{ mb: 2 }}>
						<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
							No book summary yet.
						</Typography>
						<Button
							variant="contained"
							size="small"
							onClick={handleGenerate}
							disabled={!hasCredentials || busy || (status && status.summarizedCount === 0)}
							startIcon={generating ? <CircularProgress size={16} color="inherit" /> : <AutoAwesomeIcon fontSize="small" />}
						>
							{generating ? 'Generating…' : 'Generate book summary'}
						</Button>
						{status && status.summarizedCount === 0 && (
							<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
								Generate at least one chapter summary first.
							</Typography>
						)}
					</Box>
				) : (
					<Box sx={{ mb: 2 }}>
						<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
							{book.source === 'EDITED' ? 'Edited' : 'Generated'}
							{book.generatedAt ? ` · ${formatTime(book.generatedAt)}` : ''}
							{typeof book.wordCount === 'number' ? ` · ${book.wordCount} words` : ''}
							{book.model ? ` · ${book.model}` : ''}
						</Typography>

						<TextField
							value={editing ? text : (book.content ?? '')}
							onChange={(e) => setText(e.target.value)}
							fullWidth
							multiline
							minRows={8}
							maxRows={20}
							size="small"
							InputProps={{ readOnly: !editing }}
							disabled={busy && editing}
						/>

						<Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ mt: 1 }}>
							{editing ? (
								<>
									<Button onClick={() => setEditing(false)} disabled={saving}>Cancel</Button>
									<Button variant="contained" onClick={handleSave} disabled={saving}>
										{saving ? 'Saving…' : 'Save'}
									</Button>
								</>
							) : (
								<>
									<Button onClick={startEdit} disabled={busy}>Edit</Button>
									<Button
										variant="outlined"
										onClick={handleGenerate}
										disabled={!hasCredentials || busy}
										startIcon={generating ? <CircularProgress size={16} color="inherit" /> : null}
									>
										{generating ? 'Generating…' : 'Regenerate'}
									</Button>
								</>
							)}
						</Stack>
					</Box>
				)}

				<Divider sx={{ my: 2 }} />

				{/* ── Chapter summaries (read-only aggregate) ──────────────────── */}
				<Typography variant="subtitle1" sx={{ mb: 1 }}>Chapter summaries</Typography>

				{rowsLoading ? (
					<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
				) : rows.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						This book has no chapters yet.
					</Typography>
				) : (
					<Stack spacing={2}>
						{rows.map((r) => (
							<Box key={r.chapterId}>
								<Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
									<Typography variant="subtitle2">{chapterName(r)}</Typography>
									<Tooltip title={stateExplanation(r.state)}>
										<Chip
											size="small"
											color={stateColor(r.state)}
											variant={isFlagged(r.state) ? 'filled' : 'outlined'}
											label={stateLabel(r.state)}
										/>
									</Tooltip>
								</Stack>
								{r.content?.trim() ? (
									<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
										{r.content.trim()}
									</Typography>
								) : (
									<Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
										No summary yet — generate one from this chapter’s context menu.
									</Typography>
								)}
							</Box>
						))}
					</Stack>
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>

			<PreBookSummaryDialog
				open={gateOpen}
				onCancel={() => setGateOpen(false)}
				onProceed={() => { setGateOpen(false); doGenerate() }}
				flagged={flagged}
				bookId={bookId}
				credentialId={defaultCredentialId}
			/>
		</Dialog>
	)
}
