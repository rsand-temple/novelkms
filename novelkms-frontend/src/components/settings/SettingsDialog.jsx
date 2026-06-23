import { useState } from 'react'
import {
	Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tab, Tabs,
} from '@mui/material'
import SettingsIcon from '@mui/icons-material/Settings'
import DocumentSettingsTab from './DocumentSettingsTab'
import OtherSettingsTab from './OtherSettingsTab'
import AiCredentialsPanel from '../ai/AiCredentialsPanel'

// Inner content is mounted fresh whenever the dialog opens (see the `{open && …}`
// guard below), so the active tab always starts at `initialTab` and each tab's
// own state starts clean — no effect syncing needed.
function SettingsContent({ initialTab, projectId }) {
	const [tab, setTab] = useState(initialTab ?? 'document')

	return (
		<>
			<Tabs
				value={tab}
				onChange={(_, v) => setTab(v)}
				sx={{ mb: 2, mt: -1 }}
			>
				<Tab value="document" label="Document" />
				<Tab value="ai" label="AI" />
				<Tab value="other" label="Other" />
			</Tabs>

			{tab === 'document' && <DocumentSettingsTab projectId={projectId} />}
			{tab === 'ai' && <AiCredentialsPanel />}
			{tab === 'other' && <OtherSettingsTab />}
		</>
	)
}

/**
 * SettingsDialog
 *
 * Global settings, organized into tabs. Opened from the AppBar gear (Document
 * tab) and from the AI menu (AI tab).
 *
 * Props:
 *   open        {boolean}
 *   initialTab  {'document'|'ai'|'other'}  Tab to show on open. Default 'document'.
 *   projectId   {string|null}              Passed to the Document tab for the
 *                                          per-project override panel.
 *   onClose     {() => void}
 */
export default function SettingsDialog({ open, initialTab = 'document', projectId, onClose }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<SettingsIcon fontSize="small" />
				Settings
			</DialogTitle>

			<DialogContent dividers>
				{open && <SettingsContent initialTab={initialTab} projectId={projectId} />}
			</DialogContent>

			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
