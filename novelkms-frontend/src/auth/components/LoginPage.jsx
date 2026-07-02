import { Box, Button, Divider, Paper, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useEffect, useMemo, useState } from 'react'
import { authApi } from '../../api/auth'
import { LogoLockup, LogoMark } from '../../components/branding/Logo'
import EditorMockup from '../../components/marketing/EditorMockup'
import FaqPage from '../../public/faq'
import PrivacyPage from '../../public/privacy'
import TermsPage from '../../public/terms'

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

const PROVIDERS = [
	{ key: 'google', label: 'Continue with Google', icon: '/auth-icons/google.svg' },
	{ key: 'microsoft', label: 'Continue with Microsoft', icon: '/auth-icons/microsoft.svg' },
	{ key: 'apple', label: 'Continue with Apple', icon: '/auth-icons/apple.svg' },
	{ key: 'meta', label: 'Continue with Facebook', icon: '/auth-icons/facebook.svg' },
	{ key: 'github', label: 'Continue with GitHub', icon: '/auth-icons/github.svg' },
]

const FEATURES = [
	{
		title: 'Write in scenes, chapters, parts, or the full book',
		body: 'Move naturally through your manuscript while NovelKMS preserves scene-level structure behind the editor.',
	},
	{
		title: 'Keep story knowledge beside the page',
		body: 'Characters, canon, timeline notes, research, and world details stay attached to the project instead of scattered across other tools.',
	},
	{
		title: 'Turn AI feedback into a revision inbox',
		body: 'Run chapter or scene reviews, jump to the relevant passage, and triage each finding as Done, Dismissed, Deferred, or promoted to Codex.',
	},
	{
		title: 'Summarize the book as it grows',
		body: 'Generate chapter summaries and story-so-far memory docs to keep long manuscripts coherent through revision.',
	},
	{
		title: 'Import, export, and stay in control',
		body: 'Bring in DOCX, export your work, and use AI as review support — not as an automatic ghostwriter.',
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
	const location = useLocation()
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

	if (location.pathname === '/faq') {
		return <FaqPage />
	}

	if (location.pathname === '/privacy') {
		return <PrivacyPage />
	}

	if (location.pathname === '/terms') {
		return <TermsPage />
	}

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
						Revise your novel without losing the story around it.
					</Typography>

					<Typography sx={{ mt: 2, color: 'text.secondary', fontSize: '1rem', maxWidth: 860 }}>
						NovelKMS keeps your manuscript, Codex, summaries, continuity notes, and AI review findings in one
						structured workspace — built for authors managing long-form fiction.
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
								<Typography color="text.secondary" sx={{ fontSize: '1rem', fontStyle: 'italic' }}>
									Founders access is open! Start your 14-day trial, then continue for $9/month
									as an early subscriber.
								</Typography>
								<Typography color="text.secondary" sx={{ fontSize: '1rem', fontStyle: 'italic' }}>
									Sign in below and meet your manuscript workspace.
								</Typography>
								<Box
									component={RouterLink}
									to="/faq"
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
								component={RouterLink}
								to="/privacy"
								sx={{
									color: 'primary.main',
									textDecoration: 'none',
									'&:hover': { textDecoration: 'underline' },
								}}
							>
								Privacy Policy
							</Box>
							{', '}
							<Box
								component={RouterLink}
								to="/terms"
								sx={{
									color: 'primary.main',
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