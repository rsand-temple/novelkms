import { useState, useEffect } from 'react'
import {
	Alert,
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	TextField,
	Typography,
} from '@mui/material'
import FileDownloadIcon from '@mui/icons-material/FileDownload'

/**
 * ExportDialog
 *
 * Presents a filename field and triggers a .docx download from the server.
 *
 * When the browser supports the File System Access API (Chrome / Edge), clicking
 * "Export" opens a native "Save As" dialog so the user can choose both the
 * filename and the destination folder. On other browsers (Firefox, Safari) the
 * file is sent to the default downloads folder using a hidden <a download> link.
 *
 * Props:
 *   open          — boolean
 *   onClose       — () => void
 *   url           — string   full API URL, e.g. /api/export/books/{id}/docx
 *   suggestedName — string   pre-populated base name WITHOUT extension (editable)
 */
export default function ExportDialog({ open, onClose, url, suggestedName }) {

	const [filename,   setFilename]   = useState('')
	const [timestamp,  setTimestamp]  = useState('')
	const [exporting,  setExporting]  = useState(false)
	const [error,      setError]      = useState(null)

	// Reset every time the dialog opens with a new suggestion.
	// Timestamp is locked at open-time so it doesn't tick while the user types.
	useEffect(() => {
		if (open) {
			setFilename(suggestedName || 'export')
			setError(null)
			setExporting(false)
			const now = new Date()
			const pad = (n) => String(n).padStart(2, '0')
			setTimestamp(
				`${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
				`-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
			)
		}
	}, [open, suggestedName])

	// True when the browser can show a native "Save As" picker
	const hasSavePicker = typeof window !== 'undefined' && 'showSaveFilePicker' in window

	/**
	 * Builds the full filename the file will be saved as:
	 *   {base}-{YYYYMMDD-HHmmss}.docx
	 * Spaces in the user's typed name become underscores; the timestamp is
	 * the value locked when the dialog opened.
	 */
	const computedFilename = () => {
		const base = (filename.trim() || 'export')
			.replace(/\.docx$/i, '')          // strip any .docx the user typed
			.replace(/\s+/g, '_')             // spaces → underscores
			.replace(/[\\/:*?"<>|]/g, '')     // strip filesystem-illegal chars
		return `${base}-${timestamp}.docx`
	}

	const handleExport = async () => {
		if (!url) return
		setExporting(true)
		setError(null)

		try {
			if (hasSavePicker) {
				// ── File System Access API: native Save-As dialog ───────────────
				let fileHandle
				try {
					fileHandle = await window.showSaveFilePicker({
						suggestedName: computedFilename(),
						types: [{
							description: 'Word Document',
							accept: {
								'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
									['.docx'],
							},
						}],
					})
				} catch (err) {
					if (err.name === 'AbortError') return   // user cancelled picker
					throw err
				}

				const response = await fetch(url)
				if (!response.ok) throw new Error(`Server returned ${response.status}`)
				const blob     = await response.blob()
				const writable = await fileHandle.createWritable()
				await writable.write(blob)
				await writable.close()

			} else {
				// ── Fallback: browser download to default downloads folder ───────
				const response = await fetch(url)
				if (!response.ok) throw new Error(`Server returned ${response.status}`)
				const blob   = await response.blob()
				const objUrl = URL.createObjectURL(blob)
				const a      = document.createElement('a')
				a.href     = objUrl
				a.download = computedFilename()
				document.body.appendChild(a)
				a.click()
				document.body.removeChild(a)
				URL.revokeObjectURL(objUrl)
			}

			onClose()

		} catch (err) {
			setError(err.message || 'Export failed. Please try again.')
		} finally {
			setExporting(false)
		}
	}

	return (
		<Dialog open={open} onClose={exporting ? undefined : onClose} maxWidth="sm" fullWidth>
			<DialogTitle>Export as Word (.docx)</DialogTitle>

			<DialogContent>
				<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
					{hasSavePicker
						? 'Set a filename, then choose where to save the file.'
						: 'Set a filename. The file will be saved to your downloads folder.'}
				</Typography>

				<TextField
					label="Filename"
					value={filename}
					onChange={(e) => setFilename(e.target.value)}
					onKeyDown={(e) => { if (e.key === 'Enter' && !exporting) handleExport() }}
					fullWidth
					autoFocus
					disabled={exporting}
					helperText={timestamp ? `Will be saved as: ${computedFilename()}` : ''}
					slotProps={{ htmlInput: { spellCheck: false } }}
				/>

				{error && (
					<Alert severity="error" sx={{ mt: 2 }}>
						{error}
					</Alert>
				)}
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose} disabled={exporting}>
					Cancel
				</Button>
				<Button
					variant="contained"
					onClick={handleExport}
					disabled={exporting || !filename.trim()}
					startIcon={exporting ? <CircularProgress size={16} color="inherit" /> : <FileDownloadIcon />}
				>
					{exporting
						? 'Exporting…'
						: hasSavePicker ? 'Choose Location…' : 'Export'}
				</Button>
			</DialogActions>
		</Dialog>
	)
}
