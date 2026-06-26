import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Stack,
	TextField,
	Tooltip,
	Typography,
} from '@mui/material'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import {
	useChapterSummary,
	useBookChapterSummaries,
	useGenerateChapterSummary,
	useSaveChapterSummary,
} from '../../hooks/useSummary'
import { isFlagged, stateColor, stateExplanation, stateLabel, formatTime } from './summaryStatus'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * ChapterSummaryEditor — view, (re)generate, and hand-edit one chapter's
 * summary. Shared by the nav ChapterSummaryDialog (and reusable elsewhere).
 *
 * Unlike memory documents, chapter summaries are independent paragraphs, so
 * there is no preceding-chapter gating: generation is immediate.
 *
 * Props:
 *   chapterId     {string}
 *   bookId        {string|undefined}  enables the staleness chip + cache refresh
 *   credentialId  {string|null}       AI key for generation; null = default key
 */
export default function ChapterSummaryEditor({ chapterId, bookId, credentialId = null }) {
	const { data: credentials = [] } = useAiCredentials()
	const { data: summary, isLoading } = useChapterSummary(chapterId)
	const { data: rows = [] } = useBookChapterSummaries(bookId, !!bookId)

	const { mutate: generate, isPending: generating } = useGenerateChapterSummary()
	const { mutate: save, isPending: saving } = useSaveChapterSummary()

	const [editing, setEditing] = useState(false)
	const [text, setText] = useState('')
	const [errorMsg, setErrorMsg] = useState(null)

	// One-time guidance for the next generation, pre-filled from whatever was
	// used last time (stored on the summary itself) so the author can tweak and
	// re-run without retyping. Re-derived on chapter switch and once the summary
	// finishes loading — never auto-cleared after a successful run.
	const [guidance, setGuidance] = useState('')
	const [guidanceInitKey, setGuidanceInitKey] = useState(null)
	const currentInitKey = `${chapterId}:${isLoading ? 'loading' : 'loaded'}`
	if (currentInitKey !== guidanceInitKey) {
		setGuidanceInitKey(currentInitKey)
		setGuidance(summary?.userGuidance ?? '')
	}

	const hasCredentials = credentials.length > 0
	const state = rows.find(s => s.chapterId === chapterId)?.state
	const busy = generating || saving

	const handleGenerate = () => {
		setErrorMsg(null)
		setEditing(false)
		generate(
			{ chapterId, bookId, credentialId, userGuidance: guidance.trim() || null },
			{ onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	const startEdit = () => {
		setErrorMsg(null)
		setText(summary?.content ?? '')
		setEditing(true)
	}

	const handleSave = () => {
		setErrorMsg(null)
		if (!text.trim()) {
			setErrorMsg('The summary must not be blank.')
			return
		}
		save(
			{ chapterId, bookId, content: text.trim() },
			{ onSuccess: () => setEditing(false), onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	if (isLoading) {
		return <Box sx={{ py: 1 }}><CircularProgress size={18} /></Box>
	}

	return (
		<Box>
			<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
				A single readable paragraph summarizing this chapter. Chapter summaries feed the
				book summary, which is built entirely from them.
			</Typography>

			{!hasCredentials && (
				<Alert severity="info" sx={{ mb: 1 }}>
					Add an AI key from Settings → AI to generate summaries.
				</Alert>
			)}

			{state && (
				<Tooltip title={stateExplanation(state)}>
					<Chip
						size="small"
						color={stateColor(state)}
						variant={isFlagged(state) ? 'filled' : 'outlined'}
						label={stateLabel(state)}
						sx={{ mb: 1 }}
					/>
				</Tooltip>
			)}

			{errorMsg && <Alert severity="error" sx={{ mb: 1 }}>{errorMsg}</Alert>}

			{hasCredentials && (
				<Box sx={{ mb: 1.5 }}>
					<TextField
						label="Guidance for this generation (optional)"
						placeholder="e.g. the letter in this chapter is canonically a forgery"
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

			{!summary ? (
				<Box sx={{ mt: 1 }}>
					<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
						No summary yet.
					</Typography>
					<Button
						variant="contained"
						size="small"
						onClick={handleGenerate}
						disabled={!hasCredentials || busy}
						startIcon={generating ? <CircularProgress size={16} color="inherit" /> : <AutoAwesomeIcon fontSize="small" />}
					>
						{generating ? 'Generating…' : 'Generate'}
					</Button>
				</Box>
			) : (
				<>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
						{summary.source === 'EDITED' ? 'Edited' : 'Generated'}
						{summary.generatedAt ? ` · ${formatTime(summary.generatedAt)}` : ''}
						{summary.model ? ` · ${summary.model}` : ''}
					</Typography>

					<TextField
						value={editing ? text : (summary.content ?? '')}
						onChange={(e) => setText(e.target.value)}
						fullWidth
						multiline
						minRows={5}
						maxRows={14}
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
				</>
			)}
		</Box>
	)
}
