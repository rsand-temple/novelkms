import { useRef, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	TextField,
	Typography,
} from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import ImageIcon from '@mui/icons-material/Image'
import PersonIcon from '@mui/icons-material/Person'
import { useImportDocx } from '../../../hooks/useImport'

/**
 * ImportDialog
 *
 * Props:
 *   open        {boolean}
 *   onClose     {() => void}
 *   projectId   {string|null}  — currently selected project
 *   onSuccess   {(bookId: string) => void}  — called with the new bookId after import
 */
export default function ImportDialog({ open, onClose, projectId, onSuccess }) {
	const fileInputRef = useRef(null)

	const [file, setFile] = useState(null)
	const [bookTitle, setBookTitle] = useState('')
	const [result, setResult] = useState(null)
	const [errorMsg, setErrorMsg] = useState(null)

	const { mutate: importDocx, isPending } = useImportDocx()

	const handleFileChange = (e) => {
		const selected = e.target.files?.[0] ?? null
		setFile(selected)
		setResult(null)
		setErrorMsg(null)
		// Do NOT pre-fill bookTitle from the filename — leaving it blank lets the
		// importer use the title detected from the document's cover page instead.
	}

	const handleImport = () => {
		if (!file || !projectId) return
		setResult(null)
		setErrorMsg(null)

		importDocx(
			{ projectId, bookTitle, file },
			{
				onSuccess: (data) => setResult(data),
				onError: (err) => {
					const msg = err?.response?.data ?? err?.message ?? 'Unknown error'
					setErrorMsg(String(msg))
				},
			},
		)
	}

	const handleGoToBook = () => {
		if (result?.bookId) onSuccess(result.bookId)
		handleClose()
	}

	const handleClose = () => {
		if (isPending) return
		setFile(null)
		setBookTitle('')
		setResult(null)
		setErrorMsg(null)
		onClose()
	}

	const canImport = !!file && !!projectId && !isPending && !result

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
			<DialogTitle>Import from Word (.docx)</DialogTitle>

			<DialogContent>
				{!projectId && (
					<Alert severity="warning" sx={{ mb: 2 }}>
						Please select a project in the nav panel before importing.
					</Alert>
				)}

				{/* File picker */}
				<input
					ref={fileInputRef}
					type="file"
					accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
					style={{ display: 'none' }}
					onChange={handleFileChange}
				/>

				<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
					<Button
						variant="outlined"
						startIcon={<UploadFileIcon />}
						onClick={() => fileInputRef.current?.click()}
						disabled={isPending || !!result}
					>
						Choose File
					</Button>
					<Typography variant="body2" color={file ? 'text.primary' : 'text.secondary'} noWrap>
						{file ? file.name : 'No file selected'}
					</Typography>
				</Box>

				{/* Optional title override */}
				<TextField
					label="Book Title"
					helperText="Leave blank to use the title detected from the document or cover page."
					value={bookTitle}
					onChange={(e) => setBookTitle(e.target.value)}
					disabled={isPending || !!result}
					fullWidth
					size="small"
					sx={{ mb: 2 }}
				/>

				{/* Hint text */}
				{!result && !errorMsg && (
					<Typography variant="caption" color="text.secondary">
						The importer detects Heading&nbsp;1 / Heading&nbsp;2 styles for the book
						hierarchy, cover-page metadata (title, subtitle, author, cover image), and
						scene breaks&nbsp;(*, #, ---). Inline bold and italic are preserved.
					</Typography>
				)}

				{/* Progress */}
				{isPending && (
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
						<CircularProgress size={20} />
						<Typography variant="body2">Importing — this may take a moment…</Typography>
					</Box>
				)}

				{/* Error */}
				{errorMsg && (
					<Alert severity="error" sx={{ mt: 2 }}>
						{errorMsg}
					</Alert>
				)}

				{/* Success summary */}
				{result && (
					<Box sx={{ mt: 2 }}>
						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
							<CheckCircleOutlinedIcon color="success" />
							<Typography variant="subtitle2">Import complete</Typography>
						</Box>

						<Typography variant="body2" gutterBottom>
							<strong>{result.bookTitle}</strong>
						</Typography>

						{/* Structure counts */}
						<Box component="ul" sx={{ mt: 0.5, mb: 1.5, pl: 2, '& li': { typography: 'body2' } }}>
							{result.partCount > 0 && (
								<li>{result.partCount} part{result.partCount !== 1 ? 's' : ''}</li>
							)}
							<li>{result.chapterCount} chapter{result.chapterCount !== 1 ? 's' : ''}</li>
							<li>{result.sceneCount} scene{result.sceneCount !== 1 ? 's' : ''}</li>
							<li>{result.wordCount.toLocaleString()} words</li>
						</Box>

						{/* Detected extras */}
						{(result.coverImageImported || result.authorUpdated) && (
							<>
								<Divider sx={{ my: 1 }} />
								{result.coverImageImported && (
									<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
										<ImageIcon fontSize="small" color="primary" />
										<Typography variant="caption">Cover image imported from document</Typography>
									</Box>
								)}
								{result.authorUpdated && (
									<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
										<PersonIcon fontSize="small" color="primary" />
										<Typography variant="caption">Author name populated from cover page</Typography>
									</Box>
								)}
							</>
						)}

						{/* Warnings */}
						{result.warnings?.length > 0 && (
							<>
								<Divider sx={{ my: 1 }} />
								<Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
									<WarningAmberIcon fontSize="small" color="warning" sx={{ mt: '2px' }} />
									<Box>
										{result.warnings.map((w, i) => (
											<Typography key={i} variant="caption" display="block" color="text.secondary">
												{w}
											</Typography>
										))}
									</Box>
								</Box>
							</>
						)}
					</Box>
				)}
			</DialogContent>

			<DialogActions>
				<Button onClick={handleClose} disabled={isPending}>
					{result ? 'Close' : 'Cancel'}
				</Button>
				{!result && (
					<Button variant="contained" onClick={handleImport} disabled={!canImport}>
						Import
					</Button>
				)}
				{result && (
					<Button variant="contained" onClick={handleGoToBook}>
						Go to Book
					</Button>
				)}
			</DialogActions>
		</Dialog>
	)
}