import { useState } from 'react'
import {
	Popover, Box, Typography, Divider, Select, MenuItem,
	TextField, FormControl, InputLabel, Stack, Button,
} from '@mui/material'

const FONT_FAMILIES = [
	{ label: 'Georgia',        value: 'Georgia, serif' },
	{ label: 'Times New Roman', value: '"Times New Roman", Times, serif' },
	{ label: 'Garamond',       value: 'Garamond, serif' },
	{ label: 'Palatino',       value: '"Palatino Linotype", Palatino, serif' },
	{ label: 'Helvetica',      value: 'Helvetica, Arial, sans-serif' },
	{ label: 'Arial',          value: 'Arial, sans-serif' },
	{ label: 'Courier New',    value: '"Courier New", Courier, monospace' },
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
	// Local copy — only committed on Apply
	const [local, setLocal] = useState(() => ({ ...settings }))

	const set = (key, val) => setLocal(prev => ({ ...prev, [key]: val }))

	const handleApply = () => {
		onSave(local)
		onClose()
	}

	const handleCancel = () => {
		// Discard local edits by resetting on next open (parent re-passes settings as prop)
		onClose()
	}

	return (
		<Popover
			open={Boolean(anchorEl)}
			anchorEl={anchorEl}
			onClose={handleCancel}
			anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
			transformOrigin={{ vertical: 'top', horizontal: 'right' }}
			PaperProps={{ sx: { mt: 0.5 } }}
		>
			<Box sx={{ p: 2.5, width: 300 }}>
				<Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
					Document Settings
				</Typography>

				<Stack spacing={2}>
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

					{/* Buttons */}
					<Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, pt: 0.5 }}>
						<Button size="small" onClick={handleCancel}>Cancel</Button>
						<Button size="small" variant="contained" onClick={handleApply}>Apply</Button>
					</Box>
				</Stack>
			</Box>
		</Popover>
	)
}
