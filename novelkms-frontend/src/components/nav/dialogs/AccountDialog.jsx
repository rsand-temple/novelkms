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

	const initialKey = `${account?.id ?? 'none'}:${open ? 'open' : 'closed'}`

	const [draftKey, setDraftKey] = useState(initialKey)
	const [name, setName] = useState('')
	const [phone, setPhone] = useState('')

	if (draftKey !== initialKey && account) {
		setDraftKey(initialKey)
		setName(account.name ?? '')
		setPhone(account.phone ?? '')
	}

	const canSave = useMemo(() => {
		if (!account) return false
		return name.trim() !== (account.name ?? '') || phone.trim() !== (account.phone ?? '')
	}, [account, name, phone])

	async function handleSave() {
		await updateMutation.mutateAsync({
			name: name.trim(),
			phone: phone.trim(),
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
						label="Name"
						value={name}
						onChange={(event) => setName(event.target.value)}
						fullWidth
					/>

					<TextField
						label="Phone"
						value={phone}
						onChange={(event) => setPhone(event.target.value)}
						fullWidth
					/>
				</Box>
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Cancel</Button>
				<Button
					variant="contained"
					onClick={handleSave}
					disabled={!canSave || updateMutation.isPending}
				>
					Save
				</Button>
			</DialogActions>
		</Dialog>
	)
}