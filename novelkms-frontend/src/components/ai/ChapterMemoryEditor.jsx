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
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import {
	useChapterMemory,
	useChapterMemoryStatus,
	useGenerateChapterMemory,
} from '../../hooks/useChapterMemory'
import { isFlagged, stateColor, stateExplanation, stateLabel, formatTime, flaggedPreceding } from './memoryStatus'
import PreReviewMemoryDialog from './PreReviewMemoryDialog'
import RegenerateConfirmDialog from './RegenerateConfirmDialog'
import RichTextPreview from './RichTextPreview'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * ChapterMemoryEditor — "peek" surface for one chapter's memory document,
 * shown in the ReviewRail "Memory" tab. The real editing surface is the
 * document's own nav node (Chapter -> Memory), opened in EditorPanel for
 * full rich-text editing; this view renders the current content read-only
 * and offers Generate/Regenerate plus a link to jump to the nav node for
 * hand-editing.
 *
 * The document is the parent chapter's even when a scene is selected (memory is
 * per chapter), hence the optional scene-scope note.
 *
 * Props:
 *   chapterId        {string}
 *   bookId            {string|undefined}  enables the staleness chip + cache refresh
 *   credentialId      {string|null}       AI key for generation; null = default key
 *   sceneScopeNote    {boolean}           show the "memory is per chapter" caption
 *   onEditInDocument  {() => void}        selects the Memory nav node for this chapter
 */
export default function ChapterMemoryEditor({ chapterId, bookId, credentialId = null, sceneScopeNote = false, onEditInDocument }) {
	const { data: credentials = [] } = useAiCredentials()
	const { data: memory, isLoading } = useChapterMemory(chapterId)
	const { data: status = [] } = useChapterMemoryStatus(bookId, !!bookId)

	const { mutate: generate, isPending: generating } = useGenerateChapterMemory()

	const [errorMsg, setErrorMsg] = useState(null)
	const [gateOpen, setGateOpen] = useState(false)
	const [regenConfirmOpen, setRegenConfirmOpen] = useState(false)

	// One-time guidance for the next generation, pre-filled from whatever was
	// used last time (stored on the document itself) so the author can tweak and
	// re-run without retyping. Re-derived on chapter switch and once the doc
	// finishes loading — never auto-cleared after a successful run, since the
	// whole point is to let guidance be repeated or refined across runs.
	const [guidance, setGuidance] = useState('')
	const [guidanceInitKey, setGuidanceInitKey] = useState(null)
	const currentInitKey = `${chapterId}:${isLoading ? 'loading' : 'loaded'}`
	if (currentInitKey !== guidanceInitKey) {
		setGuidanceInitKey(currentInitKey)
		setGuidance(memory?.userGuidance ?? '')
	}

	const hasCredentials = credentials.length > 0
	const state = status.find(s => s.chapterId === chapterId)?.state
	const flagged = flaggedPreceding(status, chapterId)
	const busy = generating

	const doGenerate = () => {
		setErrorMsg(null)
		generate(
			{ chapterId, bookId, credentialId, userGuidance: guidance.trim() || null },
			{ onError: (e) => setErrorMsg(errMessage(e)) },
		)
	}

	// Generating this chapter's memory is gated the same way a chapter review is:
	// if an earlier chapter's memory document is missing or behind, warn first so
	// the author can fill the chain in order (or proceed anyway).
	const proceedToGate = () => {
		if (flagged.length > 0) { setGateOpen(true); return }
		doGenerate()
	}

	// Regenerating discards whatever is currently there (content + formatting);
	// a first-ever Generate has nothing to lose, so it skips straight to the gate.
	const handleGenerate = () => {
		if (memory) { setRegenConfirmOpen(true); return }
		proceedToGate()
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

					<Box sx={{ p: 1, mb: 1, border: '1px solid', borderColor: 'divider', borderRadius: 1, maxHeight: 280, overflowY: 'auto' }}>
						<RichTextPreview html={memory.content} />
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
				</>
			)}

			<RegenerateConfirmDialog
				open={regenConfirmOpen}
				onCancel={() => setRegenConfirmOpen(false)}
				onConfirm={() => { setRegenConfirmOpen(false); proceedToGate() }}
			/>

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
