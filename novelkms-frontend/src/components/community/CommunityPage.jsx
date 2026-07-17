import { useState } from 'react'
import {
	AppBar,
	Box,
	Button,
	Paper,
	Stack,
	Tab,
	Tabs,
	Toolbar,
	Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RateReviewOutlinedIcon from '@mui/icons-material/RateReviewOutlined'
import ReviewProfilePanel from './ReviewProfilePanel'
import MyRequestsPanel from './MyRequestsPanel'
import ReviewQueuePanel from './ReviewQueuePanel'
import { LogoMark } from '../branding/Logo'

/**
 * The human-review network's top-level surface, at /app/community.
 *
 * It deliberately sits outside the manuscript workspace rather than inside the
 * nav tree: review packages are derived, published snapshots, not editable
 * manuscript children, and putting them in the tree would imply otherwise.
 *
 * The tab set is the full Phase 1 shape. Only "My Profile" is built (slice 1A) —
 * the rest render an honest placeholder rather than a mock, so the roadmap is
 * visible without anything pretending to work.
 */
const TABS = [
	{ key: 'profile',  label: 'My Profile' },
	{ key: 'queue',    label: 'Review Queue' },
	{ key: 'requests', label: 'My Requests' },
	{ key: 'writing',  label: "Reviews I'm Writing", soon: 'Drafts of reviews you have started.' },
	{ key: 'received', label: 'Reviews Received',    soon: 'Feedback other writers have sent you.' },
]

// The active tab is deep-linkable: /app/community?tab=requests lands directly on
// My Requests. An unknown or absent value falls back to My Profile.
function initialTab() {
	if (typeof window === 'undefined') return 'profile'
	const requested = new URLSearchParams(window.location.search).get('tab')
	return TABS.some(t => t.key === requested) ? requested : 'profile'
}

function ComingSoon({ label, description }) {
	return (
		<Paper
			variant="outlined"
			sx={{
				p: 5,
				display: 'flex',
				flexDirection: 'column',
				alignItems: 'center',
				textAlign: 'center',
				gap: 1,
				maxWidth: 520,
			}}
		>
			<RateReviewOutlinedIcon sx={{ fontSize: 34, color: 'text.disabled' }} />
			<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
				{label}
			</Typography>
			<Typography variant="body2" color="text.secondary">
				{description}
			</Typography>
			<Typography variant="caption" color="text.disabled" sx={{ mt: 1 }}>
				Not built yet.
			</Typography>
		</Paper>
	)
}

export default function CommunityPage() {
	const [tab, setTab] = useState(initialTab)
	const active = TABS.find(t => t.key === tab) ?? TABS[0]

	// Keep the tab in the URL (without a history entry) so a refresh or a shared
	// link stays on the same tab.
	const handleTab = (_e, next) => {
		setTab(next)
		if (typeof window !== 'undefined') {
			const url = new URL(window.location.href)
			if (next === 'profile') url.searchParams.delete('tab')
			else url.searchParams.set('tab', next)
			window.history.replaceState(null, '', url)
		}
	}

	return (
		<Box sx={{
			display: 'flex',
			flexDirection: 'column',
			height: '100vh',
			overflow: 'hidden',
			bgcolor: 'background.default',
		}}>
			<AppBar position="static" elevation={0}>
				<Toolbar sx={{ minHeight: '54px !important', px: 2, gap: 1.5 }}>
					<LogoMark size={34} />
					<Typography variant="h6" sx={{ fontWeight: 750, letterSpacing: 0.5, lineHeight: 1 }}>
						Community
					</Typography>
					<Typography
						variant="caption"
						sx={{
							display: { xs: 'none', md: 'block' },
							color: 'rgba(255,255,255,0.62)',
							letterSpacing: 0.25,
						}}
					>
						Human review network
					</Typography>

					<Box sx={{ flexGrow: 1 }} />

					<Button
						color="inherit"
						size="small"
						startIcon={<ArrowBackIcon fontSize="small" />}
						onClick={() => { window.location.href = '/app/' }}
					>
						Back to workspace
					</Button>
				</Toolbar>
			</AppBar>

			<Box sx={{ borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
				<Tabs
					value={tab}
					onChange={handleTab}
					variant="scrollable"
					scrollButtons="auto"
					sx={{ px: 2 }}
				>
					{TABS.map(t => (
						<Tab key={t.key} value={t.key} label={t.label} />
					))}
				</Tabs>
			</Box>

			<Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 3 }}>
				{active.key === 'profile' ? (
					<ReviewProfilePanel />
				) : active.key === 'requests' ? (
					<MyRequestsPanel />
				) : active.key === 'queue' ? (
					<ReviewQueuePanel onGoToProfile={() => handleTab(null, 'profile')} />
				) : (
					<Stack sx={{ alignItems: 'center', pt: 4 }}>
						<ComingSoon label={active.label} description={active.soon} />
					</Stack>
				)}
			</Box>
		</Box>
	)
}
