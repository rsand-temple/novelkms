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
					justifyContent: 'center',
					gap: 3,
					width: '58%',
					px: { md: 6, lg: 8, xl: 10 },
					py: 5,
				}}
			>
				<Box sx={{ width: '100%' }}>
					<LogoLockup width={340} />

					<Typography
						variant="h4"
						sx={{
							mt: 2.5,
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

				<Stack
					spacing={2}
					sx={{
						width: '100%',
						maxWidth: 'none',
						pr: { md: 1, lg: 2 },
					}}
				>
					{FEATURES.map((f) => (
						<FeatureRow key={f.title} {...f} />
					))}
				</Stack>

				<Box sx={{ width: '100%', maxWidth: 760 }}>
					<EditorMockup />
				</Box>
			</Box>

			{/* Sign-in column */}
			<Box
				sx={{
					flex: 1,
					display: 'grid',
					placeItems: 'center',
					p: 2,
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
							<Typography variant="h5" fontWeight={700}>
								Start your NovelKMS trial
							</Typography>
							<Typography color="text.secondary">
								Sign in to create your account and continue to your manuscript workspace.
							</Typography>
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

						<Stack spacing={1.25}>
							<Typography color="text.secondary" sx={{ fontSize: '0.875rem' }}>
								Founders access is open: start with a 14-day trial, then continue for $9/month as an early
								subscriber.
							</Typography>

							<Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, color: 'text.secondary' }}>
								<LogoMark size={16} onDark={false} />
								<Typography variant="caption">
									Your manuscript stays yours. NovelKMS is built around author control, portability, and
									structured revision. AI features use your own OpenAI key.
								</Typography>
							</Box>
						</Stack>
					</Stack>
				</Paper>
			</Box>
		</Box>
	)
}