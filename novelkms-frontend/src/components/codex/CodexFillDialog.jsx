import { useState, useEffect, useRef } from 'react'
import {
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Button,
	Checkbox,
	Box,
	Typography,
	CircularProgress,
	Alert,
	TextField,
	Divider,
	Chip,
} from '@mui/material'
import { useCodexChapters, useFillCodexWithAi } from '../../hooks/useCodexEntry'
import { useGenerateChapterSummary } from '../../hooks/useSummary'

const MAX_GUIDANCE_CHARS = 1000

/**
 * Dialog that lets the author choose which chapters to use as context before
 * running AI fill-in on a codex entry.
 *
 * Each row shows one chapter with:
 *  - Checkbox (enabled only when the chapter has a summary)
 *  - Chapter number and title
 *  - "Stale" chip when the chapter's scenes were edited after the summary
 *  - "Generate" or "Regenerate" button for inline summary creation
 *
 * Chapters without a summary have a disabled, unchecked checkbox and display
 * a "no summary" label. The author can generate a missing summary in place;
 * on success the row's checkbox enables and auto-checks.
 *
 * For project-scoped codexes with multiple books, book-group headings appear
 * above the first chapter of each book.
 *
 * Props:
 *   open       - controls dialog visibility
 *   onClose    - called when the dialog should close (Cancel or after apply)
 *   sceneId    - UUID of the codex entry scene (determines chapter scope)
 *   onApply    - called with the raw FillResponse { fields, body, promptVersion }
 *                after a successful generation; the parent handles overwrite
 *                confirmation and applies the result to the form
 */
export default function CodexFillDialog({ open, onClose, sceneId, onApply }) {
	const { data: chapterData, isLoading, isError } =
		useCodexChapters(sceneId, open)

	// Local chapter state — augments server data with `selected` flag.
	// Re-seeded each time the dialog opens (via the initialized ref).
	const [chapters, setChapters]       = useState([])
	const [guidance, setGuidance]       = useState('')
	const [generatingIds, setGeneratingIds] = useState(new Set())
	const [fillError, setFillError]     = useState(null)
	const initialized                   = useRef(false)

	const fillWithAi        = useFillCodexWithAi()
	const generateSummary   = useGenerateChapterSummary()

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	useEffect(() => {
		if (open && chapterData && !initialized.current) {
			// Seed on first data arrival after the dialog opens.
			// Chapters that already have a summary are checked by default.
			setChapters(chapterData.map((ch) => ({ ...ch, selected: ch.hasSummary })))
			initialized.current = true
		}
		if (!open) {
			// Reset all transient state when the dialog closes.
			initialized.current = false
			setChapters([])
			setGuidance('')
			setFillError(null)
			setGeneratingIds(new Set())
			fillWithAi.reset()
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [open, chapterData])

	// ── Selection helpers ─────────────────────────────────────────────────────

	const hasSummaryCount = chapters.filter((ch) => ch.hasSummary).length
	const selectedCount   = chapters.filter((ch) => ch.selected).length
	const allSelected     = hasSummaryCount > 0 && selectedCount === hasSummaryCount
	const someSelected    = selectedCount > 0 && selectedCount < hasSummaryCount

	const handleSelectAll = () => {
		const next = !allSelected
		setChapters((prev) =>
			prev.map((ch) => ({ ...ch, selected: ch.hasSummary ? next : false }))
		)
	}

	const handleToggle = (chapterId, checked) => {
		setChapters((prev) =>
			prev.map((ch) =>
				ch.chapterId === chapterId ? { ...ch, selected: checked } : ch
			)
		)
	}

	// ── Inline summary generation ─────────────────────────────────────────────

	const handleGenerateSummary = (ch) => {
		const id = ch.chapterId
		setGeneratingIds((prev) => new Set([...prev, id]))
		generateSummary.mutate(
			{ chapterId: id },
			{
				onSuccess: () => {
					// Mark the chapter as having a fresh summary and auto-check it.
					setChapters((prev) =>
						prev.map((c) =>
							c.chapterId === id
								? { ...c, hasSummary: true, isStale: false, selected: true }
								: c
						)
					)
					setGeneratingIds((prev) => {
						const s = new Set(prev)
						s.delete(id)
						return s
					})
				},
				onError: () => {
					setGeneratingIds((prev) => {
						const s = new Set(prev)
						s.delete(id)
						return s
					})
				},
			}
		)
	}

	// ── Fill submission ───────────────────────────────────────────────────────

	const handleFill = () => {
		setFillError(null)
		const selectedIds = chapters
			.filter((ch) => ch.selected)
			.map((ch) => ch.chapterId)
		fillWithAi.mutate(
			{
				sceneId,
				credentialId:       null,
				userGuidance:       guidance.trim() || null,
				selectedChapterIds: selectedIds,
			},
			{
				onSuccess: (result) => {
					onApply(result)
					onClose()
				},
				onError: (err) => {
					const msg = err?.response?.data?.message || err?.response?.data
					setFillError(
						typeof msg === 'string' && msg
							? msg
							: 'AI generation failed. Check your AI settings and try again.'
					)
				},
			}
		)
	}

	// ── Derived render data ───────────────────────────────────────────────────

	// Pre-compute which chapters should show a book-group heading.
	const chaptersWithHeadings = chapters.map((ch, i) => ({
		...ch,
		showBookHeading:
			ch.bookTitle != null &&
			(i === 0 || chapters[i - 1].bookId !== ch.bookId),
	}))

	const canGenerate = selectedCount > 0 && !fillWithAi.isPending
	const anyInProgress =
		generatingIds.size > 0 || generateSummary.isPending || fillWithAi.isPending

	// ── Render ────────────────────────────────────────────────────────────────

	return (
		<Dialog
			open={open}
			onClose={anyInProgress ? undefined : onClose}
			maxWidth="sm"
			fullWidth
		>
			<DialogTitle>Generate Codex Entry</DialogTitle>

			<DialogContent dividers>
				{/* ── Loading ──────────────────────────────────────────── */}
				{isLoading && (
					<Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
						<CircularProgress size={28} />
					</Box>
				)}

				{/* ── Error ────────────────────────────────────────────── */}
				{!isLoading && isError && (
					<Alert severity="error">Could not load the chapter list. Please close and try again.</Alert>
				)}

				{/* ── Empty ────────────────────────────────────────────── */}
				{!isLoading && !isError && chapters.length === 0 && (
					<Alert severity="info">
						No manuscript chapters found for this codex. Add chapters to the book first.
					</Alert>
				)}

				{/* ── Chapter list ──────────────────────────────────────── */}
				{!isLoading && !isError && chapters.length > 0 && (
					<>
						<Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
							Select which chapters to use as manuscript context. Chapters without a
							summary must be generated before they can be selected.
						</Typography>

						{/* Select all / count */}
						<Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5 }}>
							<Checkbox
								checked={allSelected}
								indeterminate={someSelected}
								onChange={handleSelectAll}
								disabled={hasSummaryCount === 0 || fillWithAi.isPending}
								size="small"
							/>
							<Typography variant="body2" color="text.secondary">
								{hasSummaryCount === 0
									? 'No summaries yet — generate one below to begin'
									: `${selectedCount} of ${hasSummaryCount} chapter${hasSummaryCount !== 1 ? 's' : ''} selected`}
							</Typography>
						</Box>

						<Divider sx={{ mb: 0.5 }} />

						{/* Chapter rows */}
						{chaptersWithHeadings.map((ch) => {
							const isGenerating = generatingIds.has(ch.chapterId)
							const chapterLabel = ch.title
								? `Ch.\u00a0${ch.chapterNumber} \u00b7 ${ch.title}`
								: `Chapter\u00a0${ch.chapterNumber}`

							return (
								<Box key={ch.chapterId}>
									{/* Book heading for project-scoped multi-book codexes */}
									{ch.showBookHeading && (
										<Typography
											variant="overline"
											sx={{
												display: 'block',
												mt: 1.5,
												mb: 0.25,
												color: 'text.secondary',
												lineHeight: 1.4,
											}}
										>
											{ch.bookTitle}
										</Typography>
									)}

									<Box
										sx={{
											display: 'flex',
											alignItems: 'center',
											gap: 0.75,
											py: 0.25,
											minHeight: 36,
										}}
									>
										{/* Selection checkbox */}
										<Checkbox
											checked={ch.selected}
											disabled={!ch.hasSummary || isGenerating || fillWithAi.isPending}
											onChange={(e) => handleToggle(ch.chapterId, e.target.checked)}
											size="small"
											sx={{ p: 0.5 }}
										/>

										{/* Chapter label */}
										<Typography
											variant="body2"
											sx={{
												flex: 1,
												color: ch.hasSummary ? 'text.primary' : 'text.disabled',
												overflow: 'hidden',
												textOverflow: 'ellipsis',
												whiteSpace: 'nowrap',
											}}
										>
											{chapterLabel}
										</Typography>

										{/* Status indicators */}
										{ch.hasSummary && ch.isStale && (
											<Chip
												label="Stale"
												size="small"
												color="warning"
												variant="outlined"
												sx={{ height: 20, fontSize: '0.7rem', flexShrink: 0 }}
											/>
										)}
										{!ch.hasSummary && (
											<Typography
												variant="caption"
												sx={{ color: 'text.disabled', flexShrink: 0, whiteSpace: 'nowrap' }}
											>
												no summary
											</Typography>
										)}

										{/* Inline generate / regenerate */}
										<Button
											size="small"
											onClick={() => handleGenerateSummary(ch)}
											disabled={isGenerating || fillWithAi.isPending}
											sx={{ textTransform: 'none', minWidth: 92, flexShrink: 0 }}
										>
											{isGenerating ? (
												<>
													<CircularProgress size={12} sx={{ mr: 0.5 }} />
													Generating…
												</>
											) : ch.hasSummary ? (
												'Regenerate'
											) : (
												'Generate'
											)}
										</Button>
									</Box>
								</Box>
							)
						})}

						<Divider sx={{ mt: 1.5, mb: 2 }} />

						{/* Author guidance */}
						<TextField
							size="small"
							fullWidth
							multiline
							minRows={2}
							maxRows={5}
							label="Guidance for this generation (optional)"
							placeholder="e.g. Focus on her relationship with the antagonist; she switched sides in chapter 4."
							value={guidance}
							onChange={(e) => {
								if (e.target.value.length <= MAX_GUIDANCE_CHARS)
									setGuidance(e.target.value)
							}}
							disabled={fillWithAi.isPending}
							helperText={
								guidance
									? `${guidance.length}/${MAX_GUIDANCE_CHARS} — used only for this generation, not saved`
									: 'Optional: steer this generation. Not saved permanently.'
							}
						/>

						{/* Fill error */}
						{fillError && (
							<Alert
								severity="error"
								sx={{ mt: 1.5 }}
								onClose={() => setFillError(null)}
							>
								{fillError}
							</Alert>
						)}
					</>
				)}
			</DialogContent>

			<DialogActions>
				<Button
					onClick={onClose}
					disabled={anyInProgress}
					sx={{ textTransform: 'none' }}
				>
					Cancel
				</Button>
				<Button
					onClick={handleFill}
					disabled={!canGenerate}
					variant="contained"
					color="primary"
					sx={{ textTransform: 'none' }}
				>
					{fillWithAi.isPending ? (
						<>
							<CircularProgress size={14} sx={{ mr: 0.75, color: 'inherit' }} />
							Generating…
						</>
					) : (
						'Generate Codex Entry'
					)}
				</Button>
			</DialogActions>
		</Dialog>
	)
}
