import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	FormControlLabel,
	IconButton,
	MenuItem,
	Switch,
	TextField,
	Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import StarIcon from '@mui/icons-material/Star'
import StarBorderIcon from '@mui/icons-material/StarBorder'
import VpnKeyOutlinedIcon from '@mui/icons-material/VpnKeyOutlined'
import {
	useAiCredentials,
	useCreateAiCredential,
	useUpdateAiCredential,
	useDeleteAiCredential,
	useSetDefaultAiCredential,
} from '../../hooks/useAiCredentials'

const EMPTY_FORM = { id: null, provider: 'OPENAI', label: '', apiKey: '', defaultModel: '', makeDefault: false }

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

/**
 * AiSettingsDialog
 *
 * Props:
 *   open     {boolean}
 *   onClose  {() => void}
 */
export default function AiSettingsDialog({ open, onClose }) {
	const [form, setForm] = useState(null)        // null = list view; object = add/edit form
	const [confirmId, setConfirmId] = useState(null)
	const [errorMsg, setErrorMsg] = useState(null)

	const { data: credentials = [], isLoading } = useAiCredentials()
	const { mutate: createCredential, isPending: creating } = useCreateAiCredential()
	const { mutate: updateCredential, isPending: updating } = useUpdateAiCredential()
	const { mutate: deleteCredential, isPending: deleting } = useDeleteAiCredential()
	const { mutate: setDefault } = useSetDefaultAiCredential()

	const saving = creating || updating
	const isEdit = !!form?.id

	const startAdd = () => { setErrorMsg(null); setForm({ ...EMPTY_FORM }) }
	const startEdit = (c) => {
		setErrorMsg(null)
		setForm({ id: c.id, provider: c.provider, label: c.label ?? '', apiKey: '', defaultModel: c.defaultModel ?? '', makeDefault: false })
	}
	const cancelForm = () => { setErrorMsg(null); setForm(null) }

	const handleClose = () => {
		if (saving || deleting) return
		setForm(null)
		setConfirmId(null)
		setErrorMsg(null)
		onClose()
	}

	const handleSave = () => {
		setErrorMsg(null)
		const label = form.label.trim() || 'Default'
		const defaultModel = form.defaultModel.trim()
		if (isEdit) {
			updateCredential(
				{ id: form.id, data: { label, apiKey: form.apiKey, defaultModel } },
				{ onSuccess: () => setForm(null), onError: (e) => setErrorMsg(errMessage(e)) },
			)
		} else {
			if (!form.apiKey.trim()) { setErrorMsg('An API key is required.'); return }
			createCredential(
				{ provider: form.provider, label, apiKey: form.apiKey.trim(), defaultModel, makeDefault: form.makeDefault },
				{ onSuccess: () => setForm(null), onError: (e) => setErrorMsg(errMessage(e)) },
			)
		}
	}

	const handleDelete = (id) => {
		setErrorMsg(null)
		deleteCredential({ id }, {
			onSuccess: () => setConfirmId(null),
			onError: (e) => { setErrorMsg(errMessage(e)); setConfirmId(null) },
		})
	}

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<VpnKeyOutlinedIcon fontSize="small" />
				AI Settings
			</DialogTitle>

			<DialogContent>
				<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
					Bring your own API key. Keys are encrypted at rest and never shown again after saving —
					you can replace a key but not view it.
				</Typography>

				{errorMsg && <Alert severity="error" sx={{ mb: 2 }}>{errorMsg}</Alert>}

				{/* ── List view ─────────────────────────────────────────────── */}
				{!form && (
					<>
						{isLoading ? (
							<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2 }}>
								<CircularProgress size={20} />
								<Typography variant="body2">Loading keys…</Typography>
							</Box>
						) : credentials.length === 0 ? (
							<Alert severity="info" sx={{ mb: 2 }}>
								No API keys yet. Add one to enable AI chapter reviews.
							</Alert>
						) : (
							<Box sx={{ mb: 1 }}>
								{credentials.map((c) => (
									<Box
										key={c.id}
										sx={{
											display: 'flex', alignItems: 'center', gap: 1, py: 1,
											borderBottom: '1px solid', borderColor: 'divider',
										}}
									>
										<IconButton
											size="small"
											title={c.defaultCredential ? 'Default key' : 'Make default'}
											onClick={() => !c.defaultCredential && setDefault({ id: c.id })}
											color={c.defaultCredential ? 'warning' : 'default'}
										>
											{c.defaultCredential ? <StarIcon fontSize="small" /> : <StarBorderIcon fontSize="small" />}
										</IconButton>

										<Box sx={{ flexGrow: 1, minWidth: 0 }}>
											<Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
												<Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{c.label}</Typography>
												<Chip label={c.provider} size="small" variant="outlined" />
												{c.defaultCredential && <Chip label="Default" size="small" color="warning" />}
											</Box>
											<Typography variant="caption" color="text.secondary">
												••••{c.keyLast4 ?? '????'}{c.defaultModel ? ` · ${c.defaultModel}` : ''}
											</Typography>
										</Box>

										{confirmId === c.id ? (
											<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
												<Typography variant="caption" color="text.secondary">Delete?</Typography>
												<Button size="small" color="error" onClick={() => handleDelete(c.id)} disabled={deleting}>
													Confirm
												</Button>
												<Button size="small" onClick={() => setConfirmId(null)} disabled={deleting}>
													Cancel
												</Button>
											</Box>
										) : (
											<>
												<IconButton size="small" title="Edit" onClick={() => startEdit(c)}>
													<EditOutlinedIcon fontSize="small" />
												</IconButton>
												<IconButton size="small" title="Delete" onClick={() => setConfirmId(c.id)}>
													<DeleteOutlineIcon fontSize="small" />
												</IconButton>
											</>
										)}
									</Box>
								))}
							</Box>
						)}

						<Button startIcon={<AddIcon />} onClick={startAdd} sx={{ mt: 1 }}>
							Add API Key
						</Button>
					</>
				)}

				{/* ── Add / edit form ───────────────────────────────────────── */}
				{form && (
					<Box>
						<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
							{isEdit ? 'Edit Key' : 'Add Key'}
						</Typography>

						<TextField
							select label="Provider" value={form.provider}
							onChange={(e) => setForm(f => ({ ...f, provider: e.target.value }))}
							fullWidth size="small" sx={{ mb: 2 }} disabled
							helperText="More providers coming later."
						>
							<MenuItem value="OPENAI">OpenAI</MenuItem>
						</TextField>

						<TextField
							label="Label" value={form.label}
							onChange={(e) => setForm(f => ({ ...f, label: e.target.value }))}
							placeholder="Default" fullWidth size="small" sx={{ mb: 2 }}
							helperText="A name to tell multiple keys apart."
						/>

						<TextField
							label="API Key" type="password" value={form.apiKey}
							onChange={(e) => setForm(f => ({ ...f, apiKey: e.target.value }))}
							placeholder={isEdit ? 'Leave blank to keep current key' : 'sk-…'}
							fullWidth size="small" sx={{ mb: 2 }}
							helperText={isEdit ? 'Leave blank to keep the existing key.' : 'Stored encrypted; shown only as ••••last4 afterward.'}
							autoComplete="off"
						/>

						<TextField
							label="Default Model" value={form.defaultModel}
							onChange={(e) => setForm(f => ({ ...f, defaultModel: e.target.value }))}
							placeholder="gpt-5.4" fullWidth size="small" sx={{ mb: 2 }}
							helperText="Optional. Blank uses the provider default."
						/>

						{!isEdit && (
							<FormControlLabel
								control={
									<Switch
										checked={form.makeDefault}
										onChange={(e) => setForm(f => ({ ...f, makeDefault: e.target.checked }))}
									/>
								}
								label="Make this the default key"
							/>
						)}

						<Divider sx={{ my: 2 }} />
						<Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
							<Button onClick={cancelForm} disabled={saving}>Cancel</Button>
							<Button variant="contained" onClick={handleSave} disabled={saving}>
								{saving ? 'Saving…' : (isEdit ? 'Save Changes' : 'Add Key')}
							</Button>
						</Box>
					</Box>
				)}
			</DialogContent>

			<DialogActions>
				<Button onClick={handleClose} disabled={saving || deleting}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
