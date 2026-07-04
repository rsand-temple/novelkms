import { useState } from 'react'
import {
	Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Tab, Tabs, Typography,
} from '@mui/material'
import SettingsIcon from '@mui/icons-material/Settings'
import DocumentSettingsTab from './DocumentSettingsTab'
import OtherSettingsTab from './OtherSettingsTab'
import AiCredentialsPanel from '../ai/AiCredentialsPanel'
import AiFormInstructionsEditor from '../ai/AiFormInstructionsEditor'
import AiPromptTemplateEditor from '../ai/AiPromptTemplateEditor'
import MemoryTemplateEditor from '../ai/MemoryTemplateEditor'
import BillingPanel from '../subscription/BillingPanel'
import { HelpButton } from '../../help'

// Fixed height for the tab body. MUI centers Dialog paper based on its
// content height, so without a fixed height here the dialog grew/shrank and
// recentered every time the active tab changed. Pinning the body to one height
// keeps the dialog's overall size, and therefore its on-screen position,
// constant across tab switches; a tab whose content is shorter than this just
// has empty space below it, and a taller one scrolls internally.
const TAB_CONTENT_HEIGHT = '55vh'

/**
 * Map each top-level settings tab to the most relevant help topic.
 */
const TAB_HELP_TOPICS = {
	document: 'editor.styles',
	aiKeys: 'ai.credentials',
	aiPrompts: 'ai.review.prompts',
	billing: 'account.billing',
	other: 'index',
}

// ── AI prompt subtab content ───────────────────────────────────────────────────

/**
 * The four AI prompt subtabs rendered inside the AI Prompts outer tab. Each
 * subtab is mounted fresh when selected (via key) so its form state resets
 * cleanly without any useEffect syncing — the same mount-on-select pattern used
 * by the outer SettingsContent for the Document tab.
 */
function AiPromptSubtabs() {
	const [aiTab, setAiTab] = useState('reviews')

	return (
		<Box>
			<Tabs
				value={aiTab}
				onChange={(_, v) => setAiTab(v)}
				sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
				variant="scrollable"
				scrollButtons="auto"
			>
				<Tab value="reviews" label="Reviews" />
				<Tab value="memory" label="Memory" />
				<Tab value="summary" label="Summary" />
				<Tab value="editorial" label="Editorial" />
			</Tabs>

			{aiTab === 'reviews' && (
				<AiFormInstructionsEditor scope="global" />
			)}

			{aiTab === 'memory' && (
				<MemoryTemplateEditor scope="global" />
			)}

			{aiTab === 'summary' && (
				<Box>
					<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
						Chapter Summary Prompt
					</Typography>
					<AiPromptTemplateEditor
						key="chapterSummary:global"
						templateType="chapterSummary"
						scope="global"
						description="The instruction the AI follows when it writes a one-paragraph summary of a chapter. These summaries are gathered in book order as the sole input when generating the book summary."
						placeholder="You are summarizing one chapter of a novel. Write a single, clear, human-readable paragraph…"
					/>

					<Divider sx={{ my: 3 }} />

					<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
						Book Summary Prompt
					</Typography>
					<AiPromptTemplateEditor
						key="bookSummary:global"
						templateType="bookSummary"
						scope="global"
						description="The instruction the AI follows when it synthesizes the chapter summaries into a whole-book synopsis. The book summary is built entirely from chapter summaries — never the manuscript prose directly."
						placeholder="You are writing a synopsis of an entire novel from the per-chapter summaries…"
					/>
				</Box>
			)}

			{aiTab === 'editorial' && (
				<AiPromptTemplateEditor
					key="editorial:global"
					templateType="editorial"
					scope="global"
					description="The developmental-editor persona the AI adopts when it gives its overall impressionistic read of a chapter — tone, genre drift, character arcs, and storyline evolution. An editorial is not a line-level review and is never consumed by any other AI function."
					placeholder="You are an experienced developmental editor giving the author your overall editorial impression…"
				/>
			)}
		</Box>
	)
}

// ── Main Settings content ─────────────────────────────────────────────────────

// Inner content is mounted fresh whenever the dialog opens (see the `{open && …}`
// guard below), so the active tab always starts at `initialTab` and each tab's
// own state starts clean — no effect syncing needed.
function SettingsContent({ initialTab, projectId, subscriptionRequired, onTabChange }) {
	const normalizedInitialTab = initialTab === 'ai' ? 'aiKeys' : (initialTab ?? 'document')
	const [tab, setTab] = useState(normalizedInitialTab)

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
				variant="scrollable"
				scrollButtons="auto"
			>
				<Tab value="document" label="Document" />
				<Tab value="aiKeys" label="AI Keys" />
				<Tab value="aiPrompts" label="AI Prompts" />
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
				{tab === 'aiKeys' && <AiCredentialsPanel />}
				{tab === 'aiPrompts' && <AiPromptSubtabs />}
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
 * AI settings are split into two top-level tabs:
 *   - AI Keys: BYOK credential management.
 *   - AI Prompts: review, memory, summary, and editorial instructions.
 *
 * Props:
 *   open        {boolean}
 *   initialTab  {'document'|'ai'|'aiKeys'|'aiPrompts'|'billing'|'other'}  Tab to show on open.
 *   projectId   {string|null}                                             Passed to the Document tab for the
 *                                                                         per-project override panel.
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
	const normalizedInitialTab = initialTab === 'ai' ? 'aiKeys' : (initialTab ?? 'document')
	const [activeTab, setActiveTab] = useState(normalizedInitialTab)
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
					initialTab={normalizedInitialTab}
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