import { Box, Button, Divider, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { authApi } from '../../api/auth'
import { LogoLockup, LogoMark } from '../../components/branding/Logo'
import EditorMockup from '../../components/marketing/EditorMockup'

function SpriteIcon({ id, size = 20 }) {
	return (
		<Box component="svg" width={size} height={size} sx={{ flexShrink: 0 }}>
			<use href={`/icons.svg#${id}`} />
		</Box>
	)
}

const PROVIDERS = [
	{ key: 'google', label: 'Continue with Google', icon: 'google-icon' },
	{ key: 'github', label: 'Continue with GitHub', icon: 'github-icon' },
	{ key: 'meta', label: 'Continue with Facebook', icon: 'facebook-icon' },
]

const FEATURES = [
	{
		title: 'Edits round-trip, every time',
		body: 'Every chapter and scene keeps a permanent ID, so AI-assisted edits land back in the right place instead of overwriting your draft blind.',
	},
	{
		title: 'Characters, canon, and timeline \u2014 built in',
		body: 'A Codex sits right next to the page you\u2019re writing, so continuity details are one click away instead of three other apps.',
	},
	{
		title: 'See the page, not just the text',
		body: 'Page-accurate layout, cover and part templates, and one-click Word export show you the book readers will actually hold.',
	},
	{
		title: 'Search that doesn\u2019t lose your place',
		body: 'Find and replace across a scene, a chapter, or the whole manuscript \u2014 without ever leaving the page you\u2019re on.',
	},
]

function FeatureRow({ title, body }) {
	return (
		<Box sx={{ display: 'flex', gap: 1.5 }}>
			<Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: 'secondary.main', mt: 0.9, flexShrink: 0 }} />
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
	const [providers, setProviders] = useState({ google: false, github: false, meta: false })
	useEffect(() => {
		authApi.providers().then(setProviders).catch(() => {})
	}, [])

	return (
		<Box sx={{ minHeight: '100vh', display: 'flex', bgcolor: 'background.paper' }}>
			{/* Marketing column - desktop only */}
			<Box
				sx={{
					display: { xs: 'none', md: 'flex' },
					flexDirection: 'column',
					justifyContent: 'center',
					gap: 4,
					width: '58%',
					px: { md: 6, lg: 10 },
					py: 6,
				}}
			>
				<Box sx={{ maxWidth: 480 }}>
					<LogoLockup width={340} />
					<Typography sx={{ mt: 2, color: 'text.secondary', fontSize: '1rem' }}>
						NovelKMS (Novel Knowledge Management System) keeps your manuscript, characters, canon, and timeline in one place —
						built for authors who want structure without losing the words.
					</Typography>
				</Box>

				<Stack spacing={2.25} sx={{ maxWidth: 480 }}>
					{FEATURES.map((f) => (
						<FeatureRow key={f.title} {...f} />
					))}
				</Stack>

				<EditorMockup />
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
					sx={{ width: '100%', maxWidth: 420, p: 4, border: { md: 'none' }, boxShadow: { md: 'none' } }}
				>
					<Stack spacing={3}>
						<Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1.25 }}>
							<LogoMark size={32} onDark={false} />
							<Typography
								sx={{ fontFamily: 'Georgia, "Times New Roman", serif', fontWeight: 700, color: 'primary.main' }}
							>
								NovelKMS
							</Typography>
						</Box>
						<Box>
							<Typography variant="h5" fontWeight={700}>
								Welcome back
							</Typography>
							<Typography color="text.secondary">Sign in to continue to your manuscripts.</Typography>
						</Box>

						<Stack spacing={1.5}>
							{PROVIDERS.map((p) => (
								<Button
									key={p.key}
									variant="outlined"
									size="large"
									disabled={!providers[p.key]}
									href={authApi.startUrl(p.key)}
									startIcon={<SpriteIcon id={p.icon} />}
									sx={{ justifyContent: 'flex-start', pl: 2, color: 'text.primary', borderColor: 'divider' }}
								>
									{p.label}
								</Button>
							))}
						</Stack>

						<Divider />

						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
							<LogoMark size={16} onDark={false} />
							<Typography variant="caption">
								NovelKMS is a personal, single-author workspace — sign-in just confirms it's you.
							</Typography>
						</Box>
					</Stack>
				</Paper>
			</Box>
		</Box>
	)
}
