import { useMemo, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	TextField,
} from '@mui/material'
import { useAccount, useUpdateAccount } from '../../../hooks/useAccount'

export default function AccountDialog({ open, onClose }) {
	const accountQuery = useAccount()
	const updateMutation = useUpdateAccount()

	const account = accountQuery.data

	const initialKey = `${account?.email ?? 'none'}:${open ? 'open' : 'closed'}`

	const [draftKey, setDraftKey] = useState(initialKey)
	const [displayName, setDisplayName] = useState('')
	const [mobile, setMobile] = useState('')

	if (draftKey !== initialKey && account) {
		setDraftKey(initialKey)
		setDisplayName(account.display_name ?? '')
		setMobile(account.mobile_number ?? '')
	}

	const canSave = useMemo(() => {
		if (!account) return false

		return displayName.trim() !== (account.display_name ?? '') ||
			mobile.trim() !== (account.mobile_number ?? '')
	}, [account, displayName, mobile])

	async function handleSave() {
		if (!account) return

		await updateMutation.mutateAsync({
			firstname: account.first_name ?? '',
			lastname: account.last_name ?? '',
			displayname: displayName.trim(),
			mobile: mobile.trim(),
		})

		onClose()
	}

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle>Account</DialogTitle>

			<DialogContent>
				<Box sx={{ display: 'grid', gap: 2, mt: 1 }}>
					{accountQuery.isError && (
						<Alert severity="error">Unable to load account information.</Alert>
					)}

					{updateMutation.isError && (
						<Alert severity="error">Unable to save account information.</Alert>
					)}

					<TextField
						label="Email"
						value={account?.email ?? ''}
						disabled
						helperText="Email cannot be changed yet."
						fullWidth
					/>

					<TextField
						label="Display name"
						value={displayName}
						onChange={(event) => setDisplayName(event.target.value)}
						fullWidth
					/>

					<TextField
						label="Mobile phone"
						value={mobile}
						onChange={(event) => setMobile(event.target.value)}
						fullWidth
					/>
				</Box>
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button
					variant="contained"
					onClick={handleSave}
					disabled={!canSave || accountQuery.isLoading || updateMutation.isPending}
				>
					Save
				</Button>
			</DialogActions>
		</Dialog>
	)
}