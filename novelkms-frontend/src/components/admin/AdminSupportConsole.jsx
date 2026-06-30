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
	TextField,
	Toolbar,
	Typography,
} from '@mui/material'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom'
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

export default function AdminSupportConsole() {
	const [query, setQuery] = useState('')
	const [users, setUsers] = useState([])
	const [selectedUserId, setSelectedUserId] = useState(null)
	const [selectedUser, setSelectedUser] = useState(null)
	const [billing, setBilling] = useState(null)
	const [auditRows, setAuditRows] = useState([])
	const [loadingUsers, setLoadingUsers] = useState(true)
	const [loadingDetail] = useState(false)
	const [savingFamilyAccess, setSavingFamilyAccess] = useState(false)
	const [familyDialogOpen, setFamilyDialogOpen] = useState(false)
	const [error, setError] = useState(null)
	const [success, setSuccess] = useState(null)

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

			const [userDetail, billingDetail, audit] = await Promise.all([
				adminApi.getUser(selectedUserId),
				adminApi.getBilling(selectedUserId),
				adminApi.getUserAudit(selectedUserId, 25),
			])

			setSelectedUser(userDetail)
			setBilling(billingDetail)
			setAuditRows(audit)
			setSuccess('Family access granted.')
			setFamilyDialogOpen(false)
		} catch (err) {
			setError(err.response?.data?.message ?? 'Could not grant family access.')
		} finally {
			setSavingFamilyAccess(false)
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
						onClick={() => { window.location.href = '/' }}
					>
						Workspace
					</Button>
				</Toolbar>
			</AppBar>

			<Box sx={{ p: 2, display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '360px 1fr' }, gap: 2 }}>
				<Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden', bgcolor: 'background.paper' }}>
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
								<Section title="User">
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
										<Button
											size="small"
											variant="contained"
											startIcon={<FamilyRestroomIcon />}
											onClick={() => setFamilyDialogOpen(true)}
											disabled={!selectedUserId || billing?.familyAccess}
										>
											Grant family access
										</Button>
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

			{familyDialogOpen && (
				<GrantFamilyAccessDialog
					open={familyDialogOpen}
					user={userForHeader}
					saving={savingFamilyAccess}
					onClose={() => setFamilyDialogOpen(false)}
					onSubmit={handleGrantFamilyAccess}
				/>
			)}
		</Box>
	)
}