import { useState, useRef, useEffect } from 'react'
import {
	Box,
	Stack,
	TextField,
	MenuItem,
	Typography,
	IconButton,
	Button,
	CircularProgress,
	Divider,
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Tooltip,
	Alert,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { useSaveSceneStructured } from '../../hooks/useScenes'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import {
	useExportCodexDocx,
	useImportCodexDocx,
	useFillCodexWithAi,
} from '../../hooks/useCodexEntry'

const SAVE_DEBOUNCE_MS = 600
const REMOVED_KEY = '_removedFields'
const MAX_GUIDANCE_CHARS = 1000

/**
 * Parses the stored structured_data into a plain object. Accepts either the raw
 * JSON string from the API or an already-parsed object; anything else yields an
 * empty object so a malformed/legacy value never breaks the form.
 */
function parseData(raw) {
	if (!raw) return {}
	if (typeof raw === 'object') return raw
	try {
		const o = JSON.parse(raw)
		return o && typeof o === 'object' && !Array.isArray(o) ? o : {}
	} catch {
		return {}
	}
}

/**
 * Returns true if any field in the AI result would overwrite a non-empty
 * existing value. Used to decide whether to show the confirm dialog.
 */
function wouldOverwriteExisting(currentValues, incomingFields) {
	if (!incomingFields || Object.keys(incomingFields).length === 0) return false
	return Object.entries(incomingFields).some(([key, newVal]) => {
		if (key === REMOVED_KEY) return false
		const existing = currentValues[key]
		return existing && existing.trim() && newVal && newVal.trim()
	})
}

/**
 * Renders the structured fields for a codex entry, driven entirely by the
 * category's schema (see CodexSchema on the backend). Values are stored as a
 * JSON object on scene.structured_data via a debounced autosave, independent of
 * the free rich-text body below it.
 *
 * An action row above the Details card provides Export to Word, Import from
 * Word, and Generate with AI buttons.
 *
 * Authors can remove individual fields from a specific entry via the × button.
 * Removed field keys are tracked in a `_removedFields` array inside the same
 * structured_data object, so the removal is per-entry and persists. A "Show
 * removed fields" action at the bottom restores them.
 *
 * The component is mounted with `key={sceneId}` so it remounts fresh per entry,
 * seeding its initial state from props at mount — no effect-driven state syncing.
 *
 * Props:
 *   sceneId        - UUID of the codex entry scene (required)
 *   schema         - CodexSchema object with fields array (may be null)
 *   initialData    - raw structured_data string or object from the scene
 *   entryTitle     - display title used as the DOCX download filename
 *   onBodyGenerated - callback(htmlString) called when AI returns body text;
 *                     the parent (EditorPanel) sets this on the TipTap editor
 */
export default function CodexEntryFields({
	sceneId,
	schema,
	initialData,
	entryTitle,
	onBodyGenerated,
}) {
	// ── Structured fields state ───────────────────────────────────────────────
	const [values, setValues] = useState(() => parseData(initialData))
	const valuesRef           = useRef(values)
	const dirtyRef            = useRef(false)
	const timerRef            = useRef(null)
	const saveStructured      = useSaveSceneStructured()

	// ── AI Generate state ─────────────────────────────────────────────────────
	const [guidanceOpen, setGuidanceOpen]           = useState(false)
	const [guidance, setGuidance]                   = useState('')
	const [confirmOpen, setConfirmOpen]             = useState(false)
	const [pendingAiResult, setPendingAiResult]     = useState(null)
	const [aiError, setAiError]                     = useState(null)

	// ── Hooks ─────────────────────────────────────────────────────────────────
	const { data: credentials } = useAiCredentials()
	const aiEnabled = (credentials ?? []).some((c) => c.status === 'ACTIVE')

	const exportDocx  = useExportCodexDocx()
	const importDocx  = useImportCodexDocx()
	const fillWithAi  = useFillCodexWithAi()

	const fileInputRef = useRef(null)

	// ── Autosave ──────────────────────────────────────────────────────────────

	const scheduleSave = () => {
		if (timerRef.current) clearTimeout(timerRef.current)
		timerRef.current = setTimeout(() => {
			timerRef.current = null
			if (dirtyRef.current) {
				dirtyRef.current = false
				saveStructured.mutate({
					id: sceneId,
					structuredData: JSON.stringify(valuesRef.current),
				})
			}
		}, SAVE_DEBOUNCE_MS)
	}

	// Flush any pending edit when the entry changes or the component unmounts.
	useEffect(() => {
		return () => {
			if (timerRef.current) {
				clearTimeout(timerRef.current)
				timerRef.current = null
			}
			if (dirtyRef.current) {
				dirtyRef.current = false
				saveStructured.mutate({
					id: sceneId,
					structuredData: JSON.stringify(valuesRef.current),
				})
			}
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sceneId])

	const handleChange = (key, val) => {
		const next = { ...valuesRef.current, [key]: val }
		valuesRef.current = next
		dirtyRef.current = true
		setValues(next)
		scheduleSave()
	}

	// ── Field removal ─────────────────────────────────────────────────────────

	const removedFields = Array.isArray(values[REMOVED_KEY]) ? values[REMOVED_KEY] : []

	const handleRemoveField = (key) => {
		const removed = [...removedFields, key]
		const next = { ...valuesRef.current, [key]: '', [REMOVED_KEY]: removed }
		valuesRef.current = next
		dirtyRef.current = true
		setValues(next)
		scheduleSave()
	}

	const handleRestoreAll = () => {
		const next = { ...valuesRef.current }
		delete next[REMOVED_KEY]
		valuesRef.current = next
		dirtyRef.current = true
		setValues(next)
		scheduleSave()
	}

	// ── Export ────────────────────────────────────────────────────────────────

	const handleExport = () => {
		const filename = entryTitle
			? entryTitle.trim().replace(/[^\w\s-]/g, '').replace(/\s+/g, '_') + '.docx'
			: 'codex-entry.docx'
		exportDocx.mutate({ sceneId, filename })
	}

	// ── Import ────────────────────────────────────────────────────────────────

	const handleImportClick = () => {
		if (fileInputRef.current) fileInputRef.current.click()
	}

	const handleFileSelected = (e) => {
		const file = e.target.files && e.target.files[0]
		if (!file) return
		// Reset input so the same file can be re-selected if needed
		e.target.value = ''
		importDocx.mutate(
			{ sceneId, file },
			{
				onSuccess: (updatedScene) => {
					if (!updatedScene) return
					// Merge the imported structured data into local state
					const incoming = parseData(updatedScene.structuredData)
					const next = { ...valuesRef.current, ...incoming }
					valuesRef.current = next
					dirtyRef.current = false  // already saved by the server
					setValues(next)
					// Apply the imported body to the editor
					if (onBodyGenerated && updatedScene.content) {
						onBodyGenerated(updatedScene.content)
					}
				},
			}
		)
	}

	// ── AI Generate ───────────────────────────────────────────────────────────

	const handleGenerateClick = () => {
		setAiError(null)
		setGuidanceOpen((prev) => !prev)
	}

	const applyAiResult = (result) => {
		if (!result) return
		// Merge AI fields on top of current values, preserving _removedFields etc.
		const next = { ...valuesRef.current, ...result.fields }
		valuesRef.current = next
		dirtyRef.current = true
		setValues(next)
		scheduleSave()
		// Apply AI-generated body to the editor
		if (onBodyGenerated && result.body) {
			// Wrap plain-text body paragraphs in <p> tags
			const html = result.body
				.split(/\n\n+/)
				.map((p) => p.trim())
				.filter(Boolean)
				.map((p) => `<p>${p}</p>`)
				.join('')
			if (html) onBodyGenerated(html)
		}
	}

	const runAiFill = () => {
		setAiError(null)
		fillWithAi.mutate(
			{ sceneId, credentialId: null, userGuidance: guidance.trim() || null },
			{
				onSuccess: (result) => {
					if (!result) return
					if (wouldOverwriteExisting(valuesRef.current, result.fields)) {
						setPendingAiResult(result)
						setConfirmOpen(true)
					} else {
						applyAiResult(result)
					}
				},
				onError: (err) => {
					const serverMsg = err?.response?.data?.message
					setAiError(serverMsg || 'AI generation failed. Check your AI settings and try again.')
				},
			}
		)
	}

	const handleConfirmReplace = () => {
		setConfirmOpen(false)
		applyAiResult(pendingAiResult)
		setPendingAiResult(null)
	}

	const handleCancelConfirm = () => {
		setConfirmOpen(false)
		setPendingAiResult(null)
	}

	// ── Render ────────────────────────────────────────────────────────────────

	const allFields     = schema?.fields || []
	const visibleFields = allFields.filter((f) => !removedFields.includes(f.key))
	const hasRemovedFields = removedFields.length > 0

	// The component renders even with no schema fields so the action row is always
	// visible on codex entries.

	/** Small × button positioned at the trailing edge of each field row. */
	const removeButton = (key) => (
		<IconButton
			size="small"
			onClick={() => handleRemoveField(key)}
			tabIndex={-1}
			title="Remove this field from this entry"
			sx={{ p: 0.25, ml: 0.5, flexShrink: 0, color: 'text.disabled', '&:hover': { color: 'text.secondary' } }}
		>
			<CloseIcon sx={{ fontSize: 16 }} />
		</IconButton>
	)

	return (
		<>
			{/* ── Action row ─────────────────────────────────────────────── */}
			<Box
				sx={{
					maxWidth: '72ch',
					mx: 'auto',
					width: '100%',
					mb: 2,
					display: 'flex',
					alignItems: 'center',
					flexWrap: 'wrap',
					gap: 1,
				}}
			>
				{/* Export */}
				<Tooltip title="Export this entry to a Word document">
					<span>
						<Button
							size="small"
							variant="outlined"
							onClick={handleExport}
							disabled={exportDocx.isPending}
							sx={{ textTransform: 'none', minWidth: 120 }}
						>
							{exportDocx.isPending ? (
								<CircularProgress size={14} sx={{ mr: 0.75 }} />
							) : null}
							Export to Word
						</Button>
					</span>
				</Tooltip>

				{/* Import */}
				<Tooltip title="Import from a previously exported Word document">
					<span>
						<Button
							size="small"
							variant="outlined"
							onClick={handleImportClick}
							disabled={importDocx.isPending}
							sx={{ textTransform: 'none', minWidth: 120 }}
						>
							{importDocx.isPending ? (
								<CircularProgress size={14} sx={{ mr: 0.75 }} />
							) : null}
							Import from Word
						</Button>
					</span>
				</Tooltip>
				<input
					ref={fileInputRef}
					type="file"
					accept=".docx"
					style={{ display: 'none' }}
					onChange={handleFileSelected}
				/>

				<Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

				{/* Generate with AI */}
				<Tooltip
					title={
						aiEnabled
							? 'Fill in this entry using AI and your chapter summaries'
							: 'Add an AI provider key in Settings to enable generation'
					}
				>
					<span>
						<Button
							size="small"
							variant={guidanceOpen ? 'contained' : 'outlined'}
							color="primary"
							onClick={handleGenerateClick}
							disabled={!aiEnabled || fillWithAi.isPending}
							sx={{ textTransform: 'none', minWidth: 140 }}
						>
							{fillWithAi.isPending ? (
								<CircularProgress size={14} sx={{ mr: 0.75 }} />
							) : null}
							Generate with AI
						</Button>
					</span>
				</Tooltip>
			</Box>

			{/* ── AI guidance field ────────────────────────────────────────── */}
			{guidanceOpen && (
				<Box
					sx={{
						maxWidth: '72ch',
						mx: 'auto',
						width: '100%',
						mb: 2,
						display: 'flex',
						flexDirection: 'column',
						gap: 1,
					}}
				>
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
							if (e.target.value.length <= MAX_GUIDANCE_CHARS) {
								setGuidance(e.target.value)
							}
						}}
						helperText={
							guidance
								? `${guidance.length}/${MAX_GUIDANCE_CHARS} — This note is used only for this generation and is not saved`
								: 'Optional: steer this generation. Not saved permanently.'
						}
					/>
					<Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
						<Button
							size="small"
							variant="contained"
							color="primary"
							onClick={runAiFill}
							disabled={fillWithAi.isPending}
							sx={{ textTransform: 'none' }}
						>
							{fillWithAi.isPending ? (
								<>
									<CircularProgress size={14} sx={{ mr: 0.75, color: 'inherit' }} />
									Generating…
								</>
							) : (
								'Generate'
							)}
						</Button>
						{guidance && (
							<Button
								size="small"
								onClick={() => setGuidance('')}
								sx={{ textTransform: 'none', color: 'text.secondary' }}
							>
								Clear guidance
							</Button>
						)}
					</Box>
					{aiError && (
						<Alert severity="error" onClose={() => setAiError(null)} sx={{ mt: 0.5 }}>
							{aiError}
						</Alert>
					)}
				</Box>
			)}

			{/* ── Import error feedback ─────────────────────────────────────── */}
			{importDocx.isError && (
				<Box sx={{ maxWidth: '72ch', mx: 'auto', width: '100%', mb: 2 }}>
					<Alert severity="error" onClose={() => importDocx.reset()}>
						Import failed — make sure the file was exported from NovelKMS. (
						{importDocx.error?.response?.data || importDocx.error?.message || 'Unknown error'})
					</Alert>
				</Box>
			)}

			{/* ── Structured fields card ────────────────────────────────────── */}
			{allFields.length > 0 && (
				<Box
					sx={{
						maxWidth: '72ch',
						mx: 'auto',
						width: '100%',
						mb: 5,
						p: 2.5,
						border: '1px solid',
						borderColor: 'divider',
						borderRadius: 2,
						bgcolor: 'background.paper',
					}}
				>
					<Typography
						variant="overline"
						sx={{ color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1.5 }}
					>
						Details
					</Typography>

					{visibleFields.length > 0 && (
						<Stack spacing={2.25}>
							{visibleFields.map((field) => {
								const value      = values[field.key] ?? ''
								const privateNote = field.feedsAi === false ? 'Not shared with AI' : ''
								const helper      = [field.help, privateNote].filter(Boolean).join(' · ')

								if (field.type === 'SELECT') {
									const options = Array.isArray(field.options) ? field.options : []
									return (
										<Box key={field.key} sx={{ display: 'flex', alignItems: 'flex-start' }}>
											<TextField
												select
												fullWidth
												size="small"
												label={field.label || field.key}
												value={value}
												helperText={helper || undefined}
												onChange={(e) => handleChange(field.key, e.target.value)}
											>
												<MenuItem value="">
													<em>—</em>
												</MenuItem>
												{options.map((opt) => (
													<MenuItem key={opt} value={opt}>
														{opt}
													</MenuItem>
												))}
											</TextField>
											{removeButton(field.key)}
										</Box>
									)
								}

								const multiline = field.type === 'LONG_TEXT'
								return (
									<Box key={field.key} sx={{ display: 'flex', alignItems: 'flex-start' }}>
										<TextField
											fullWidth
											size="small"
											label={field.label || field.key}
											value={value}
											helperText={helper || undefined}
											multiline={multiline}
											minRows={multiline ? 2 : undefined}
											onChange={(e) => handleChange(field.key, e.target.value)}
										/>
										{removeButton(field.key)}
									</Box>
								)
							})}
						</Stack>
					)}

					{hasRemovedFields && (
						<Button
							size="small"
							onClick={handleRestoreAll}
							sx={{ mt: visibleFields.length > 0 ? 2 : 0, textTransform: 'none', color: 'text.secondary' }}
						>
							Show {removedFields.length} removed {removedFields.length === 1 ? 'field' : 'fields'}
						</Button>
					)}
				</Box>
			)}

			{/* ── Overwrite confirm dialog ──────────────────────────────────── */}
			<Dialog open={confirmOpen} onClose={handleCancelConfirm} maxWidth="xs" fullWidth>
				<DialogTitle>Replace existing values?</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						The AI has suggestions for fields that already contain values. Continuing will replace those values with the AI suggestions. You can edit or undo any changes afterward.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={handleCancelConfirm} sx={{ textTransform: 'none' }}>
						Cancel
					</Button>
					<Button
						onClick={handleConfirmReplace}
						variant="contained"
						color="primary"
						sx={{ textTransform: 'none' }}
					>
						Replace All
					</Button>
				</DialogActions>
			</Dialog>
		</>
	)
}
