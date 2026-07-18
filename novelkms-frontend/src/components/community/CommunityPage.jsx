import { useState } from 'react'
import {
	AppBar,
	Badge,
	Box,
	Button,
	Tab,
	Tabs,
	Toolbar,
	Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ReviewProfilePanel from './ReviewProfilePanel'
import MyRequestsPanel from './MyRequestsPanel'
import ReviewQueuePanel from './ReviewQueuePanel'
import MyWritingPanel from './MyWritingPanel'
import ReviewsReceivedPanel from './ReviewsReceivedPanel'
import { useUnreadReceivedCount } from '../../hooks/useHumanReviews'
import { LogoMark } from '../branding/Logo'

/**
 * The human-review network's top-level surface, at /app/community.
 *
 * It deliberately sits outside the manuscript workspace rather than inside the
 * nav tree: review packages are derived, published snapshots, not editable
 * manuscript children, and putting them in the tree would imply otherwise.
 *
 * The full Phase 1 tab set is now live end to end: claim a handle (1A), publish and
 * manage requests (1B), browse the queue (1C), and — slice 1D — write and submit
 * reviews and read the feedback you receive. The Reviews Received tab carries an
 * unread badge, the whole of Phase 1's notification model.
 */
const TABS = [
	{ key: 'profile',  label: 'My Profile' },
	{ key: 'queue',    label: 'Review Queue' },
	{ key: 'requests', label: 'My Requests' },
	{ key: 'writing',  label: "Reviews I'm Writing" },
	{ key: 'received', label: 'Reviews Received' },
]

// The active tab is deep-linkable: /app/community?tab=requests lands directly on
// My Requests. An unknown or absent value falls back to My Profile.
function initialTab() {
	if (typeof window === 'undefined') return 'profile'
	const requested = new URLSearchParams(window.location.search).get('tab')
	return TABS.some(t => t.key === requested) ? requested : 'profile'
}

export default function CommunityPage() {
	const [tab, setTab] = useState(initialTab)
	const active = TABS.find(t => t.key === tab) ?? TABS[0]

	// The badge is a lightweight count endpoint, safe to poll on this surface; it
	// returns 0 for a handle-less user rather than erroring.
	const unread = useUnreadReceivedCount()
	const unreadCount = unread.data ?? 0

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

	const goToProfile = () => handleTab(null, 'profile')

	const tabLabel = (t) => {
		if (t.key === 'received' && unreadCount > 0) {
			return (
				<Badge color="primary" badgeContent={unreadCount} max={99} sx={{ '& .MuiBadge-badge': { right: -14, top: 2 } }}>
					{t.label}
				</Badge>
			)
		}
		return t.label
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
						<Tab key={t.key} value={t.key} label={tabLabel(t)} />
					))}
				</Tabs>
			</Box>

			<Box sx={{ flex: 1, minHeight: 0, overflow: 'auto', p: 3 }}>
				{active.key === 'profile' ? (
					<ReviewProfilePanel />
				) : active.key === 'requests' ? (
					<MyRequestsPanel />
				) : active.key === 'queue' ? (
					<ReviewQueuePanel onGoToProfile={goToProfile} />
				) : active.key === 'writing' ? (
					<MyWritingPanel />
				) : (
					<ReviewsReceivedPanel />
				)}
			</Box>
		</Box>
	)
}
