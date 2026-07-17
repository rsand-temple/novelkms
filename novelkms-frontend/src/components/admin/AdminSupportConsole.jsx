import { useCallback, useMemo, useEffect, useState } from 'react'
import {
	Alert,
	AppBar,
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	List,
	ListItemButton,
	ListItemText,
	Paper,
	Stack,
	Tab,
	Tabs,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Toolbar,
	Typography,
} from '@mui/material'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import DeleteForeverIcon from '@mui/icons-material/DeleteForever'
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom'
import MoreTimeIcon from '@mui/icons-material/MoreTime'
import SearchIcon from '@mui/icons-material/Search'
import { adminApi } from '../../api/admin'
import { LogoMark } from '../branding/Logo'

function valueOrDash(value) {
	return value === null || value === undefined || value === '' ? '—' : String(value)
}

function formatDate(value) {
	if (!value) return '—'

	const date = new Date(value)
	if (Number.isNaN(date.getTime())) return String(value)

	return date.toLocaleString()
}

function statusColor(status) {
	switch (status) {
		case 'ACTIVE':
		case 'active':
		case 'family':
			return 'success'
		case 'trialing':
		case 'active_canceling':
		case 'past_due':
			return 'warning'
		case 'DISABLED':
		case 'canceled':
		case 'unpaid':
			return 'error'
		default:
			return 'default'
	}
}

function roleColor(role) {
	switch (role) {
		case 'ADMIN':
			return 'success'
		default:
			return 'default'
	}
}

function Section({ title, children, actions }) {
	return (
		<Paper
			variant="outlined"
			sx={{
				p: 2,
				borderRadius: 2,
				bgcolor: 'background.paper',
			}}
		>
			<Stack spacing={1.5}>
				<Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
					<Typography variant="subtitle1" sx={{ fontWeight: 750, flex: 1 }}>
						{title}
					</Typography>
					{actions}
				</Box>
				{children}
			</Stack>
		</Paper>
	)
}

function DetailRow({ label, value }) {
	return (
		<Box
			sx={{
				display: 'grid',
				gridTemplateColumns: { xs: '1fr', sm: '160px 1fr' },
				gap: { xs: 0.25, sm: 1 },
				alignItems: 'baseline',
			}}
		>
			<Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
				{label}
			</Typography>
			<Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
				{valueOrDash(value)}
			</Typography>
		</Box>
	)
}

function UsageSummary({ usage }) {
	if (!usage) {
		return <Typography variant="body2" color="text.secondary">No usage data.</Typography>
	}

	const items = [
		['Projects', usage.projectCount],
		['Books', usage.bookCount],
		['Parts', usage.partCount],
		['Chapters', usage.chapterCount],
		['Scenes', usage.sceneCount],
		['Codex entries', usage.codexEntryCount],
		['AI reviews', usage.aiReviewCount],
	]

	return (
		<Box
			sx={{
				display: 'grid',
				gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(4, minmax(0, 1fr))' },
				gap: 1,
			}}
		>
			{items.map(([label, value]) => (
				<Paper key={label} variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
					<Typography variant="h6" sx={{ lineHeight: 1.1 }}>
						{value ?? 0}
					</Typography>
					<Typography variant="caption" color="text.secondary">
						{label}
					</Typography>
				</Paper>
			))}
		</Box>
	)
}

function BillingFlags({ billing }) {
	if (!billing) return null

	const flags = [
		['Has access', billing.hasAccess],
		['Family', billing.familyAccess],
		['Stripe linked', billing.stripeLinked],
		['Trial active', billing.trialActive],
		['Canceling', billing.canceling],
		['Payment problem', billing.paymentProblem],
	]

	return (
		<Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
			{flags.map(([label, active]) => (
				<Chip
					key={label}
					size="small"
					label={label}
					color={active ? 'primary' : 'default'}
					variant={active ? 'filled' : 'outlined'}
				/>
			))}
		</Stack>
	)
}

function AuditList({ rows }) {
	if (!rows?.length) {
		return <Typography variant="body2" color="text.secondary">No audit entries for this user.</Typography>
	}

	return (
		<Stack divider={<Divider flexItem />} spacing={1}>
			{rows.map((row) => (
				<Box key={row.id}>
					<Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
						<Chip size="small" label={row.action} />
						<Typography variant="caption" color="text.secondary">
							{formatDate(row.createdAt)}
						</Typography>
					</Stack>
					<Typography variant="body2" sx={{ mt: 0.5 }}>
						{valueOrDash(row.reason)}
					</Typography>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, overflowWrap: 'anywhere' }}>
						Admin: {row.adminUserId}
					</Typography>
				</Box>
			))}
		</Stack>
	)
}

function MetricCard({ label, value, helper, severity = 'default' }) {
	const colorMap = {
		success: 'success.main',
		warning: 'warning.main',
		error: 'error.main',
		default: 'text.primary',
	}

	return (
		<Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
			<Typography variant="h4" sx={{ fontWeight: 800, color: colorMap[severity] ?? colorMap.default }}>
				{value ?? 0}
			</Typography>
			<Typography variant="subtitle2" sx={{ fontWeight: 750 }}>
				{label}
			</Typography>
			{helper && (
				<Typography variant="caption" color="text.secondary">
					{helper}
				</Typography>
			)}
		</Paper>
	)
}

function MetricGrid({ children }) {
	return (
		<Box
			sx={{
				display: 'grid',
				gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(4, minmax(0, 1fr))' },
				gap: 1.5,
			}}
		>
			{children}
		</Box>
	)
}

function MetricRows({ rows }) {
	return (
		<Stack divider={<Divider flexItem />} spacing={0.75}>
			{rows.map(([label, value]) => (
				<Box key={label} sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
					<Typography variant="body2" sx={{ flex: 1 }}>
						{label}
					</Typography>
					<Typography variant="body2" sx={{ fontWeight: 750 }}>
						{value ?? 0}
					</Typography>
				</Box>
			))}
		</Stack>
	)
}

function AdminOverview({ metrics, loading, onRefresh }) {
	if (loading && !metrics) {
		return (
			<Paper variant="outlined" sx={{ p: 4, borderRadius: 2, display: 'grid', placeItems: 'center' }}>
				<CircularProgress />
			</Paper>
		)
	}

	if (!metrics) {
		return (
			<Paper variant="outlined" sx={{ p: 3, borderRadius: 2 }}>
				<Typography variant="body2" color="text.secondary">
					No overview metrics loaded.
				</Typography>
			</Paper>
		)
	}

	const users = metrics.users ?? {}
	const billing = metrics.billing ?? {}
	const activity = metrics.activity ?? {}
	const content = metrics.content ?? {}
	const ai = metrics.ai ?? {}
	const billingHealth = metrics.billingHealth ?? {}

	return (
		<Stack spacing={2}>
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<Box sx={{ flex: 1 }}>
					<Typography variant="h5" sx={{ fontWeight: 800 }}>
						Admin overview
					</Typography>
					<Typography variant="caption" color="text.secondary">
						Evaluated {formatDate(metrics.evaluatedAt)}
					</Typography>
				</Box>
				<Button size="small" variant="outlined" onClick={onRefresh} disabled={loading}>
					Refresh
				</Button>
			</Box>

			<MetricGrid>
				<MetricCard label="Total users" value={users.total} />
				<MetricCard label="Subscribed/access" value={billing.subscribedAccess} helper="active + canceling + trial + family" severity="success" />
				<MetricCard label="Trialing" value={billing.trialing} severity="warning" />
				<MetricCard label="Idle 30 days" value={users.idle30Days} helper="active users only" severity={users.idle30Days ? 'warning' : 'default'} />
			</MetricGrid>

			<MetricGrid>
				<MetricCard label="New users 7d" value={users.createdLast7Days} />
				<MetricCard label="Logins 7d" value={activity.loginsLast7Days} />
				<MetricCard label="AI reviews 30d" value={ai.reviewsLast30Days} />
				<MetricCard label="Payment problems" value={billingHealth.paymentProblems} severity={billingHealth.paymentProblems ? 'error' : 'default'} />
			</MetricGrid>

			<Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
				<Section title="Users">
					<MetricRows rows={[
						['Active', users.active],
						['Disabled', users.disabled],
						['Created last 30 days', users.createdLast30Days],
						['Never logged in', users.neverLoggedIn],
					]} />
				</Section>

				<Section title="Billing">
					<MetricRows rows={[
						['Active', billing.active],
						['Active canceling', billing.activeCanceling],
						['Trialing', billing.trialing],
						['Family', billing.family],
						['Past due', billing.pastDue],
						['Canceled', billing.canceled],
						['No subscription row', billing.noSubscriptionRow],
					]} />
				</Section>

				<Section title="Content">
					<MetricRows rows={[
						['Projects', content.projects],
						['Books', content.books],
						['Parts', content.parts],
						['Chapters', content.chapters],
						['Scenes', content.scenes],
						['Codex entries', content.codexEntries],
					]} />
				</Section>

				<Section title="AI review activity">
					<MetricRows rows={[
						['Reviews total', ai.reviewsTotal],
						['Reviews last 7 days', ai.reviewsLast7Days],
						['Reviews last 30 days', ai.reviewsLast30Days],
						['Open recommendations', ai.openRecommendations],
						['Deferred recommendations', ai.deferredRecommendations],
						['Promoted recommendations', ai.promotedRecommendations],
					]} />
				</Section>

				<Section title="Billing health">
					<MetricRows rows={[
						['Failed webhooks last 7 days', billingHealth.failedWebhookEventsLast7Days],
						['Unprocessed/failed webhook events', billingHealth.unprocessedWebhookEvents],
						['Payment problems', billingHealth.paymentProblems],
					]} />
				</Section>
			</Box>
		</Stack>
	)
}

function GrantFamilyAccessDialog({ open, user, saving, onClose, onSubmit }) {
	const [reason, setReason] = useState('')
	const [note, setNote] = useState('')

	const canSubmit = !saving

	return (
		<Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle>Grant family access</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					<Alert severity="warning">
						This grants local NovelKMS access outside the normal Stripe subscription flow.
					</Alert>

					<Typography variant="body2">
						Target user: <strong>{user?.displayName ?? user?.emailAddress ?? user?.id}</strong>
					</Typography>

					<TextField
						label="Reason"
						value={reason}
						onChange={(event) => setReason(event.target.value)}
						placeholder="family_discount, founder_account, support_comp"
						fullWidth
						size="small"
					/>

					<TextField
						label="Note"
						value={note}
						onChange={(event) => setNote(event.target.value)}
						placeholder="Optional support note"
						fullWidth
						multiline
						minRows={3}
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button
					variant="contained"
					onClick={() => onSubmit({ reason, note })}
					disabled={!canSubmit}
					startIcon={saving ? <CircularProgress size={16} /> : <FamilyRestroomIcon />}
				>
					Grant access
				</Button>
			</DialogActions>
		</Dialog>
	)
}

/**
 * Extends (or starts) a user's local trial. The admin picks one of two modes:
 * an absolute end date or a fixed number of days to add. The chosen mode's value
 * is the only one sent — the backend requires exactly one.
 *
 * A trial cannot be extended over a stronger entitlement (family/active/
 * active_canceling); the button is disabled and a note is shown in that case so
 * the admin sees why before opening the dialog is even useful.
 */
function ExtendTrialDialog({ open, user, billing, saving, onClose, onSubmit }) {
	const [mode, setMode] = useState('days')
	const [days, setDays] = useState('14')
	const [endDate, setEndDate] = useState('')
	const [reason, setReason] = useState('')
	const [note, setNote] = useState('')

	const status = billing?.subscription?.status ?? null
	const blockedStatus =
		status === 'family' || status === 'active' || status === 'active_canceling'

	const parsedDays = Number.parseInt(days, 10)
	const daysValid = Number.isFinite(parsedDays) && parsedDays > 0
	const dateValid = Boolean(endDate)
	const modeValid = mode === 'days' ? daysValid : dateValid
	const canSubmit = !saving && !blockedStatus && modeValid

	function handleSubmit() {
		const body = { reason, note }
		if (mode === 'days') {
			body.extendDays = parsedDays
		} else {
			// A date-only input has no timezone; treat midnight as UTC. The backend
			// resolves it to end-of-day UTC, so any local date maps to a full day.
			body.trialEndsAt = new Date(`${endDate}T00:00:00Z`).toISOString()
		}
		onSubmit(body)
	}

	return (
		<Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle>Extend trial</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					{blockedStatus ? (
						<Alert severity="warning">
							This user's status is <strong>{status}</strong>. Extending a trial would
							demote a stronger entitlement, so it is not allowed. Manage this user's
							access through Stripe (or family access) instead.
						</Alert>
					) : (
						<Alert severity="info">
							This sets a local trial end date outside the normal Stripe flow. When it
							passes, the user loses access until they subscribe.
						</Alert>
					)}

					<Typography variant="body2">
						Target user: <strong>{user?.displayName ?? user?.emailAddress ?? user?.id}</strong>
					</Typography>

					<Typography variant="body2" color="text.secondary">
						Current trial end: {formatDate(billing?.subscription?.trialEnd)}
					</Typography>

					<ToggleButtonGroup
						exclusive
						size="small"
						value={mode}
						onChange={(_event, next) => {
							if (next) setMode(next)
						}}
						disabled={saving || blockedStatus}
					>
						<ToggleButton value="days">Add days</ToggleButton>
						<ToggleButton value="date">Set date</ToggleButton>
					</ToggleButtonGroup>

					{mode === 'days' ? (
						<TextField
							label="Days to add"
							type="number"
							value={days}
							onChange={(event) => setDays(event.target.value)}
							helperText="Extends from the later of today or the current trial end."
							fullWidth
							size="small"
							disabled={saving || blockedStatus}
							slotProps={{ htmlInput: { min: 1, max: 365 } }}
						/>
					) : (
						<TextField
							label="Trial end date"
							type="date"
							value={endDate}
							onChange={(event) => setEndDate(event.target.value)}
							helperText="Access runs through the end of this day (UTC)."
							fullWidth
							size="small"
							disabled={saving || blockedStatus}
							slotProps={{ inputLabel: { shrink: true } }}
						/>
					)}

					<TextField
						label="Reason"
						value={reason}
						onChange={(event) => setReason(event.target.value)}
						placeholder="extended_eval, support_comp, onboarding_delay"
						fullWidth
						size="small"
						disabled={saving || blockedStatus}
					/>

					<TextField
						label="Note"
						value={note}
						onChange={(event) => setNote(event.target.value)}
						placeholder="Optional support note"
						fullWidth
						multiline
						minRows={3}
						disabled={saving || blockedStatus}
					/>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>Cancel</Button>
				<Button
					variant="contained"
					onClick={handleSubmit}
					disabled={!canSubmit}
					startIcon={saving ? <CircularProgress size={16} /> : <MoreTimeIcon />}
				>
					Extend trial
				</Button>
			</DialogActions>
		</Dialog>
	)
}

/**
 * Confirmation dialog for the irreversible hard-delete action.
 *
 * The admin must type the target user's email address verbatim before the
 * Delete button becomes active — same pattern GitHub uses for dangerous
 * destructive actions.
 */
function HardDeleteUserDialog({ open, user, saving, onClose, onSubmit }) {
	const [reason, setReason] = useState('')
	const [confirmEmail, setConfirmEmail] = useState('')

	const targetEmail = user?.emailAddress ?? ''
	const emailMatches = confirmEmail.trim().toLowerCase() === targetEmail.toLowerCase()
	const canSubmit = !saving && emailMatches && targetEmail !== ''

	return (
		<Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
			<DialogTitle sx={{ color: 'error.main' }}>
				Permanently delete user
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					<Alert severity="error">
						This action is <strong>irreversible</strong>. It will permanently delete all
						manuscripts, codex entries, AI reviews, artifacts, billing records, and
						credentials for this account. Any active Stripe subscription will be
						canceled immediately.
					</Alert>

					<Paper variant="outlined" sx={{ p: 1.5, borderRadius: 1.5, bgcolor: 'action.hover' }}>
						<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
							Account to be deleted
						</Typography>
						<Typography variant="body2" sx={{ fontWeight: 700 }}>
							{user?.displayName ?? '—'}
						</Typography>
						<Typography variant="body2" color="text.secondary">
							{targetEmail}
						</Typography>
						<Typography variant="caption" color="text.secondary">
							{user?.id}
						</Typography>
					</Paper>

					<TextField
						label="Reason for deletion"
						value={reason}
						onChange={(event) => setReason(event.target.value)}
						placeholder="spam_account, abuse, gdpr_request"
						fullWidth
						size="small"
					/>

					<Box>
						<Typography variant="body2" sx={{ mb: 1 }}>
							Type <strong>{targetEmail}</strong> to confirm:
						</Typography>
						<TextField
							value={confirmEmail}
							onChange={(event) => setConfirmEmail(event.target.value)}
							placeholder={targetEmail}
							fullWidth
							size="small"
							error={confirmEmail !== '' && !emailMatches}
							autoComplete="off"
							inputProps={{ spellCheck: false }}
						/>
					</Box>
				</Stack>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose} disabled={saving}>
					Cancel
				</Button>
				<Button
					variant="contained"
					color="error"
					onClick={() => onSubmit({ reason })}
					disabled={!canSubmit}
					startIcon={saving ? <CircularProgress size={16} /> : <DeleteForeverIcon />}
				>
					Delete permanently
				</Button>
			</DialogActions>
		</Dialog>
	)
}

export default function AdminSupportConsole() {
	const [query, setQuery] = useState('')
	const [users, setUsers] = useState([])
	const [selectedUserId, setSelectedUserId] = useState(null)
	const [selectedUser, setSelectedUser] = useState(null)
	const [billing, setBilling] = useState(null)
	const [auditRows, setAuditRows] = useState([])
	const [loadingUsers, setLoadingUsers] = useState(true)
	const [loadingDetail, setLoadingDetail] = useState(false)
	const [savingFamilyAccess, setSavingFamilyAccess] = useState(false)
	const [savingTrial, setSavingTrial] = useState(false)
	const [deletingUser, setDeletingUser] = useState(false)
	const [familyDialogOpen, setFamilyDialogOpen] = useState(false)
	const [extendTrialDialogOpen, setExtendTrialDialogOpen] = useState(false)
	const [hardDeleteDialogOpen, setHardDeleteDialogOpen] = useState(false)
	const [error, setError] = useState(null)
	const [success, setSuccess] = useState(null)
	const [tab, setTab] = useState('overview')
	const [metrics, setMetrics] = useState(null)
	const [loadingMetrics, setLoadingMetrics] = useState(true)

	const selectedSummary = useMemo(
		() => users.find((user) => user.id === selectedUserId) ?? null,
		[users, selectedUserId]
	)

	const searchUsers = useCallback(async (effectiveQuery = '') => {
		const trimmedQuery = effectiveQuery?.trim() ?? ''

		try {
			const rows = await adminApi.searchUsers(trimmedQuery, 25)

			setUsers(rows)
			setSelectedUserId((currentSelectedUserId) => {
				if (currentSelectedUserId) return currentSelectedUserId
				return rows.length > 0 ? rows[0].id : null
			})
			setError(null)
		} catch (err) {
			if (err.response?.status === 403) {
				setError('You are authenticated, but this account does not have administrator access.')
			} else {
				setError(err.response?.data?.message ?? 'Could not load admin users.')
			}
		} finally {
			setLoadingUsers(false)
		}
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitialUsers() {
			try {
				const rows = await adminApi.searchUsers('', 25)

				if (!cancelled) {
					setUsers(rows)
					setSelectedUserId(rows.length > 0 ? rows[0].id : null)
					setError(null)
				}
			} catch (err) {
				if (!cancelled) {
					if (err.response?.status === 403) {
						setError('You are authenticated, but this account does not have administrator access.')
					} else {
						setError(err.response?.data?.message ?? 'Could not load admin users.')
					}
				}
			} finally {
				if (!cancelled) {
					setLoadingUsers(false)
				}
			}
		}

		loadInitialUsers()

		return () => {
			cancelled = true
		}
	}, [])

	useEffect(() => {
		let cancelled = false

		async function loadInitialMetrics() {
			try {
				const overview = await adminApi.getOverviewMetrics()

				if (!cancelled) {
					setMetrics(overview)
					setError(null)
				}
			} catch (err) {
				if (!cancelled) {
					if (err.response?.status === 403) {
						setError('You are authenticated, but this account does not have administrator access.')
					} else {
						setError(err.response?.data?.message ?? 'Could not load admin overview metrics.')
					}
				}
			} finally {
				if (!cancelled) {
					setLoadingMetrics(false)
				}
			}
		}

		loadInitialMetrics()

		return () => {
			cancelled = true
		}
	}, [])

	const loadSelectedUserDetail = useCallback(async (userId, { signalLoading = true } = {}) => {
		if (!userId) {
			setSelectedUser(null)
			setBilling(null)
			setAuditRows([])
			return
		}

		if (signalLoading) setLoadingDetail(true)

		try {
			const [userDetail, billingDetail, audit] = await Promise.all([
				adminApi.getUser(userId),
				adminApi.getBilling(userId),
				adminApi.getUserAudit(userId, 25),
			])

			setSelectedUser(userDetail)
			setBilling(billingDetail)
			setAuditRows(audit)
			setError(null)
		} catch (err) {
			if (err.response?.status === 403) {
				setError('You are authenticated, but this account does not have administrator access.')
			} else {
				setError(err.response?.data?.message ?? 'Could not load user detail.')
			}
		} finally {
			if (signalLoading) setLoadingDetail(false)
		}
	}, [])

	// Whenever the selected user changes (clicking a different row in the list,
	// or the initial/search-driven auto-selection), (re)load that user's detail,
	// billing, and audit panels. Without this effect the right-hand panel only
	// ever gets populated as a side effect of granting family access, so it
	// stays stuck on whichever user was last mutated, and a fresh page load
	// never populates it at all.
	useEffect(() => {
		let cancelled = false

		async function run() {
			if (!selectedUserId) {
				if (!cancelled) {
					setSelectedUser(null)
					setBilling(null)
					setAuditRows([])
				}
				return
			}

			if (!cancelled) setLoadingDetail(true)
			await loadSelectedUserDetail(selectedUserId, { signalLoading: false })
			if (!cancelled) setLoadingDetail(false)
		}

		run()

		return () => {
			cancelled = true
		}
	}, [selectedUserId, loadSelectedUserDetail])

	const loadMetrics = useCallback(async () => {
		setLoadingMetrics(true)

		try {
			const overview = await adminApi.getOverviewMetrics()
			setMetrics(overview)
			setError(null)
		} catch (err) {
			if (err.response?.status === 403) {
				setError('You are authenticated, but this account does not have administrator access.')
			} else {
				setError(err.response?.data?.message ?? 'Could not load admin overview metrics.')
			}
		} finally {
			setLoadingMetrics(false)
		}
	}, [])

	async function handleSearchSubmit(event) {
		event.preventDefault()
		setLoadingUsers(true)
		setSelectedUserId(null)
		setSelectedUser(null)
		setBilling(null)
		setAuditRows([])
		await searchUsers(query)
	}

	async function handleGrantFamilyAccess(body) {
		if (!selectedUserId) return

		setSavingFamilyAccess(true)
		setError(null)
		setSuccess(null)

		try {
			await adminApi.grantFamilyAccess(selectedUserId, body)
			await loadSelectedUserDetail(selectedUserId)
			setSuccess('Family access granted.')
			setFamilyDialogOpen(false)
		} catch (err) {
			setError(err.response?.data?.message ?? 'Could not grant family access.')
		} finally {
			setSavingFamilyAccess(false)
		}
	}

	async function handleExtendTrial(body) {
		if (!selectedUserId) return

		setSavingTrial(true)
		setError(null)
		setSuccess(null)

		try {
			await adminApi.extendTrial(selectedUserId, body)
			await loadSelectedUserDetail(selectedUserId)
			setSuccess('Trial extended.')
			setExtendTrialDialogOpen(false)
		} catch (err) {
			setError(err.response?.data?.message ?? 'Could not extend trial.')
		} finally {
			setSavingTrial(false)
		}
	}

	async function handleHardDeleteUser(body) {
		if (!selectedUserId) return

		setDeletingUser(true)
		setError(null)
		setSuccess(null)

		try {
			await adminApi.hardDeleteUser(selectedUserId, body.reason)

			// Remove the deleted user from the list and clear selection
			setUsers((prev) => prev.filter((u) => u.id !== selectedUserId))
			setSelectedUserId(null)
			setSelectedUser(null)
			setBilling(null)
			setAuditRows([])
			setSuccess('User permanently deleted.')
			setHardDeleteDialogOpen(false)
		} catch (err) {
			setError(err.response?.data?.message ?? 'Could not delete user.')
		} finally {
			setDeletingUser(false)
		}
	}

	const userForHeader = selectedUser ?? selectedSummary

	return (
		<Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
			<AppBar position="static" elevation={0}>
				<Toolbar sx={{ minHeight: '54px !important', px: 2, gap: 1.5 }}>
					<LogoMark size={34} />
					<AdminPanelSettingsIcon fontSize="small" />
					<Box sx={{ minWidth: 0, flex: 1 }}>
						<Typography variant="h6" sx={{ fontWeight: 750, lineHeight: 1 }}>
							NovelKMS Admin
						</Typography>
						<Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.72)' }}>
							Support console
						</Typography>
					</Box>
					<Button
						color="inherit"
						size="small"
						startIcon={<ArrowBackIcon />}
						onClick={() => { window.location.href = '..' }}
					>
						Workspace
					</Button>
				</Toolbar>
			</AppBar>

			<Box sx={{ px: 2, pt: 2 }}>
				<Paper variant="outlined" sx={{ borderRadius: 2, bgcolor: 'background.paper' }}>
					<Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 1 }}>
						<Tab value="overview" label="Overview" />
						<Tab value="users" label="Users" />
					</Tabs>
				</Paper>
			</Box>

			{tab === 'overview' ? (
				<Box sx={{ p: 2 }}>
					<Stack spacing={2}>
						{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
						{success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}
						<AdminOverview metrics={metrics} loading={loadingMetrics} onRefresh={loadMetrics} />
					</Stack>
				</Box>
			) : (
				<Box sx={{ p: 2, display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '360px 1fr' }, gap: 2 }}>				<Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden', bgcolor: 'background.paper' }}>
					<Box component="form" onSubmit={handleSearchSubmit} sx={{ p: 2 }}>
						<Stack spacing={1.5}>
							<Typography variant="subtitle1" sx={{ fontWeight: 750 }}>
								User search
							</Typography>
							<TextField
								value={query}
								onChange={(event) => setQuery(event.target.value)}
								placeholder="Email, name, user id, Stripe id"
								size="small"
								fullWidth
							/>
							<Button
								type="submit"
								variant="contained"
								startIcon={loadingUsers ? <CircularProgress size={16} /> : <SearchIcon />}
								disabled={loadingUsers}
							>
								Search
							</Button>
						</Stack>
					</Box>

					<Divider />

					<Box sx={{ maxHeight: 'calc(100vh - 220px)', overflow: 'auto' }}>
						{loadingUsers && users.length === 0 ? (
							<Box sx={{ p: 3, display: 'grid', placeItems: 'center' }}>
								<CircularProgress />
							</Box>
						) : (
							<List dense disablePadding>
								{users.map((user) => (
									<ListItemButton
										key={user.id}
										selected={user.id === selectedUserId}
										onClick={() => setSelectedUserId(user.id)}
									>
										<ListItemText
											primary={user.displayName || user.emailAddress}
											secondary={
												<>
													{user.emailAddress}
													<br />
													{user.id}
												</>
											}
											slotProps={{
												primary: {
													fontWeight: 650,
													noWrap: true,
												},
												secondary: {
													component: 'span',
													sx: { overflowWrap: 'anywhere' },
												},
											}}
										/>									</ListItemButton>
								))}
								{!loadingUsers && users.length === 0 && (
									<Box sx={{ p: 2 }}>
										<Typography variant="body2" color="text.secondary">
											No users found.
										</Typography>
									</Box>
								)}
							</List>
						)}
					</Box>
				</Paper>

					<Box>
						<Stack spacing={2}>
							{error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
							{success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

							{!selectedUserId ? (
								<Paper variant="outlined" sx={{ p: 3, borderRadius: 2 }}>
									<Typography variant="body1" color="text.secondary">
										Search for a user to begin.
									</Typography>
								</Paper>
							) : loadingDetail && !selectedUser ? (
								<Paper variant="outlined" sx={{ p: 4, borderRadius: 2, display: 'grid', placeItems: 'center' }}>
									<CircularProgress />
								</Paper>
							) : (
								<>
									<Section
										title="User"
										actions={
											<Button
												size="small"
												variant="outlined"
												color="error"
												startIcon={<DeleteForeverIcon />}
												onClick={() => setHardDeleteDialogOpen(true)}
												disabled={!selectedUserId}
											>
												Delete user
											</Button>
										}
									>
										<Stack
											direction="row"
											spacing={1}
											useFlexGap
											sx={{ alignItems: 'center', flexWrap: 'wrap' }}
										>
											<Typography variant="h5" sx={{ fontWeight: 750 }}>
												{valueOrDash(userForHeader?.displayName)}
											</Typography>
											<Chip size="small" label={valueOrDash(userForHeader?.status)} color={statusColor(userForHeader?.status)} />
											{userForHeader?.roles?.map((role) => (
												<Chip
													key={role}
													size="small"
													label={role}
													color={roleColor(role)}
													variant={roleColor(role) === 'default' ? 'outlined' : 'filled'}
												/>
											))}
										</Stack>

										<Divider />

										<DetailRow label="User ID" value={userForHeader?.id} />
										<DetailRow label="Email" value={userForHeader?.emailAddress} />
										<DetailRow label="Normalized email" value={userForHeader?.normalizedEmail} />
										<DetailRow label="Created" value={formatDate(userForHeader?.createdAt)} />
										<DetailRow label="Last login" value={formatDate(userForHeader?.lastLoginAt)} />
									</Section>

									<Section
										title="Billing"
										actions={
											<Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
												<Button
													size="small"
													variant="outlined"
													startIcon={<MoreTimeIcon />}
													onClick={() => setExtendTrialDialogOpen(true)}
													disabled={
														!selectedUserId ||
														billing?.subscription?.status === 'family' ||
														billing?.subscription?.status === 'active' ||
														billing?.subscription?.status === 'active_canceling'
													}
												>
													Extend trial
												</Button>
												<Button
													size="small"
													variant="contained"
													startIcon={<FamilyRestroomIcon />}
													onClick={() => setFamilyDialogOpen(true)}
													disabled={!selectedUserId || billing?.familyAccess}
												>
													Grant family access
												</Button>
											</Stack>
										}
									>
										{billing ? (
											<>
												<Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
													<Chip
														label={billing.subscription?.status ?? 'no subscription'}
														color={statusColor(billing.subscription?.status)}
													/>
													<Chip
														label={`Access: ${billing.hasAccess ? 'yes' : 'no'}`}
														color={billing.hasAccess ? 'success' : 'default'}
														variant={billing.hasAccess ? 'filled' : 'outlined'}
													/>
													<Chip label={`Reason: ${billing.accessReason}`} variant="outlined" />
												</Stack>

												<BillingFlags billing={billing} />

												<Divider />

												<DetailRow label="Plan" value={billing.subscription?.planKey} />
												<DetailRow label="Stripe customer" value={billing.subscription?.stripeCustomerId} />
												<DetailRow label="Stripe subscription" value={billing.subscription?.stripeSubscriptionId} />
												<DetailRow label="Current period end" value={formatDate(billing.subscription?.currentPeriodEnd)} />
												<DetailRow label="Trial end" value={formatDate(billing.subscription?.trialEnd)} />
												<DetailRow label="Last payment failed" value={formatDate(billing.subscription?.lastPaymentFailedAt)} />
												<DetailRow label="Evaluated" value={formatDate(billing.evaluatedAt)} />
											</>
										) : (
											<Typography variant="body2" color="text.secondary">
												No billing detail loaded.
											</Typography>
										)}
									</Section>

									<Section title="Usage">
										<UsageSummary usage={selectedUser?.usage} />
									</Section>

									<Section title="Audit">
										<AuditList rows={auditRows} />
									</Section>
								</>
							)}
						</Stack>
					</Box>
				</Box>
			)}

			{familyDialogOpen && (
				<GrantFamilyAccessDialog
					open={familyDialogOpen}
					user={userForHeader}
					saving={savingFamilyAccess}
					onClose={() => setFamilyDialogOpen(false)}
					onSubmit={handleGrantFamilyAccess}
				/>
			)}

			{extendTrialDialogOpen && (
				<ExtendTrialDialog
					open={extendTrialDialogOpen}
					user={userForHeader}
					billing={billing}
					saving={savingTrial}
					onClose={() => setExtendTrialDialogOpen(false)}
					onSubmit={handleExtendTrial}
				/>
			)}

			{hardDeleteDialogOpen && (
				<HardDeleteUserDialog
					open={hardDeleteDialogOpen}
					user={userForHeader}
					saving={deletingUser}
					onClose={() => setHardDeleteDialogOpen(false)}
					onSubmit={handleHardDeleteUser}
				/>
			)}
		</Box>
	)
}