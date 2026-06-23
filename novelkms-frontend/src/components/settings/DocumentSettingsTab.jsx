import { useState } from 'react'
import {
	Alert, Box, Button, Divider, FormControl, InputLabel, MenuItem,
	Select, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material'
import HorizontalRuleIcon from '@mui/icons-material/HorizontalRule'
import {
	useUserEditorSettings,
	useUpdateUserEditorSettings,
	useResetUserEditorSettings,
	useProjectEditorSettings,
	useUpsertProjectEditorSettings,
	useDeleteProjectEditorSettings,
} from '../../hooks/useEditorSettings'
import { PROJECT_SETTINGS_DEFAULTS } from '../../hooks/useProjectSettings'

// Mirrors the option sets in DocSettingsPopover. Kept local because the popover
// does not export them; if these ever diverge, reconcile both files.
const FONT_FAMILIES = [
	{ label: 'Georgia', value: 'Georgia, serif' },
	{ label: 'Times New Roman', value: '"Times New Roman", Times, serif' },
	{ label: 'Garamond', value: 'Garamond, serif' },
	{ label: 'Palatino', value: '"Palatino Linotype", Palatino, serif' },
	{ label: 'Helvetica', value: 'Helvetica, Arial, sans-serif' },
	{ label: 'Arial', value: 'Arial, sans-serif' },
	{ label: 'Courier New', value: '"Courier New", Courier, monospace' },
]

const FONT_SIZES = [
	{ label: '12', value: '0.75rem' },
	{ label: '13', value: '0.8125rem' },
	{ label: '14', value: '0.875rem' },
	{ label: '15', value: '0.9375rem' },
	{ label: '16', value: '1rem' },
	{ label: '17', value: '1.0625rem' },
	{ label: '18', value: '1.125rem' },
	{ label: '20', value: '1.25rem' },
	{ label: '22', value: '1.375rem' },
	{ label: '24', value: '1.5rem' },
]

const LINE_HEIGHTS = ['1.4', '1.5', '1.6', '1.7', '1.8', '1.9', '2.0', '2.2']

// ── Shared field set ──────────────────────────────────────────────────────────
// Presentational: renders the 8 document-settings fields against `value` and
// reports edits through onChange(key, val). No persistence of its own.

function SettingsFields({ value, onChange }) {
	return (
		<Stack spacing={2}>
			<FormControl size="small" fullWidth>
				<InputLabel>Font Family</InputLabel>
				<Select
					value={value.fontFamily}
					label="Font Family"
					onChange={(e) => onChange('fontFamily', e.target.value)}
				>
					{FONT_FAMILIES.map(f => (
						<MenuItem key={f.value} value={f.value} sx={{ fontFamily: f.value }}>
							{f.label}
						</MenuItem>
					))}
				</Select>
			</FormControl>

			<FormControl size="small" fullWidth>
				<InputLabel>Font Size</InputLabel>
				<Select
					value={value.fontSize}
					label="Font Size"
					onChange={(e) => onChange('fontSize', e.target.value)}
				>
					{FONT_SIZES.map(s => (
						<MenuItem key={s.value} value={s.value}>{s.label} pt</MenuItem>
					))}
				</Select>
			</FormControl>

			<FormControl size="small" fullWidth>
				<InputLabel>Line Height</InputLabel>
				<Select
					value={value.lineHeight}
					label="Line Height"
					onChange={(e) => onChange('lineHeight', e.target.value)}
				>
					{LINE_HEIGHTS.map(l => (
						<MenuItem key={l} value={l}>{l}×</MenuItem>
					))}
				</Select>
			</FormControl>

			<Divider />

			<TextField
				label="Default First-Line Indent"
				size="small"
				fullWidth
				value={value.firstLineIndent}
				onChange={(e) => onChange('firstLineIndent', e.target.value)}
				helperText='CSS value, e.g. "1.5em" or "0" to disable'
			/>

			<TextField
				label="Paragraph Spacing After"
				size="small"
				fullWidth
				value={value.spacingAfter}
				onChange={(e) => onChange('spacingAfter', e.target.value)}
				helperText='CSS value, e.g. "0.9em" or "0"'
			/>

			<Divider />

			<Typography variant="caption" color="text.secondary"
				sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}
			>
				Scene Break
			</Typography>

			<Box>
				<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
					Style
				</Typography>
				<ToggleButtonGroup
					value={value.sceneBreakStyle}
					exclusive
					size="small"
					onChange={(_, val) => { if (val) onChange('sceneBreakStyle', val) }}
					sx={{ width: '100%' }}
				>
					<ToggleButton value="* * *" sx={{ flex: 1, fontSize: '0.75rem', letterSpacing: '0.15em' }}>
						* * *
					</ToggleButton>
					<ToggleButton value="#" sx={{ flex: 1, fontSize: '0.85rem' }}>
						#
					</ToggleButton>
					<ToggleButton value="rule" sx={{ flex: 1 }}>
						<HorizontalRuleIcon fontSize="small" />
					</ToggleButton>
				</ToggleButtonGroup>
			</Box>

			<TextField
				label="Spacing Above Break"
				size="small"
				fullWidth
				value={value.sceneBreakSpacingAbove}
				onChange={(e) => onChange('sceneBreakSpacingAbove', e.target.value)}
				helperText='CSS value, e.g. "2em" or "24px"'
			/>

			<TextField
				label="Spacing Below Break"
				size="small"
				fullWidth
				value={value.sceneBreakSpacingBelow}
				onChange={(e) => onChange('sceneBreakSpacingBelow', e.target.value)}
				helperText='CSS value, e.g. "2em" or "24px"'
			/>
		</Stack>
	)
}

// ── Editable form ─────────────────────────────────────────────────────────────
// Holds a local working copy seeded from `initial`. The parent remounts this via
// `key` whenever the persisted row changes (after save / reset / remove), so the
// useState initializer always re-seeds from fresh data — no effect needed.

function EditableForm({ initial, onSave, saving, saveLabel = 'Save', extraActions = null }) {
	const [local, setLocal] = useState(() => ({ ...PROJECT_SETTINGS_DEFAULTS, ...(initial ?? {}) }))
	const set = (key, val) => setLocal(prev => ({ ...prev, [key]: val }))

	return (
		<Box>
			<SettingsFields value={local} onChange={set} />
			<Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
				{extraActions}
				<Button variant="contained" disabled={saving} onClick={() => onSave(local)}>
					{saving ? 'Saving…' : saveLabel}
				</Button>
			</Box>
		</Box>
	)
}

// ── Your default (spans all projects) ─────────────────────────────────────────

function DefaultSettingsSection() {
	const { data: row, isLoading } = useUserEditorSettings()
	const { mutate: save, isPending: saving } = useUpdateUserEditorSettings()
	const { mutate: reset, isPending: resetting } = useResetUserEditorSettings()

	if (isLoading || !row) {
		return <Typography variant="body2" color="text.secondary">Loading…</Typography>
	}

	const overriding = row.scope === 'USER'

	return (
		<Box>
			<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
				Your default
			</Typography>
			<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
				Applies to every project unless a project overrides it.
				{overriding ? '' : ' Currently using the built-in factory defaults.'}
			</Typography>

			<EditableForm
				key={`user:${row.scope}:${row.updatedAt ?? ''}`}
				initial={row.definition}
				saving={saving}
				saveLabel="Save default"
				onSave={(def) => save(def)}
				extraActions={overriding ? (
					<Button color="inherit" disabled={resetting} onClick={() => reset()}>
						{resetting ? 'Resetting…' : 'Reset to factory'}
					</Button>
				) : null}
			/>
		</Box>
	)
}

// ── This project (override) ───────────────────────────────────────────────────

function ProjectSettingsSection({ projectId }) {
	const { data: row, isLoading } = useProjectEditorSettings(projectId)
	const { mutate: upsert, isPending: upserting } = useUpsertProjectEditorSettings()
	const { mutate: remove, isPending: removing } = useDeleteProjectEditorSettings()

	if (isLoading || !row) {
		return <Typography variant="body2" color="text.secondary">Loading…</Typography>
	}

	const overriding = row.scope === 'PROJECT'

	return (
		<Box>
			<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
				This project
			</Typography>

			{overriding ? (
				<>
					<Alert severity="info" sx={{ my: 2 }}>
						This project overrides your default settings.
					</Alert>
					<EditableForm
						key={`proj:${projectId}:${row.updatedAt ?? ''}`}
						initial={row.definition}
						saving={upserting}
						saveLabel="Save project settings"
						onSave={(def) => upsert({ projectId, definition: def })}
						extraActions={
							<Button color="error" disabled={removing} onClick={() => remove({ projectId })}>
								{removing ? 'Removing…' : 'Remove override'}
							</Button>
						}
					/>
				</>
			) : (
				<>
					<Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
						This project uses your default settings. Customize it to give this
						project its own look without affecting the others.
					</Typography>
					<Button
						variant="outlined"
						disabled={upserting}
						onClick={() => upsert({ projectId, definition: { ...PROJECT_SETTINGS_DEFAULTS, ...(row.definition ?? {}) } })}
					>
						{upserting ? 'Working…' : 'Customize for this project'}
					</Button>
				</>
			)}
		</Box>
	)
}

/**
 * DocumentSettingsTab
 *
 * Edits the cascading document ("editor") settings: the user default (all
 * projects) and, when a project is selected, that project's override.
 *
 * Props:
 *   projectId  {string|null}  When set, shows the per-project override panel.
 */
export default function DocumentSettingsTab({ projectId }) {
	return (
		<Stack spacing={3} divider={<Divider flexItem />}>
			<DefaultSettingsSection />
			{projectId && <ProjectSettingsSection projectId={projectId} />}
		</Stack>
	)
}
