import { useState } from 'react'
import {
	Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Tab, Tabs, Typography,
} from '@mui/material'
import SettingsIcon from '@mui/icons-material/Settings'
import DocumentSettingsTab from './DocumentSettingsTab'
import OtherSettingsTab from './OtherSettingsTab'
import AiCredentialsPanel from '../ai/AiCredentialsPanel'
import AiFormInstructionsEditor from '../ai/AiFormInstructionsEditor'
import MemoryTemplateEditor from '../ai/MemoryTemplateEditor'

// Fixed height for the tab body. MUI centers Dialog paper based on its
// content height, so without a fixed height here the dialog grew/shrank and
// recentered every time the active tab changed (Document — especially with a
// project override section — is much taller than Other). Pinning the body to
// one height keeps the dialog's overall size, and therefore its on-screen
// position, constant across tab switches; a tab whose content is shorter than
// this just has empty space below it, and a taller one scrolls internally.
const TAB_CONTENT_HEIGHT = '55vh'

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
				{tab === 'other' && <OtherSettingsTab />}
			</Box>
		</>
	)
}

/**
 * SettingsDialog
 *
 * Global settings, organized into tabs. Opened from the AppBar gear icon.
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