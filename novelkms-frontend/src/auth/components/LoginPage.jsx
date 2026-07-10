import { Box, Button, Divider, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { authApi } from '../../api/auth'
import { LogoLockup } from '../../components/branding/Logo'
import EditorMockup from '../../components/marketing/EditorMockup'
import SiteHeader from '../../components/marketing/SiteHeader'

function ProviderIcon({ src, size = 20 }) {
	return (
		<Box
			component="img"
			src={src}
			alt=""
			aria-hidden="true"
			sx={{ width: size, height: size, flexShrink: 0, display: 'block' }}
		/>
	)
}

const appAsset = (path) => `${import.meta.env.BASE_URL}${path.replace(/^\/+/, '')}`

const PROVIDERS = [
	{ key: 'google', label: 'Continue with Google', icon: appAsset('auth-icons/google.svg') },
	{ key: 'microsoft', label: 'Continue with Microsoft', icon: appAsset('auth-icons/microsoft.svg') },
	{ key: 'apple', label: 'Continue with Apple', icon: appAsset('auth-icons/apple.svg') },
	{ key: 'meta', label: 'Continue with Facebook', icon: appAsset('auth-icons/facebook.svg') },
	{ key: 'github', label: 'Continue with GitHub', icon: appAsset('auth-icons/github.svg') },
]

const FEATURES = [
	{
		title: 'Straight to your project',
		body: 'Sign in and land directly in your manuscript tree — no onboarding maze between you and your book.',
	},
	{
		title: 'One account, no new password',
		body: 'Use the Google, Microsoft, Apple, Facebook, or GitHub account you already have.',
	},
	{
		title: 'See it in action first?',
		body: 'Features and AI Review (above) cover what the editor and review workflow actually do.',
	},
]

function FeatureRow({ title, body }) {
	return (
		<Box sx={{ display: 'flex', gap: 1.5 }}>
			<Box
				sx={{
					width: 7,
					height: 7,
					borderRadius: '50%',
					bgcolor: 'secondary.main',
					mt: 0.9,
					flexShrink: 0,
				}}
			/>
			<Box>
				<Typography sx={{ fontWeight: 650, fontSize: '0.95rem' }}>{title}</Typography>
				<Typography color="text.secondary" sx={{ fontSize: '0.875rem' }}>
					{body}
				</Typography>
			</Box>
		</Box>
	)
}

export default function LoginPage() {
	const [providers, setProviders] = useState({
		google: false,
		microsoft: false,
		apple: false,
		meta: false,
		github: false,
	})
	const [providersLoaded, setProvidersLoaded] = useState(false)

	useEffect(() => {
		let alive = true

		authApi.providers()
			.then((result) => {
				if (alive) setProviders(result)
			})
			.catch(() => { })
			.finally(() => {
				if (alive) setProvidersLoaded(true)
			})

		return () => {
			alive = false
		}
	}, [])

	const enabledProviders = useMemo(
		() => PROVIDERS.filter((p) => providers[p.key]),
		[providers]
	)

	return (
		<Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.paper' }}>
			<SiteHeader />

			<Box sx={{ flex: 1, display: 'flex' }}>
				{/* Marketing column - desktop only */}
				<Box
					sx={{
						display: { xs: 'none', md: 'flex' },
						flexDirection: 'column',
						justifyContent: 'flex-start',
						gap: 2.5,
						width: '58%',
						px: { md: 6, lg: 8, xl: 10 },
						pt: { md: 5, lg: 6 },
						pb: 4,
					}}
				>
					<Box sx={{ width: '100%' }}>
						<LogoLockup width={260} />

						<Typography
							variant="h4"
							sx={{
								mt: 1.75,
								fontWeight: 750,
								lineHeight: 1.15,
								color: 'text.primary',
								maxWidth: 760,
							}}
						>
							Sign in to your manuscript workspace.
						</Typography>

						<Typography sx={{ mt: 2, color: 'text.secondary', fontSize: '1rem', maxWidth: 860 }}>
							NovelKMS keeps your manuscript, Codex, and AI review history together in one place.
							Haven't seen what it does yet? Features and AI Review are in the menu above.
						</Typography>
					</Box>

					<Box
						sx={{
							display: 'flex',
							flexDirection: 'column',
							gap: 1.75,
							width: '100%',
							maxWidth: 620,
						}}
					>
						{FEATURES.map((f) => (
							<FeatureRow key={f.title} {...f} />
						))}
					</Box>

					<Box sx={{ width: '100%', maxWidth: 760, mt: 0.5 }}>
						<EditorMockup />
					</Box>
				</Box>

				{/* Sign-in column */}
				<Box
					sx={{
						flex: 1,
						display: 'flex',
						alignItems: { xs: 'center', md: 'flex-start' },
						justifyContent: 'center',
						p: 2,
						pt: { xs: 2, md: 20, lg: 22 },
						borderLeft: { md: '1px solid' },
						borderColor: { md: 'divider' },
					}}
				>
				<Paper
					elevation={3}
					sx={{
						width: '100%',
						maxWidth: 420,
						p: 4,
						border: { md: 'none' },
						boxShadow: { md: 'none' },
					}}
				>
					<Stack spacing={3}>
						<Box>
							<Stack spacing={1.25}>
								<Typography variant="h5" fontWeight={700}>
									Start your 14-day trial
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem' }}>
									For novelists working on series fiction, historical fiction, speculative fiction,
									or any other continuity-heavy manuscript.
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem' }}>
									No credit card required. Then $9/month as an early subscriber —{' '}
									<Box
										component="a"
										href="/pricing"
										sx={{
											color: 'primary.main',
											fontWeight: 650,
											textDecoration: 'none',
											'&:hover': { textDecoration: 'underline' },
										}}
									>
										see full pricing
									</Box>
									.
								</Typography>
								<Box
									component="a"
									href="/faq"
									sx={{
										color: 'primary.main',
										fontWeight: 650,
										textDecoration: 'none',
										'&:hover': { textDecoration: 'underline' },
									}}
								>
									Questions? Read our FAQ.
								</Box>
							</Stack>
						</Box>

						<Stack spacing={1.5}>
							{enabledProviders.map((p) => (
								<Button
									key={p.key}
									variant="outlined"
									size="large"
									href={authApi.startUrl(p.key)}
									startIcon={<ProviderIcon src={p.icon} />}
									sx={{
										justifyContent: 'flex-start',
										pl: 2,
										color: 'text.primary',
										borderColor: 'divider',
									}}
								>
									{p.label}
								</Button>
							))}

							{providersLoaded && enabledProviders.length === 0 && (
								<Typography color="text.secondary" sx={{ fontSize: '0.875rem' }}>
									Sign-in providers are temporarily unavailable. Please try again shortly.
								</Typography>
							)}
						</Stack>

						<Divider />

						<Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
							<Box
								component="a"
								href="/privacy"
								sx={{
									textDecoration: 'none',
									'&:hover': { textDecoration: 'underline' },
								}}
							>
								Privacy Policy
							</Box>
							{', '}
							<Box
								component="a"
								href="/terms"
								sx={{
									textDecoration: 'none',
									'&:hover': { textDecoration: 'underline' },
								}}
							>
								Terms of Service
							</Box>
						</Typography>
					</Stack>
				</Paper>
			</Box>
			</Box>
		</Box>
	)
}