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
	Typography,
} from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { useImportKmsArchive, useValidateKmsArchive } from '../../hooks/useArchive'
import { HelpButton } from '../../help'

function plural(count, label) {
	return `${count.toLocaleString()} ${label}${count === 1 ? '' : 's'}`
}

function ArchiveCounts({ data }) {
	if (!data) return null
	return (
		<Box component="ul" sx={{ mt: 1, mb: 1.5, pl: 2, '& li': { typography: 'body2' } }}>
			<li>{plural(data.projectCount ?? 0, 'project')}</li>
			<li>{plural(data.bookCount ?? 0, 'book')}</li>
			<li>{plural(data.partCount ?? 0, 'part')}</li>
			<li>{plural(data.chapterCount ?? 0, 'chapter')}</li>
			<li>{plural(data.sceneCount ?? 0, 'scene')}</li>
			{data.codexCount > 0 && <li>{plural(data.codexCount, 'codex')}</li>}
			{data.aiReviewCount > 0 && <li>{plural(data.aiReviewCount, 'AI review')}</li>}
			{data.aiRecommendationCount > 0 && <li>{plural(data.aiRecommendationCount, 'AI recommendation')}</li>}
		</Box>
	)
}

export default function KmsArchiveImportDialog({ open, onClose, onImported }) {
	const fileInputRef = useRef(null)
	const [file, setFile] = useState(null)
	const [preview, setPreview] = useState(null)
	const [result, setResult] = useState(null)
	const [errorMsg, setErrorMsg] = useState(null)

	const validateMutation = useValidateKmsArchive()
	const importMutation = useImportKmsArchive()
	const isBusy = validateMutation.isPending || importMutation.isPending

	const resetForFile = (selected) => {
		setFile(selected)
		setPreview(null)
		setResult(null)
		setErrorMsg(null)
	}

	const handleFileChange = (event) => {
		const selected = event.target.files?.[0] ?? null
		resetForFile(selected)
		if (!selected) return

		validateMutation.mutate(selected, {
			onSuccess: (data) => setPreview(data),
			onError: (err) => {
				const msg = err?.response?.data ?? err?.message ?? 'Unknown validation error'
				setErrorMsg(String(msg))
			},
		})
	}

	const handleImport = () => {
		if (!file || !preview?.valid) return
		setErrorMsg(null)
		setResult(null)
		importMutation.mutate(file, {
			onSuccess: (data) => {
				setResult(data)
				onImported?.(data)
			},
			onError: (err) => {
				const msg = err?.response?.data ?? err?.message ?? 'Unknown import error'
				setErrorMsg(String(msg))
			},
		})
	}

	const handleClose = () => {
		if (isBusy) return
		setFile(null)
		setPreview(null)
		setResult(null)
		setErrorMsg(null)
		onClose()
	}

	const canImport = !!file && preview?.valid && !isBusy && !result

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				Import NovelKMS archive
				<Box sx={{ flex: 1 }} />
				<HelpButton topic="import-export.import" />
			</DialogTitle>
			<DialogContent>
				<input
					ref={fileInputRef}
					type="file"
					accept=".json,application/json,application/vnd.novelkms.archive+json"
					style={{ display: 'none' }}
					onChange={handleFileChange}
				/>

				<Alert severity="info" sx={{ mb: 2 }}>
					Archives are imported as new projects. Existing projects are not merged or replaced, and API keys are not imported.
				</Alert>

				<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
					<Button
						variant="outlined"
						startIcon={<UploadFileIcon />}
						onClick={() => fileInputRef.current?.click()}
						disabled={isBusy || !!result}
					>
						Choose Archive
					</Button>
					<Typography variant="body2" color={file ? 'text.primary' : 'text.secondary'} noWrap>
						{file ? file.name : 'No file selected'}
					</Typography>
				</Box>

				{validateMutation.isPending && (
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
						<CircularProgress size={20} />
						<Typography variant="body2">Validating archive…</Typography>
					</Box>
				)}

				{importMutation.isPending && (
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
						<CircularProgress size={20} />
						<Typography variant="body2">Importing archive…</Typography>
					</Box>
				)}

				{preview && !result && (
					<Box sx={{ mt: 2 }}>
						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
							{preview.valid ? (
								<CheckCircleOutlinedIcon color="success" fontSize="small" />
							) : (
								<WarningAmberIcon color="error" fontSize="small" />
							)}
							<Typography variant="subtitle2">
								{preview.valid ? 'Archive is valid' : 'Archive has errors'}
							</Typography>
						</Box>
						<ArchiveCounts data={preview} />
						{preview.warnings?.length > 0 && (
							<Alert severity="warning" sx={{ mt: 1 }}>
								{preview.warnings.map((w, i) => <Typography key={i} variant="caption" display="block">{w}</Typography>)}
							</Alert>
						)}
						{preview.errors?.length > 0 && (
							<Alert severity="error" sx={{ mt: 1 }}>
								{preview.errors.map((e, i) => <Typography key={i} variant="caption" display="block">{e}</Typography>)}
							</Alert>
						)}
					</Box>
				)}

				{result && (
					<Box sx={{ mt: 2 }}>
						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
							<CheckCircleOutlinedIcon color="success" />
							<Typography variant="subtitle2">Import complete</Typography>
						</Box>
						<ArchiveCounts data={result} />
						{result.warnings?.length > 0 && (
							<>
								<Divider sx={{ my: 1 }} />
								<Alert severity="warning">
									{result.warnings.map((w, i) => <Typography key={i} variant="caption" display="block">{w}</Typography>)}
								</Alert>
							</>
						)}
					</Box>
				)}

				{errorMsg && <Alert severity="error" sx={{ mt: 2 }}>{errorMsg}</Alert>}
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose} disabled={isBusy}>{result ? 'Close' : 'Cancel'}</Button>
				{!result && <Button variant="contained" onClick={handleImport} disabled={!canImport}>Import as New Project</Button>}
			</DialogActions>
		</Dialog>
	)
}
