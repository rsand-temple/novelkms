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
	useChapterMemory,
	useChapterMemoryStatus,
	useGenerateChapterMemory,
	useSaveChapterMemory,
} from '../../hooks/useChapterMemory'
import { isFlagged, stateColor, stateExplanation, stateLabel, formatTime, flaggedPreceding } from './memoryStatus'
import PreReviewMemoryDialog from './PreReviewMemoryDialog'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * ChapterMemoryEditor — view, (re)generate, and hand-edit one chapter's memory
 * document. Shared by the ReviewRail "Memory" tab and the nav MemoryDocDialog,
 * so both surfaces behave identically.
 *
 * The document is the parent chapter's even when a scene is selected (memory is
 * per chapter), hence the optional scene-scope note.
 *
 * Props:
 *   chapterId     {string}
 *   bookId        {string|undefined}  enables the staleness chip + cache refresh
 *   credentialId  {string|null}       AI key for generation; null = default key
 *   sceneScopeNote{boolean}           show the "memory is per chapter" caption
 */
export default function ChapterMemoryEditor({ chapterId, bookId, credentialId = null, sceneScopeNote = false }) {
	const { data: credentials = [] } = useAiCredentials()
	const { data: memory, isLoading } = useChapterMemory(chapterId)
	const { data: status = [] } = useChapterMemoryStatus(bookId, !!bookId)

	const { mutate: generate, isPending: generating } = useGenerateChapterMemory()
	const { mutate: save, isPending: saving } = useSaveChapterMemory()

	const [editing, setEditing] = useState(false)
	const [text, setText] = useState('')
	const [errorMsg, setErrorMsg] = useState(null)
	const [gateOpen, setGateOpen] = useState(false)

	const hasCredentials = credentials.length > 0
	const state = status.find(s => s.chapterId === chapterId)?.state
	const flagged = flaggedPreceding(status, chapterId)
	const busy = generating || saving

	const doGenerate = () => {
		setErrorMsg(null)
		setEditing(false)
		generate(
			{ chapterId, bookId, credentialId },
			{ onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	// Generating this chapter's memory is gated the same way a chapter review is:
	// if an earlier chapter's memory document is missing or behind, warn first so
	// the author can fill the chain in order (or proceed anyway).
	const handleGenerate = () => {
		if (flagged.length > 0) { setGateOpen(true); return }
		doGenerate()
	}

	const startEdit = () => {
		setErrorMsg(null)
		setText(memory?.content ?? '')
		setEditing(true)
	}

	const handleSave = () => {
		setErrorMsg(null)
		if (!text.trim()) {
			setErrorMsg('The memory document must not be blank.')
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
				A short, standardized summary of this chapter. When you review a later chapter, the
				memory documents of all preceding chapters are gathered as “story so far” context.
			</Typography>

			{sceneScopeNote && (
				<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
					Memory is per chapter — this is the parent chapter’s document.
				</Typography>
			)}

			{!hasCredentials && (
				<Alert severity="info" sx={{ mb: 1 }}>
					Add an AI key from Settings → AI to generate memory documents.
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

			{!memory ? (
				<Box sx={{ mt: 1 }}>
					<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
						No memory document yet.
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
						{memory.source === 'EDITED' ? 'Edited' : 'Generated'}
						{memory.generatedAt ? ` · ${formatTime(memory.generatedAt)}` : ''}
						{memory.model ? ` · ${memory.model}` : ''}
					</Typography>

					<TextField
						value={editing ? text : (memory.content ?? '')}
						onChange={(e) => setText(e.target.value)}
						fullWidth
						multiline
						minRows={8}
						maxRows={18}
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

			<PreReviewMemoryDialog
				open={gateOpen}
				onCancel={() => setGateOpen(false)}
				onProceed={() => { setGateOpen(false); doGenerate() }}
				flagged={flagged}
				bookId={bookId}
				credentialId={credentialId}
				title="Earlier chapters’ memory is missing or out of date"
				intro="Memory documents read best as a complete chain in book order."
				proceedLabel="Generate anyway"
				regenerateLabel="Regenerate earlier first"
			/>
		</Box>
	)
}
