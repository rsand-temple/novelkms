import { Box, Typography } from '@mui/material'
import { LogoMark } from '../branding/Logo'

/**
 * Top banner used on public-facing React pages (currently: LoginPage).
 * Mirrors novelkms-static/layouts/partials/header.html + the .site-header /
 * .brand* / .site-nav rules in novelkms-static/assets/css/main.css so the
 * React app's public pages look continuous with the Hugo marketing site.
 *
 * Nav destinations are the Hugo public pages, so plain <a> tags are used
 * (full navigation, not React Router). The Hugo header's "Sign in" nav-cta
 * is omitted here since this header is rendered ON the sign-in page itself.
 */

const NAV_LINKS = [
	{ label: 'Features', href: '/features/' },
	{ label: 'AI Review', href: '/ai-review/' },
	{ label: 'FAQ', href: '/faq/' },
	{ label: 'Pricing', href: '/pricing/' },
]

export default function SiteHeader() {
	return (
		<Box
			component="header"
			sx={{
				width: '100%',
				bgcolor: '#0E1B31', // --nk-navy-dark
				borderBottom: '1px solid rgba(255, 255, 255, 0.11)',
				flexShrink: 0,
			}}
		>
			<Box
				sx={{
					width: '100%',
					maxWidth: 1120,
					mx: 'auto',
					minHeight: 72,
					px: { xs: 2.5, sm: 3 },
					py: { xs: 2, md: 0 },
					display: 'flex',
					flexDirection: { xs: 'column', md: 'row' },
					alignItems: { xs: 'flex-start', md: 'center' },
					justifyContent: 'space-between',
					gap: { xs: 1.5, md: 3 },
				}}
			>
				<Box
					component="a"
					href="/"
					sx={{
						display: 'inline-flex',
						alignItems: 'center',
						gap: 1.75,
						color: '#ffffff',
						textDecoration: 'none',
						minWidth: 0,
						'&:hover': { textDecoration: 'none' },
					}}
				>
					<LogoMark size={42} onDark sx={{ borderRadius: 0 }} />
					<Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.4, minWidth: 0 }}>
						<Typography
							sx={{
								color: '#ffffff',
								fontWeight: 600,
								fontSize: '1.4rem',
								lineHeight: 1,
								letterSpacing: '-0.025em',
								whiteSpace: 'nowrap',
							}}
						>
							NovelKMS
						</Typography>
						<Typography
							sx={{
								color: 'rgba(255, 255, 255, 0.65)',
								fontSize: '0.78rem',
								fontWeight: 400,
								lineHeight: 1,
								letterSpacing: '0.01em',
								whiteSpace: 'nowrap',
								display: { xs: 'none', sm: 'block' },
							}}
						>
							Novel Knowledge Management System
						</Typography>
					</Box>
				</Box>

				<Box
					component="nav"
					aria-label="Main navigation"
					sx={{
						display: 'flex',
						flexWrap: 'wrap',
						alignItems: 'center',
						gap: 2.5,
						fontSize: '0.875rem',
					}}
				>
					{NAV_LINKS.map((link) => (
						<Box
							key={link.href}
							component="a"
							href={link.href}
							sx={{
								color: 'rgba(255, 255, 255, 0.78)',
								fontWeight: 600,
								textDecoration: 'none',
								'&:hover': { color: '#ffffff', textDecoration: 'none' },
							}}
						>
							{link.label}
						</Box>
					))}
				</Box>
			</Box>
		</Box>
	)
}
