import { useState } from 'react'
import {
	Avatar,
	Divider,
	IconButton,
	ListItemIcon,
	Menu,
	MenuItem,
	Tooltip,
} from '@mui/material'
import AccountCircleIcon from '@mui/icons-material/AccountCircle'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import LogoutIcon from '@mui/icons-material/Logout'
import PersonIcon from '@mui/icons-material/Person'
import SettingsIcon from '@mui/icons-material/Settings'
import AccountDialog from './dialogs/AccountDialog'
import { useAuth } from '../../auth/AuthContext'

export default function UserMenu({ onOpenSettings }) {
	const [anchorEl, setAnchorEl] = useState(null)
	const [accountOpen, setAccountOpen] = useState(false)
	const [loggingOut, setLoggingOut] = useState(false)
	const auth = useAuth()

	const open = Boolean(anchorEl)

	const user = auth.user
	const roles = user?.roles ?? []
	const isAdmin = roles.includes('ADMIN')

	const displayName = user?.displayName ?? user?.display_name ?? user?.name ?? ''
	const initials = displayName
		? displayName.split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase()
		: null

	async function handleLogout() {
		setAnchorEl(null)
		setLoggingOut(true)

		try {
			await auth.logout()
		} finally {
			setLoggingOut(false)
		}
	}

	return (
		<>
			<Tooltip title="Account">
				<IconButton
					color="inherit"
					onClick={(event) => setAnchorEl(event.currentTarget)}
					size="large"
				>
					{initials ? (
						<Avatar sx={{ width: 32, height: 32 }}>{initials}</Avatar>
					) : (
						<AccountCircleIcon />
					)}
				</IconButton>
			</Tooltip>

			<Menu
				anchorEl={anchorEl}
				open={open}
				onClose={() => setAnchorEl(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
				transformOrigin={{ vertical: 'top', horizontal: 'right' }}
			>
				{isAdmin && (
					<MenuItem
						onClick={() => {
							setAnchorEl(null)
							window.location.href = '/admin'
						}}
					>
						<ListItemIcon>
							<AdminPanelSettingsIcon fontSize="small" />
						</ListItemIcon>
						Admin console
					</MenuItem>
				)}

				<MenuItem
					onClick={() => {
						setAnchorEl(null)
						setAccountOpen(true)
					}}
				>
					<ListItemIcon>
						<PersonIcon fontSize="small" />
					</ListItemIcon>
					Account
				</MenuItem>

				<Divider />

				<MenuItem
					onClick={() => {
						setAnchorEl(null)
						onOpenSettings?.()
					}}
				>
					<ListItemIcon>
						<SettingsIcon fontSize="small" />
					</ListItemIcon>
					Global settings
				</MenuItem>

				<Divider />

				<MenuItem onClick={handleLogout} disabled={loggingOut}>
					<ListItemIcon>
						<LogoutIcon fontSize="small" />
					</ListItemIcon>
					Log out
				</MenuItem>
			</Menu>

			<AccountDialog
				open={accountOpen}
				onClose={() => setAccountOpen(false)}
			/>
		</>
	)
}