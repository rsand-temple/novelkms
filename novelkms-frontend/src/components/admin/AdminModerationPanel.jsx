import { useCallback, useEffect, useState } from 'react'
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
	Paper,
	Stack,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Typography,
} from '@mui/material'
import { adminApi } from '../../api/admin'
import { reportReasonLabel } from '../../utils/reviewReportReasons'

const STATUS_FILTERS = ['OPEN', 'RESOLVED', 'DISMISSED', 'ALL']

function formatDate(value) {
	if (!value) return '—'
	const date = new Date(value)
	return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
}

function reportStatusColor(status) {
	switch (status) {
		case 'OPEN':      return 'warning'
		case 'RESOLVED':  return 'success'
		case 'DISMISSED': return 'default'
		default:          return 'default'
	}
}

/**
 * A shared reason/note dialog for every moderation action — resolve, dismiss, remove
 * a request or review, suspend or reinstate a profile. All of them take the same
 * { reason, note } body, so one dialog covers the lot. Mounted fresh per action so
 * its fields reseed empty. `reason` is a required free-text moderator reason (not the
 * reporter's enum); `note` is optional and stored as the resolution note where the
 * backend keeps one.
 */
function ModerationActionDialog({ action, saving, onClose, onConfirm }) {
	const [reason, setReason] = useState('')
	const [note, setNote] = useState('')

	if (!action) return null

	const canConfirm = !saving && reason.trim().length > 0

	return (
		<Dialog open onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth>
			<DialogTitle>{action.title}</DialogTitle>
			<DialogContent dividers>
				<Stack spacing={2}>
					{action.warning && <Alert severity="warning">{action.warning}</Alert>}
					<TextField
						label="Reason"
						value={reason}
						onChange={(e) => setReason(e.target.value)}
						fullWidth
						required
						size="small"
						autoFocus
						helperText="Recorded in the admin audit log."
					/>
					<TextField
						label="Note (optional)"
						value={note}
						onChange={(e) => setNote(e.target.value)}
						fullWidth
						multiline
						minRows={2}
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button
					variant="contained"
					color={action.confirmColor ?? 'primary'}
					onClick={() => onConfirm({ reason: reason.trim(), note: note.trim() || null })}
					disabled={!canConfirm}
					startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
				>
					{action.confirmLabel ?? 'Confirm'}
				</Button>
			</DialogActions>
		</Dialog>
	)
}

/**
 * One report row (ContentReportView): who reported, what they reported, and why. The
 * reporter is shown by handle only — the view never carries a user id. Resolve and
 * Dismiss close the report; removing a request or review, or suspending a profile,
 * additionally takes the content down and auto-resolves this report server-side.
 *
 * A PROFILE report shows its target id but is acted on from the "Moderate a profile"
 * tool below, keyed by handle — the view does not carry the target handle.
 */
function ReportRow({ report, onAction }) {
	const open = report.status === 'OPEN'
	const removeLabel = report.targetType === 'REQUEST'
		? 'Remove request'
		: report.targetType === 'REVIEW'
			? 'Remove review'
			: null

	return (
		<Paper variant="outlined" sx={{ p: 2 }}>
			<Stack spacing={1.25}>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
					<Chip size="small" label={report.targetType} />
					<Chip size="small" variant="outlined" label={reportReasonLabel(report.reason)} />
					<Box sx={{ flexGrow: 1 }} />
					<Chip size="small" color={reportStatusColor(report.status)} label={report.status} />
				</Stack>

				<Typography variant="caption" color="text.secondary">
					Reported by @{report.reporterHandle} · {formatDate(report.createdAt)}
				</Typography>

				<Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
					Target id: {report.targetId ?? '—'}
				</Typography>

				{report.detail && (
					<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{report.detail}</Typography>
				)}

				{!open && (report.resolutionNote || report.resolvedAt) && (
					<Alert severity="info" variant="outlined">
						<Typography variant="caption" sx={{ display: 'block' }}>
							{report.status === 'RESOLVED' ? 'Resolved' : 'Dismissed'} {formatDate(report.resolvedAt)}
						</Typography>
						{report.resolutionNote && (
							<Typography variant="body2">{report.resolutionNote}</Typography>
						)}
					</Alert>
				)}

				{open && (
					<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
						<Button size="small" variant="outlined" onClick={() => onAction('resolve', report)}>
							Resolve
						</Button>
						<Button size="small" onClick={() => onAction('dismiss', report)}>
							Dismiss
						</Button>
						{removeLabel && (
							<Button size="small" color="error" onClick={() => onAction('remove', report)}>
								{removeLabel}
							</Button>
						)}
						{report.targetType === 'PROFILE' && (
							<Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
								Suspend the writer from the profile tool below.
							</Typography>
						)}
					</Stack>
				)}
			</Stack>
		</Paper>
	)
}

/** Handle-keyed profile suspension / reinstatement, independent of any report. */
function ProfileModerationTool({ onAction }) {
	const [handle, setHandle] = useState('')
	const trimmed = handle.trim().replace(/^@/, '')

	return (
		<Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
			<Stack spacing={1.5}>
				<Typography variant="subtitle1" sx={{ fontWeight: 750 }}>Moderate a profile</Typography>
				<Typography variant="caption" color="text.secondary">
					Suspend a writer's review-network profile (hides it and blocks participation) or
					reinstate a suspended one. Both are recorded in the audit log.
				</Typography>
				<Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
					<TextField
						label="Handle"
						value={handle}
						onChange={(e) => setHandle(e.target.value)}
						size="small"
						sx={{ flexGrow: 1 }}
						placeholder="handle"
					/>
					<Stack direction="row" spacing={1}>
						<Button
							variant="outlined"
							color="error"
							disabled={!trimmed}
							onClick={() => onAction('suspend', { handle: trimmed })}
						>
							Suspend
						</Button>
						<Button
							variant="outlined"
							disabled={!trimmed}
							onClick={() => onAction('reinstate', { handle: trimmed })}
						>
							Reinstate
						</Button>
					</Stack>
				</Stack>
			</Stack>
		</Paper>
	)
}

// Maps an action kind + target onto the API call and the dialog copy. Kept out of the
// component so the wiring reads as one table.
function buildAction(kind, target) {
	switch (kind) {
		case 'resolve':
			return {
				title: 'Resolve report',
				confirmLabel: 'Resolve',
				call: (body) => adminApi.resolveReport(target.id, body),
			}
		case 'dismiss':
			return {
				title: 'Dismiss report',
				confirmLabel: 'Dismiss',
				call: (body) => adminApi.dismissReport(target.id, body),
			}
		case 'remove':
			return target.targetType === 'REQUEST'
				? {
					title: 'Remove request',
					confirmLabel: 'Remove request',
					confirmColor: 'error',
					warning: 'This takes the request down for everyone and auto-resolves its open reports.',
					call: (body) => adminApi.removeRequest(target.targetId, body),
				}
				: {
					title: 'Remove review',
					confirmLabel: 'Remove review',
					confirmColor: 'error',
					warning: 'This removes the review and auto-resolves its open reports.',
					call: (body) => adminApi.removeReview(target.targetId, body),
				}
		case 'suspend':
			return {
				title: `Suspend @${target.handle}`,
				confirmLabel: 'Suspend',
				confirmColor: 'error',
				warning: 'The writer can no longer take part in the review network until reinstated.',
				call: (body) => adminApi.suspendProfile(target.handle, body),
			}
		case 'reinstate':
			return {
				title: `Reinstate @${target.handle}`,
				confirmLabel: 'Reinstate',
				call: (body) => adminApi.reinstateProfile(target.handle, body),
			}
		default:
			return null
	}
}

/**
 * The admin moderation surface (slice 1F): a filterable report queue plus a
 * handle-keyed profile tool. Self-contained and imperative, mirroring the rest of the
 * support console (useState + adminApi, its own loading and error state) rather than
 * introducing react-query into this file.
 */
export default function AdminModerationPanel() {
	const [status, setStatus] = useState('OPEN')
	const [reports, setReports] = useState([])
	const [loading, setLoading] = useState(true)
	const [error, setError] = useState(null)
	const [notice, setNotice] = useState(null)

	const [action, setAction] = useState(null)   // { kind, target, spec }
	const [saving, setSaving] = useState(false)

	const load = useCallback(async (effectiveStatus) => {
		setLoading(true)
		try {
			const rows = await adminApi.listModerationReports(effectiveStatus, 50)
			setReports(rows)
			setError(null)
		} catch (err) {
			if (err.response?.status === 403) {
				setError('You do not have permission to view moderation reports.')
			} else {
				setError(err.response?.data?.message ?? 'Could not load reports.')
			}
			setReports([])
		} finally {
			setLoading(false)
		}
	}, [])

	useEffect(() => {
		let cancelled = false
		async function run() {
			if (!cancelled) await load(status)
		}
		run()
		return () => { cancelled = true }
	}, [status, load])

	const openAction = (kind, target) => {
		const spec = buildAction(kind, target)
		if (spec) setAction({ kind, target, spec })
	}

	const confirmAction = async (body) => {
		if (!action) return
		setSaving(true)
		try {
			await action.spec.call(body)
			setAction(null)
			setNotice({ severity: 'success', message: `${action.spec.title} — done.` })
			await load(status)
		} catch (err) {
			setNotice({
				severity: 'error',
				message: err.response?.data?.message ?? 'That action could not be completed.',
			})
		} finally {
			setSaving(false)
		}
	}

	return (
		<Box sx={{ p: 2 }}>
			<Stack spacing={2}>
				{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
				{notice && (
					<Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.message}</Alert>
				)}

				<ProfileModerationTool onAction={openAction} />

				<Divider />

				<Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 750, flexGrow: 1 }}>
						Content reports
					</Typography>
					<ToggleButtonGroup
						value={status}
						exclusive
						size="small"
						onChange={(_e, next) => { if (next) setStatus(next) }}
					>
						{STATUS_FILTERS.map(s => (
							<ToggleButton key={s} value={s}>{s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}</ToggleButton>
						))}
					</ToggleButtonGroup>
					<Button size="small" onClick={() => load(status)} disabled={loading}>Refresh</Button>
				</Stack>

				{loading ? (
					<Stack sx={{ alignItems: 'center', py: 4 }}><CircularProgress /></Stack>
				) : reports.length === 0 ? (
					<Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
						<Typography variant="body2" color="text.secondary">
							No {status === 'ALL' ? '' : status.toLowerCase()} reports.
						</Typography>
					</Paper>
				) : (
					<Stack spacing={1.5}>
						{reports.map(r => (
							<ReportRow key={r.id} report={r} onAction={openAction} />
						))}
					</Stack>
				)}
			</Stack>

			<ModerationActionDialog
				action={action?.spec ?? null}
				saving={saving}
				onClose={() => setAction(null)}
				onConfirm={confirmAction}
			/>
		</Box>
	)
}
