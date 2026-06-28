import {
	Alert,
	Box,
	Button,
	Card,
	CardContent,
	Chip,
	CircularProgress,
	Stack,
	Typography,
} from '@mui/material'
import { useBillingPortal, useBillingStatus, useCheckout } from '../../hooks/useBilling'

function statusLabel(status) {
	switch (status) {
		case 'active':
			return 'Active'
		case 'trialing':
			return 'Trial'
		case 'family':
			return 'Complimentary access'
		case 'past_due':
			return 'Past due'
		case 'canceled':
			return 'Canceled'
		case 'unpaid':
			return 'Unpaid'
		case 'incomplete':
			return 'Incomplete'
		case 'incomplete_expired':
			return 'Expired'
		case 'paused':
			return 'Paused'
		case 'none':
		default:
			return 'Not subscribed'
	}
}

function statusColor(status, hasAccess) {
	if (status === 'family') return 'secondary'
	if (hasAccess) return 'success'
	if (status === 'past_due') return 'warning'
	return 'default'
}

function formatDate(value) {
	if (!value) return null
	try {
		return new Intl.DateTimeFormat(undefined, {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
		}).format(new Date(value))
	} catch {
		return null
	}
}

export default function BillingPanel() {
	const statusQuery = useBillingStatus()
	const checkout = useCheckout()
	const portal = useBillingPortal()

	const status = statusQuery.data
	const hasAccess = !!status?.hasAccess
	const familyAccess = !!status?.familyAccess
	const hasStripeCustomer = !!status?.hasStripeCustomer
	const currentPeriodEnd = formatDate(status?.currentPeriodEnd)
	const trialEnd = formatDate(status?.trialEnd)

	const busy = checkout.isPending || portal.isPending

	return (
		<Card variant="outlined">
			<CardContent>
				<Stack spacing={2}>
					<Box>
						<Typography variant="h6">Billing</Typography>
						<Typography variant="body2" color="text.secondary">
							Manage your NovelKMS subscription and access status.
						</Typography>
					</Box>

					{statusQuery.isLoading && (
						<Stack direction="row" spacing={1} alignItems="center">
							<CircularProgress size={18} />
							<Typography variant="body2">Loading billing status…</Typography>
						</Stack>
					)}

					{statusQuery.isError && (
						<Alert severity="error">
							Billing status could not be loaded.
						</Alert>
					)}

					{status && (
						<>
							<Stack direction="row" spacing={1} alignItems="center">
								<Typography variant="body2" color="text.secondary">
									Status
								</Typography>
								<Chip
									size="small"
									label={statusLabel(status.status)}
									color={statusColor(status.status, hasAccess)}
								/>
							</Stack>

							{hasAccess && status.status === 'active' && (
								<Alert severity="success">
									Your subscription is active.
									{currentPeriodEnd ? ` Current period ends ${currentPeriodEnd}.` : ''}
								</Alert>
							)}

							{hasAccess && status.status === 'trialing' && (
								<Alert severity="info">
									Your trial is active.
									{trialEnd ? ` Trial ends ${trialEnd}.` : ''}
								</Alert>
							)}

							{familyAccess && (
								<Alert severity="success">
									You have complimentary access.
								</Alert>
							)}

							{status.status === 'past_due' && hasAccess && (
								<Alert severity="warning">
									There is a payment issue, but access is still active for now.
									{currentPeriodEnd ? ` Current period ends ${currentPeriodEnd}.` : ''}
								</Alert>
							)}

							{!hasAccess && (
								<Alert severity="warning">
									A subscription is required to use NovelKMS.
								</Alert>
							)}

							{status.cancelAtPeriodEnd && (
								<Alert severity="info">
									Your subscription is scheduled to cancel at the end of the current period.
									{currentPeriodEnd ? ` Access continues until ${currentPeriodEnd}.` : ''}
								</Alert>
							)}

							<Stack direction="row" spacing={1}>
								{!hasAccess && (
									<Button
										variant="contained"
										onClick={() => checkout.mutate()}
										disabled={busy}
									>
										Subscribe
									</Button>
								)}

								{hasAccess && hasStripeCustomer && !familyAccess && (
									<Button
										variant="outlined"
										onClick={() => portal.mutate()}
										disabled={busy}
									>
										Manage billing
									</Button>
								)}

								<Button
									variant="text"
									onClick={() => statusQuery.refetch()}
									disabled={statusQuery.isFetching || busy}
								>
									Refresh
								</Button>
							</Stack>

							{checkout.isError && (
								<Alert severity="error">
									Checkout could not be started.
								</Alert>
							)}

							{portal.isError && (
								<Alert severity="error">
									The billing portal could not be opened.
								</Alert>
							)}
						</>
					)}
				</Stack>
			</CardContent>
		</Card>
	)
}