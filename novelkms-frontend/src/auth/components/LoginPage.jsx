import { Box, Button, Divider, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { authApi } from '../../api/auth'
import { LogoLockup, LogoMark } from '../../components/branding/Logo'
import EditorMockup from '../../components/marketing/EditorMockup'

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
		title: 'Review findings like a revision inbox',
		body: 'Run chapter reviews to catch continuity errors or plot holes, then triage each finding as Done, Dismissed, or Deferred.',
	},
	{
		title: 'Carry story context forward',
		body: 'Build chapter memory so later reviews understand what happened earlier in the manuscript.',
	},
	{
		title: 'Catch the big-picture problems',
		body: 'Surface continuity breaks, pacing dips, tone drift, genre mismatch, and character arc issues before beta readers see them.',
	},
	{
		title: 'Generate clean story summaries',
		body: 'Create chapter summaries and aggregate them into a full-book synopsis for revision, querying, and orientation.',
	},
	{
		title: 'Keep world knowledge beside the page',
		body: 'Store characters, canon, timeline notes, voice sheets, and research near the manuscript they support.',
	},
	{
		title: 'Stay in control of your prose',
		body: 'Import, export, revise, and review freely. NovelKMS analyzes your manuscript; it does not rewrite it for you.',
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
		<Box sx={{ minHeight: '100vh', display: 'flex', bgcolor: 'background.paper' }}>
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
						A structured editorial workspace for long manuscripts. AI review. No ghostwriting.
					</Typography>

					<Typography sx={{ mt: 2, color: 'text.secondary', fontSize: '1rem', maxWidth: 860 }}>
						NovelKMS bridges the gap between chaotic drafting and elite developmental editing.
						Keep your manuscript, continuity notes, and deep story memory in one structured workspace
						built specifically for long-form fiction authors.
					</Typography>
				</Box>

				<Box
					sx={{
						display: 'grid',
						gridTemplateColumns: { md: '1fr', lg: '1fr 1fr' },
						columnGap: 3,
						rowGap: 1.75,
						width: '100%',
						maxWidth: 'none',
						pr: { md: 1, lg: 2 },
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
						<Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1.25 }}>
							<LogoMark size={32} onDark={false} />
							<Typography
								sx={{
									fontFamily: 'Georgia, "Times New Roman", serif',
									fontWeight: 700,
									color: 'primary.main',
								}}
							>
								NovelKMS
							</Typography>
						</Box>

						<Box>
							<Stack spacing={1.25}>
								<Typography variant="h5" fontWeight={700}>
									Start your NovelKMS trial
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem' }}>
									Best for novelists revising complex drafts, series fiction, historical fiction, speculative fiction, and continuity-heavy manuscripts.
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem' }}>
									<strong>Founders access is open!</strong> Start your 14-day trial, then continue for $9/month
									as an early subscriber. No credit card required!
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem' }}>
									We charge a flat fee and never markup AI costs.
									Plug in your own API key (like OpenAI or Anthropic)
									to edit with no hidden upcharge.
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem', fontStyle: 'italic' }}>
									Sign in below and meet your manuscript workspace!
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
	)
}