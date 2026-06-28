import {
	Alert,
	Box,
	Button,
	Card,
	CardContent,
	CircularProgress,
	Stack,
	Typography,
} from '@mui/material'
import { useBillingStatus } from '../../hooks/useBilling'

export default function BillingReturnPage({ result }) {
	const statusQuery = useBillingStatus()

	const success = result === 'success'
	const status = statusQuery.data
	const hasAccess = !!status?.hasAccess

	const goToApp = () => {
		window.history.replaceState(null, '', '/')
		window.location.reload()
	}

	const goToBilling = () => {
		window.history.replaceState(null, '', '/')
		window.location.reload()
		// The Billing tab itself will be mounted in Settings; for now return to app.
	}

	return (
		<Box
			sx={{
				minHeight: '100vh',
				display: 'grid',
				placeItems: 'center',
				bgcolor: 'background.default',
				p: 3,
			}}
		>
			<Card variant="outlined" sx={{ width: '100%', maxWidth: 560 }}>
				<CardContent>
					<Stack spacing={2.5}>
						<Box>
							<Typography variant="h5" sx={{ fontWeight: 700 }}>
								{success ? 'Checkout complete' : 'Checkout canceled'}
							</Typography>
							<Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
								{success
									? 'Stripe has completed checkout and NovelKMS is checking your subscription status.'
									: 'No changes were made to your NovelKMS subscription.'}
							</Typography>
						</Box>

						{success && statusQuery.isLoading && (
							<Stack direction="row" spacing={1.25} alignItems="center">
								<CircularProgress size={18} />
								<Typography variant="body2">
									Checking billing status…
								</Typography>
							</Stack>
						)}

						{success && statusQuery.isError && (
							<Alert severity="warning">
								Checkout completed, but billing status could not be refreshed. This may resolve after the Stripe webhook finishes processing.
							</Alert>
						)}

						{success && status && hasAccess && (
							<Alert severity="success">
								Your NovelKMS access is active.
							</Alert>
						)}

						{success && status && !hasAccess && (
							<Alert severity="info">
								Checkout completed, but your access is not active yet. Wait a few seconds and refresh status.
							</Alert>
						)}

						{!success && (
							<Alert severity="info">
								You can return to NovelKMS and subscribe later from Settings → Billing.
							</Alert>
						)}

						<Stack direction="row" spacing={1}>
							<Button variant="contained" onClick={goToApp}>
								Continue to NovelKMS
							</Button>

							{success && (
								<Button
									variant="outlined"
									onClick={() => statusQuery.refetch()}
									disabled={statusQuery.isFetching}
								>
									Refresh status
								</Button>
							)}

							{!success && (
								<Button variant="outlined" onClick={goToBilling}>
									Return to Billing
								</Button>
							)}
						</Stack>
					</Stack>
				</CardContent>
			</Card>
		</Box>
	)
}