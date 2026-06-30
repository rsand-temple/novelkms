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
import { HelpButton } from '../../../help'

export default function AccountDialog({ open, onClose }) {
	const accountQuery = useAccount()
	const updateMutation = useUpdateAccount()

	const account = accountQuery.data

	const initialKey = `${account?.email ?? 'none'}:${open ? 'open' : 'closed'}`

	const [draftKey, setDraftKey] = useState(initialKey)
	const [firstName, setFirstName] = useState('')
	const [lastName, setLastName] = useState('')
	const [displayName, setDisplayName] = useState('')
	const [mobile, setMobile] = useState('')

	if (draftKey !== initialKey && account) {
		setDraftKey(initialKey)
		setFirstName(account.first_name ?? '')
		setLastName(account.last_name ?? '')
		setDisplayName(account.display_name ?? '')
		setMobile(account.mobile_number ?? '')
	}

	const canSave = useMemo(() => {
		if (!account) return false

		return firstName.trim() !== (account.first_name ?? '') ||
			lastName.trim() !== (account.last_name ?? '') ||
			displayName.trim() !== (account.display_name ?? '') ||
			mobile.trim() !== (account.mobile_number ?? '')
	}, [account, firstName, lastName, displayName, mobile])

	async function handleSave() {
		if (!account) return

		await updateMutation.mutateAsync({
			firstname: firstName.trim(),
			lastname: lastName.trim(),
			displayname: displayName.trim(),
			mobile: mobile.trim(),
		})

		onClose()
	}

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				Account
				<Box sx={{ flex: 1 }} />
				<HelpButton topic="account.account" />
			</DialogTitle>

			<DialogContent>
				<Box sx={{ display: 'grid', gap: 2, mt: 1 }}>
					{accountQuery.isError && (
						<Alert severity="error">
							Unable to load account information.
							{accountQuery.error?.response?.status
								? ` Server returned ${accountQuery.error.response.status}.`
								: ''}
						</Alert>
					)}

					{updateMutation.isError && (
						<Alert severity="error">
							Unable to save account information.
							{updateMutation.error?.response?.status
								? ` Server returned ${updateMutation.error.response.status}.`
								: ''}
						</Alert>
					)}

					<TextField
						label="Email"
						value={account?.email ?? ''}
						disabled
						helperText="Email cannot be changed yet."
						fullWidth
					/>

					<TextField
						label="First name"
						value={firstName}
						onChange={(event) => setFirstName(event.target.value)}
						fullWidth
					/>

					<TextField
						label="Last name"
						value={lastName}
						onChange={(event) => setLastName(event.target.value)}
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