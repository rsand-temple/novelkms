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
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import {
	useBookSummary,
	useBookSummaryStatus,
	useBookChapterSummaries,
	useGenerateBookSummary,
} from '../../hooks/useSummary'
import { isFlagged, stateColor, stateExplanation, stateLabel, formatTime, flaggedChapters } from './summaryStatus'
import PreBookSummaryDialog from './PreBookSummaryDialog'
import RegenerateConfirmDialog from './RegenerateConfirmDialog'
import RichTextPreview from './RichTextPreview'
import { stripHtmlToText } from '../../utils/htmlText'
import { HelpButton } from '../../help'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * BookSummaryDialog
 *
 * Opened from the book nav context menu ("View chapter summaries"). A
 * read-only dashboard kept alongside the book's own Summary nav node (the real
 * editing surface, opened in EditorPanel for full rich-text editing). Two
 * stacked sections:
 *
 *  1. Book summary — the whole-book synopsis (<= ~1000 words) built entirely from
 *     the chapter summaries, rendered read-only here. Generate/Regenerate, with a
 *     staleness chip, coverage line, and a link to jump to the nav node to
 *     hand-edit. Generating is gated by PreBookSummaryDialog when chapters are
 *     missing or stale, and by a discard-content warning when regenerating.
 *  2. Chapter summaries — the read-only aggregate, every chapter in book order
 *     with a plain-text preview of its summary paragraph and a per-chapter state
 *     chip (rendered as plain text rather than the rich markup — this is a dense
 *     glance list, not an editing surface).
 *
 * Props:
 *   open             {boolean}
 *   onClose          {() => void}
 *   bookId           {string}
 *   title            {string|undefined}  book title, for the heading
 *   onEditInDocument {() => void}        selects the book's Summary nav node
 */
export default function BookSummaryDialog({ open, onClose, bookId, title, onEditInDocument }) {
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

	const [errorMsg, setErrorMsg] = useState(null)
	const [gateOpen, setGateOpen] = useState(false)
	const [regenConfirmOpen, setRegenConfirmOpen] = useState(false)

	// One-time guidance for the next generation, pre-filled from whatever was
	// used last time (stored on the book summary itself) so the author can tweak
	// and re-run without retyping. Re-derived on book switch and once the
	// summary finishes loading — never auto-cleared after a successful run.
	const [guidance, setGuidance] = useState('')
	const [guidanceInitKey, setGuidanceInitKey] = useState(null)
	const currentInitKey = `${bookId}:${bookLoading ? 'loading' : 'loaded'}`
	if (currentInitKey !== guidanceInitKey) {
		setGuidanceInitKey(currentInitKey)
		setGuidance(book?.userGuidance ?? '')
	}

	const flagged = flaggedChapters(rows)
	const busy = generating

	const doGenerate = () => {
		setErrorMsg(null)
		generate(
			{ bookId, credentialId: defaultCredentialId, userGuidance: guidance.trim() || null },
			{ onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	// Warn first if any chapter summary is missing or stale (the book summary
	// would otherwise be built from incomplete input).
	const proceedToGate = () => {
		if (flagged.length > 0) { setGateOpen(true); return }
		doGenerate()
	}

	// Regenerating discards whatever is currently there (content + formatting);
	// a first-ever Generate has nothing to lose, so it skips straight to the gate.
	const handleGenerate = () => {
		if (book) { setRegenConfirmOpen(true); return }
		proceedToGate()
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
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				Summary{title?.trim() ? ` — ${title.trim()}` : ''}
				<Box sx={{ flex: 1 }} />
				<HelpButton topic="ai.summaries" />
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

				{hasCredentials && (
					<Box sx={{ mb: 1.5 }}>
						<TextField
							label="Guidance for this generation (optional)"
							placeholder="e.g. emphasize the reveal in Chapter 12 as the emotional climax"
							value={guidance}
							onChange={(e) => setGuidance(e.target.value)}
							fullWidth
							multiline
							minRows={2}
							maxRows={6}
							size="small"
							disabled={busy}
						/>
						{guidance.trim() && (
							<Button size="small" sx={{ mt: 0.5 }} onClick={() => setGuidance('')} disabled={busy}>
								Clear guidance
							</Button>
						)}
					</Box>
				)}

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

						<Box sx={{ p: 1.5, mb: 1, border: '1px solid', borderColor: 'divider', borderRadius: 1, maxHeight: 320, overflowY: 'auto' }}>
							<RichTextPreview html={book.content} />
						</Box>

						<Stack direction="row" spacing={1} justifyContent="flex-end">
							<Button startIcon={<EditOutlinedIcon fontSize="small" />} onClick={onEditInDocument} disabled={busy}>
								Edit in document
							</Button>
							<Button
								variant="outlined"
								onClick={handleGenerate}
								disabled={!hasCredentials || busy}
								startIcon={generating ? <CircularProgress size={16} color="inherit" /> : null}
							>
								{generating ? 'Generating…' : 'Regenerate'}
							</Button>
						</Stack>
					</Box>
				)}

				<Divider sx={{ my: 2 }} />

				{/* ── Chapter summaries (read-only aggregate, plain-text preview) ── */}
				<Typography variant="subtitle1" sx={{ mb: 1 }}>Chapter summaries</Typography>

				{rowsLoading ? (
					<Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
				) : rows.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						This book has no chapters yet.
					</Typography>
				) : (
					<Stack spacing={2}>
						{rows.map((r) => {
							const preview = stripHtmlToText(r.content)
							return (
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
									{preview ? (
										<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
											{preview}
										</Typography>
									) : (
										<Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
											No summary yet — generate one from this chapter’s context menu, or its Summary entry in the nav tree.
										</Typography>
									)}
								</Box>
							)
						})}
					</Stack>
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>

			<RegenerateConfirmDialog
				open={regenConfirmOpen}
				onCancel={() => setRegenConfirmOpen(false)}
				onConfirm={() => { setRegenConfirmOpen(false); proceedToGate() }}
			/>

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
