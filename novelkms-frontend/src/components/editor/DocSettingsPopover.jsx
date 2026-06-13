import { useState } from 'react'
import {
	Popover, Box, Typography, Divider, Select, MenuItem,
	TextField, FormControl, InputLabel, Stack, Button,
	ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import HorizontalRuleIcon from '@mui/icons-material/HorizontalRule'

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

// ── Inner form component ──────────────────────────────────────────────────────
//
// Extracted so that it is conditionally rendered by DocSettingsPopover. Because
// it only mounts when the popover is open, the useState initializers always run
// against the current settings snapshot — no useEffect / setState-in-effect
// needed. On close it unmounts, discarding all local state cleanly.

function DocSettingsContent({ settings, onSave, onClose }) {
	// Local copy for font/paragraph settings — committed on Apply only.
	const [local, setLocal] = useState(() => ({ ...settings }))

	// Snapshot captured at mount (= popover open). Cancel reverts live
	// scene-break changes back to this state.
	const [original] = useState(() => ({ ...settings }))

	// Update local display state only (font/paragraph; committed on Apply).
	const set = (key, val) => setLocal(prev => ({ ...prev, [key]: val }))

	// Update local state AND immediately persist (scene break; live preview).
	const setLive = (key, val) => {
		setLocal(prev => ({ ...prev, [key]: val }))
		onSave({ [key]: val })
	}

	const handleApply = () => {
		onSave(local)
		onClose()
	}

	const handleCancel = () => {
		// Revert everything — including any live scene-break changes — back to
		// the state that existed when the popover was opened.
		onSave(original)
		onClose()
	}

	return (
		<Box sx={{ p: 2.5, width: 300 }}>
			<Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
				Document Settings
			</Typography>

			<Stack spacing={2}>

				{/* ── Font / paragraph ─────────────────────────────────────── */}

				{/* Font family */}
				<FormControl size="small" fullWidth>
					<InputLabel>Font Family</InputLabel>
					<Select
						value={local.fontFamily}
						label="Font Family"
						onChange={e => set('fontFamily', e.target.value)}
					>
						{FONT_FAMILIES.map(f => (
							<MenuItem key={f.value} value={f.value} sx={{ fontFamily: f.value }}>
								{f.label}
							</MenuItem>
						))}
					</Select>
				</FormControl>

				{/* Font size */}
				<FormControl size="small" fullWidth>
					<InputLabel>Font Size</InputLabel>
					<Select
						value={local.fontSize}
						label="Font Size"
						onChange={e => set('fontSize', e.target.value)}
					>
						{FONT_SIZES.map(s => (
							<MenuItem key={s.value} value={s.value}>{s.label} pt</MenuItem>
						))}
					</Select>
				</FormControl>

				{/* Line height */}
				<FormControl size="small" fullWidth>
					<InputLabel>Line Height</InputLabel>
					<Select
						value={local.lineHeight}
						label="Line Height"
						onChange={e => set('lineHeight', e.target.value)}
					>
						{LINE_HEIGHTS.map(l => (
							<MenuItem key={l} value={l}>{l}×</MenuItem>
						))}
					</Select>
				</FormControl>

				<Divider />

				{/* First-line indent */}
				<TextField
					label="Default First-Line Indent"
					size="small"
					fullWidth
					value={local.firstLineIndent}
					onChange={e => set('firstLineIndent', e.target.value)}
					helperText='CSS value, e.g. "1.5em" or "0" to disable'
				/>

				{/* Paragraph spacing */}
				<TextField
					label="Paragraph Spacing After"
					size="small"
					fullWidth
					value={local.spacingAfter}
					onChange={e => set('spacingAfter', e.target.value)}
					helperText='CSS value, e.g. "0.9em" or "0"'
				/>

				{/* ── Scene break ───────────────────────────────────────────── */}

				<Divider />

				<Typography variant="caption" color="text.secondary"
					sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}
				>
					Scene Break
				</Typography>

				{/* Style selector — live preview */}
				<Box>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
						Style
					</Typography>
					<ToggleButtonGroup
						value={local.sceneBreakStyle}
						exclusive
						size="small"
						onChange={(_, val) => { if (val) setLive('sceneBreakStyle', val) }}
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

				{/* Spacing above — live preview */}
				<TextField
					label="Spacing Above Break"
					size="small"
					fullWidth
					value={local.sceneBreakSpacingAbove}
					onChange={e => setLive('sceneBreakSpacingAbove', e.target.value)}
					helperText='CSS value, e.g. "2em" or "24px"'
				/>

				{/* Spacing below — live preview */}
				<TextField
					label="Spacing Below Break"
					size="small"
					fullWidth
					value={local.sceneBreakSpacingBelow}
					onChange={e => setLive('sceneBreakSpacingBelow', e.target.value)}
					helperText='CSS value, e.g. "2em" or "24px"'
				/>

				{/* ── Actions ───────────────────────────────────────────────── */}

				<Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, pt: 0.5 }}>
					<Button size="small" onClick={handleCancel}>Cancel</Button>
					<Button size="small" variant="contained" onClick={handleApply}>Apply</Button>
				</Box>

			</Stack>
		</Box>
	)
}

// ── Shell component ───────────────────────────────────────────────────────────

/**
 * DocSettingsPopover
 *
 * Props:
 *   anchorEl         — Element to anchor the popover to (null = closed)
 *   onClose()        — Called when the popover should close
 *   settings         — Current project settings object
 *   onSave(patch)    — Called with the updated settings object on Apply
 */
export default function DocSettingsPopover({ anchorEl, onClose, settings, onSave }) {
	return (
		<Popover
			open={Boolean(anchorEl)}
			anchorEl={anchorEl}
			onClose={onClose}
			anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
			transformOrigin={{ vertical: 'top', horizontal: 'right' }}
			PaperProps={{ sx: { mt: 0.5 } }}
		>
			{/* Conditional render — mounts DocSettingsContent fresh on each open
			    so its useState initializers always capture the current settings.
			    Unmounting on close discards all local edits cleanly. */}
			{Boolean(anchorEl) && (
				<DocSettingsContent
					settings={settings}
					onSave={onSave}
					onClose={onClose}
				/>
			)}
		</Popover>
	)
}