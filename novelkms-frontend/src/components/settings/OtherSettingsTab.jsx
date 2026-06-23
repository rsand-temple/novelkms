import {
	FormControlLabel, Stack, Switch, Typography,
} from '@mui/material'
import { usePreferences, useSetPreference } from '../../hooks/usePreferences'
import { hydrateSkipDeleteConfirm } from '../../utils/deleteConfirmPrefs'

/**
 * OtherSettingsTab
 *
 * Miscellaneous per-user UI preferences. Currently the delete-confirmation
 * toggle. "Ask before deleting" is the human-friendly inverse of the stored
 * `skipDeleteConfirm` flag.
 */
export default function OtherSettingsTab() {
	const { data: prefs = {}, isLoading } = usePreferences()
	const { mutate: setPref, isPending } = useSetPreference()

	const skip = prefs.skipDeleteConfirm === 'true'
	const askBeforeDeleting = !skip

	const handleToggle = (e) => {
		const nextSkip = !e.target.checked
		// Keep the synchronous mirror used by the nav/menu delete handlers in step
		// immediately, then persist.
		hydrateSkipDeleteConfirm(nextSkip)
		setPref({ key: 'skipDeleteConfirm', value: String(nextSkip) })
	}

	return (
		<Stack spacing={2}>
			<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
				Confirmations
			</Typography>

			<FormControlLabel
				control={
					<Switch
						checked={askBeforeDeleting}
						disabled={isLoading || isPending}
						onChange={handleToggle}
					/>
				}
				label="Ask before deleting"
			/>

			<Typography variant="caption" color="text.secondary">
				When off, items are deleted immediately without a confirmation dialog.
				Deleted items can still be recovered from the Trash.
			</Typography>
		</Stack>
	)
}
