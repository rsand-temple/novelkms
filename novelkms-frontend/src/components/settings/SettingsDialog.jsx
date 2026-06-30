import { useState } from 'react'
import {
	Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Tab, Tabs, Typography,
} from '@mui/material'
import SettingsIcon from '@mui/icons-material/Settings'
import DocumentSettingsTab from './DocumentSettingsTab'
import OtherSettingsTab from './OtherSettingsTab'
import AiCredentialsPanel from '../ai/AiCredentialsPanel'
import AiFormInstructionsEditor from '../ai/AiFormInstructionsEditor'
import MemoryTemplateEditor from '../ai/MemoryTemplateEditor'
import BillingPanel from '../subscription/BillingPanel'
import { HelpButton } from '../../help'

// Fixed height for the tab body. MUI centers Dialog paper based on its
// content height, so without a fixed height here the dialog grew/shrank and
// recentered every time the active tab changed (Document — especially with a
// project override section — is much taller than Other). Pinning the body to
// one height keeps the dialog's overall size, and therefore its on-screen
// position, constant across tab switches; a tab whose content is shorter than
// this just has empty space below it, and a taller one scrolls internally.
const TAB_CONTENT_HEIGHT = '55vh'

/**
 * Map each settings tab to the most relevant help topic. These all point to
 * existing topics today; as more specific settings-focused topics are authored
 * (e.g. settings.document, settings.other), update the mapping here and run
 * `npm run check-help` to confirm the ids resolve.
 */
const TAB_HELP_TOPICS = {
	document: 'editor.styles',
	ai: 'ai.credentials',
	billing: 'account.billing',
	other: 'index',
}

// Inner content is mounted fresh whenever the dialog opens (see the `{open && …}`
// guard below), so the active tab always starts at `initialTab` and each tab's
// own state starts clean — no effect syncing needed.
function SettingsContent({ initialTab, projectId, subscriptionRequired, onTabChange }) {
	const [tab, setTab] = useState(initialTab ?? 'document')

	const handleTabChange = (_, v) => {
		setTab(v)
		onTabChange?.(v)
	}

	return (
		<>
			<Tabs
				value={tab}
				onChange={handleTabChange}
				sx={{ mb: 2, mt: -1 }}
			>
				<Tab value="document" label="Document" />
				<Tab value="ai" label="AI" />
				<Tab value="billing" label="Billing" />
				<Tab value="other" label="Other" />
			</Tabs>

			{tab === 'billing' && subscriptionRequired && (
				<Alert severity="warning" sx={{ mb: 2 }}>
					A subscription is required to continue using NovelKMS.
				</Alert>
			)}

			<Box sx={{ height: TAB_CONTENT_HEIGHT, overflowY: 'auto', pr: 0.5 }}>
				{tab === 'document' && <DocumentSettingsTab projectId={projectId} />}
				{tab === 'ai' && (
					<>
						<AiCredentialsPanel />
						<Divider sx={{ my: 3 }} />
						<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
							Review Instructions
						</Typography>
						<AiFormInstructionsEditor scope="global" />
						<Divider sx={{ my: 3 }} />
						<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
							Memory Document Template
						</Typography>
						<MemoryTemplateEditor scope="global" />
					</>
				)}
				{tab === 'billing' && <BillingPanel />}
				{tab === 'other' && <OtherSettingsTab />}
			</Box>
		</>
	)
}

/**
 * SettingsDialog
 *
 * Global settings, organized into tabs. Opened from the account menu.
 *
 * Props:
 *   open        {boolean}
 *   initialTab  {'document'|'ai'|'billing'|'other'}  Tab to show on open. Default 'document'.
 *   projectId   {string|null}                        Passed to the Document tab for the
 *                                                    per-project override panel.
 *   onClose     {() => void}
 */
export default function SettingsDialog({ open, initialTab = 'document', projectId, subscriptionRequired = false, onClose }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			{open && (
				<SettingsDialogBody
					initialTab={initialTab}
					projectId={projectId}
					subscriptionRequired={subscriptionRequired}
					onClose={onClose}
				/>
			)}
		</Dialog>
	)
}

function SettingsDialogBody({ initialTab = 'document', projectId, subscriptionRequired = false, onClose }) {
	// Mounted only while the dialog is open, so this initializes cleanly for
	// each open without reading or writing refs during render.
	const [activeTab, setActiveTab] = useState(initialTab ?? 'document')
	const helpTopic = TAB_HELP_TOPICS[activeTab] ?? 'index'

	return (
		<>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<SettingsIcon fontSize="small" />
				Settings
				<Box sx={{ flex: 1 }} />
				<HelpButton topic={helpTopic} />
			</DialogTitle>

			<DialogContent dividers>
				<SettingsContent
					initialTab={initialTab}
					projectId={projectId}
					subscriptionRequired={subscriptionRequired}
					onTabChange={setActiveTab}
				/>
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</>
	)
}
