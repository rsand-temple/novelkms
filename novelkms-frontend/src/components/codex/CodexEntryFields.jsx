import { useState, useRef, useEffect, useCallback } from 'react'
import { Box, Stack, TextField, MenuItem, Typography, IconButton, Button } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { useSaveSceneStructured } from '../../hooks/useScenes'

const SAVE_DEBOUNCE_MS = 600
const REMOVED_KEY = '_removedFields'

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
 * Renders the structured fields for a codex entry, driven entirely by the
 * category's schema (see CodexSchema on the backend). Values are stored as a
 * JSON object on scene.structured_data via a debounced autosave, independent of
 * the free rich-text body below it.
 *
 * Authors can remove individual fields from a specific entry via the × button.
 * Removed field keys are tracked in a `_removedFields` array inside the same
 * structured_data object, so the removal is per-entry and persists. A "Show
 * removed fields" action at the bottom restores them.
 *
 * The component is meant to be mounted with `key={sceneId}` so it remounts fresh
 * per entry, seeding its initial state from props at mount — no effect-driven
 * state syncing.
 */
export default function CodexEntryFields({ sceneId, schema, initialData }) {
	const [values, setValues] = useState(() => parseData(initialData))
	const valuesRef = useRef(values)
	const dirtyRef  = useRef(false)
	const timerRef  = useRef(null)
	const saveStructured = useSaveSceneStructured()

	const flush = useCallback(() => {
		if (timerRef.current) {
			clearTimeout(timerRef.current)
			timerRef.current = null
		}
		if (dirtyRef.current) {
			dirtyRef.current = false
			saveStructured.mutate({ id: sceneId, structuredData: JSON.stringify(valuesRef.current) })
		}
	}, [sceneId, saveStructured])

	const scheduleSave = useCallback(() => {
		if (timerRef.current) clearTimeout(timerRef.current)
		timerRef.current = setTimeout(flush, SAVE_DEBOUNCE_MS)
	}, [flush])

	// Flush any pending edit when the entry changes or the panel unmounts, so a
	// quick navigation away never drops the last keystrokes.
	useEffect(() => {
		return () => {
			if (timerRef.current) {
				clearTimeout(timerRef.current)
				timerRef.current = null
			}
			if (dirtyRef.current) {
				dirtyRef.current = false
				saveStructured.mutate({ id: sceneId, structuredData: JSON.stringify(valuesRef.current) })
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

	// ── Field removal (per-entry schema override) ──────────────────────────

	const removedFields = Array.isArray(values[REMOVED_KEY]) ? values[REMOVED_KEY] : []

	const handleRemoveField = (key) => {
		const removed = [...removedFields, key]
		// Clear the value and record the removal in one update.
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

	// ── Render ─────────────────────────────────────────────────────────────

	const allFields = schema?.fields || []
	const visibleFields = allFields.filter((f) => !removedFields.includes(f.key))
	const hasRemovedFields = removedFields.length > 0

	if (!allFields.length) return null
	// If every field has been removed, still show the card with the restore action.

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
						const value = values[field.key] ?? ''
						const privateNote = field.feedsAi === false ? 'Not shared with AI' : ''
						const helper = [field.help, privateNote].filter(Boolean).join(' · ')

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
	)
}
