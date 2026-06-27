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
import LogoutIcon from '@mui/icons-material/Logout'
import PersonIcon from '@mui/icons-material/Person'
import AccountDialog from './dialogs/AccountDialog'
import { useLogout } from '../hooks/useAccount'

export default function UserMenu({ user, onLoggedOut }) {
	const [anchorEl, setAnchorEl] = useState(null)
	const [accountOpen, setAccountOpen] = useState(false)
	const logoutMutation = useLogout()

	const open = Boolean(anchorEl)

	const initials = user?.name
		? user.name.split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase()
		: null

	async function handleLogout() {
		setAnchorEl(null)
		await logoutMutation.mutateAsync()
		onLoggedOut?.()
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

				<MenuItem onClick={handleLogout} disabled={logoutMutation.isPending}>
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