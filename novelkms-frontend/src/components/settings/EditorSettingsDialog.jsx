import { useState } from 'react'
import {
	Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent,
	DialogTitle, Divider, FormControl, FormControlLabel, InputLabel, MenuItem,
	Select, Stack, Switch, Tab, Tabs, TextField, ToggleButton, ToggleButtonGroup,
	Typography,
} from '@mui/material'
import HorizontalRuleIcon from '@mui/icons-material/HorizontalRule'

import { PROJECT_SETTINGS_DEFAULTS } from '../../hooks/useProjectSettings'
import {
	useBookEditorSettings, useProjectEditorSettings,
	useUpsertBookEditorSettings, useDeleteBookEditorSettings,
	useUpsertProjectEditorSettings, useDeleteProjectEditorSettings,
} from '../../hooks/useEditorSettings'
import { usePageLayout, useSavePageLayout, useRemovePageLayout } from '../../hooks/usePageLayout'
import {
	useAiFormInstructions, useSaveAiFormInstructions, useRemoveAiFormInstructions,
} from '../../hooks/useAiFormInstructions'
import {
	useMemoryTemplate, useSaveMemoryTemplate, useRemoveMemoryTemplate,
} from '../../hooks/useMemoryTemplate'
import {
	useAiPromptTemplate, useSaveAiPromptTemplate, useRemoveAiPromptTemplate,
} from '../../hooks/useAiPromptTemplate'
import AiFormInstructionsEditor from '../ai/AiFormInstructionsEditor'
import AiPromptTemplateEditor from '../ai/AiPromptTemplateEditor'
import MemoryTemplateEditor from '../ai/MemoryTemplateEditor'
import { HelpButton } from '../../help'

const TAB_CONTENT_HEIGHT = '60vh'

const FONT_FAMILIES = [
	{ label: 'Georgia', value: 'Georgia, serif' },
	{ label: 'Times New Roman', value: '"Times New Roman", Times, serif' },
	{ label: 'Garamond', value: 'Garamond, serif' },
	{ label: 'Palatino', value: '"Palatino Linotype", Palatino, serif' },
	{ label: 'Helvetica', value: 'Helvetica, Arial, sans-serif' },
	{ label: 'Arial', value: 'Arial, sans-serif' },
	{ label: 'Courier New', value: '"Courier New", Courier, monospace' },
]
const FONT_SIZES = [
	{ label: '12', value: '0.75rem' }, { label: '13', value: '0.8125rem' },
	{ label: '14', value: '0.875rem' }, { label: '15', value: '0.9375rem' },
	{ label: '16', value: '1rem' }, { label: '17', value: '1.0625rem' },
	{ label: '18', value: '1.125rem' }, { label: '20', value: '1.25rem' },
	{ label: '22', value: '1.375rem' }, { label: '24', value: '1.5rem' },
]
const LINE_HEIGHTS = ['1.4', '1.5', '1.6', '1.7', '1.8', '1.9', '2.0', '2.2']
const PAGE_SIZE_PRESETS = [
	{ label: 'US Letter (8.5″ × 11″)', value: 'LETTER', width: 8.5, height: 11.0 },
	{ label: 'A4 (8.27″ × 11.69″)', value: 'A4', width: 8.27, height: 11.69 },
	{ label: 'Trade Paperback (6″ × 9″)', value: 'TRADE_PB', width: 6.0, height: 9.0 },
	{ label: 'Mass Market (4.25″ × 6.87″)', value: 'MASS_MARKET', width: 4.25, height: 6.87 },
	{ label: 'Hardback (6″ × 9″)', value: 'HARDBACK', width: 6.0, height: 9.0 },
	{ label: 'Custom', value: 'CUSTOM', width: null, height: null },
]

function inheritedFromLabel(serverScope) {
	switch (serverScope) {
		case 'PROJECT': return 'the project'
		case 'USER': return 'your global default'
		case 'SYSTEM': return 'the built-in default'
		default: return 'the inherited value'
	}
}

function Loading() {
	return (
		<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 3 }}>
			<CircularProgress size={20} />
			<Typography variant="body2">Loading…</Typography>
		</Box>
	)
}

// Shared toggle + save chrome around a tab's fields. Off = inherit (fields shown
// read-only); on = copy-on-write override, fields editable.
function OverrideShell({ label, scopeWord, isOwn, inheritedFrom, busy, dirty, onToggle, onSave, children }) {
	return (
		<Box>
			<FormControlLabel
				control={<Switch checked={isOwn} disabled={busy} onChange={(e) => onToggle(e.target.checked)} />}
				label={`Use ${scopeWord}-specific ${label}`}
			/>
			{!isOwn && (
				<Alert severity="info" icon={false} sx={{ my: 1, py: 0.5 }}>
					Inheriting from {inheritedFrom}. Turn on to override for this {scopeWord}.
				</Alert>
			)}
			<Box sx={{ mt: 2, opacity: isOwn ? 1 : 0.55 }}>
				{children}
			</Box>
			{isOwn && (
				<Stack direction="row" justifyContent="flex-end" sx={{ mt: 2 }}>
					<Button variant="contained" disabled={busy || !dirty} onClick={onSave}>
						{busy ? 'Saving…' : 'Save'}
					</Button>
				</Stack>
			)}
		</Box>
	)
}

// ── Document tab ───────────────────────────────────────────────────────────────

// Calls all editor-settings hooks unconditionally (rules of hooks) and selects
// the set matching the scope.
function useDocScoped(scope, id) {
	const isBook = scope === 'book'
	const bookQ = useBookEditorSettings(id, isBook)
	const projQ = useProjectEditorSettings(id, !isBook)
	const upBook = useUpsertBookEditorSettings()
	const upProj = useUpsertProjectEditorSettings()
	const delBook = useDeleteBookEditorSettings()
	const delProj = useDeleteProjectEditorSettings()
	if (isBook) {
		return {
			q: bookQ,
			upsert: (definition, opts) => upBook.mutate({ bookId: id, definition }, opts),
			remove: (opts) => delBook.mutate({ bookId: id }, opts),
			busy: upBook.isPending || delBook.isPending,
		}
	}
	return {
		q: projQ,
		upsert: (definition, opts) => upProj.mutate({ projectId: id, definition }, opts),
		remove: (opts) => delProj.mutate({ projectId: id }, opts),
		busy: upProj.isPending || delProj.isPending,
	}
}

function DocumentTab({ scope, id, ownScope, scopeWord }) {
	const { q, upsert, remove, busy } = useDocScoped(scope, id)
	if (q.isLoading || !q.data) return <Loading />
	const row = q.data
	const isOwn = row.scope === ownScope
	return (
		<DocumentBody
			key={`${id}:${row.scope}`}
			row={row} isOwn={isOwn} scopeWord={scopeWord} busy={busy}
			inheritedFrom={inheritedFromLabel(row.scope)}
			upsert={upsert} remove={remove}
		/>
	)
}

function DocumentBody({ row, isOwn, scopeWord, busy, inheritedFrom, upsert, remove }) {
	const seed = { ...PROJECT_SETTINGS_DEFAULTS, ...(row.definition ?? {}) }
	const [def, setDef] = useState(seed)
	const [dirty, setDirty] = useState(false)
	const show = isOwn ? def : seed
	const set = (k, v) => { setDef(d => ({ ...d, [k]: v })); setDirty(true) }
	const onToggle = (on) => (on ? upsert(seed) : remove())
	const onSave = () => upsert(def, { onSuccess: () => setDirty(false) })

	return (
		<OverrideShell label="document settings" scopeWord={scopeWord} isOwn={isOwn}
			inheritedFrom={inheritedFrom} busy={busy} dirty={dirty} onToggle={onToggle} onSave={onSave}>
			<Stack spacing={2}>
				<FormControl size="small" fullWidth disabled={!isOwn}>
					<InputLabel>Font Family</InputLabel>
					<Select value={show.fontFamily} label="Font Family" onChange={e => set('fontFamily', e.target.value)}>
						{FONT_FAMILIES.map(f => <MenuItem key={f.value} value={f.value} sx={{ fontFamily: f.value }}>{f.label}</MenuItem>)}
					</Select>
				</FormControl>
				<FormControl size="small" fullWidth disabled={!isOwn}>
					<InputLabel>Font Size</InputLabel>
					<Select value={show.fontSize} label="Font Size" onChange={e => set('fontSize', e.target.value)}>
						{FONT_SIZES.map(s => <MenuItem key={s.value} value={s.value}>{s.label} pt</MenuItem>)}
					</Select>
				</FormControl>
				<FormControl size="small" fullWidth disabled={!isOwn}>
					<InputLabel>Line Height</InputLabel>
					<Select value={show.lineHeight} label="Line Height" onChange={e => set('lineHeight', e.target.value)}>
						{LINE_HEIGHTS.map(l => <MenuItem key={l} value={l}>{l}×</MenuItem>)}
					</Select>
				</FormControl>
				<Divider />
				<TextField label="Default First-Line Indent" size="small" fullWidth disabled={!isOwn}
					value={show.firstLineIndent} onChange={e => set('firstLineIndent', e.target.value)}
					helperText='CSS value, e.g. "1.5em" or "0" to disable' />
				<TextField label="Paragraph Spacing After" size="small" fullWidth disabled={!isOwn}
					value={show.spacingAfter} onChange={e => set('spacingAfter', e.target.value)}
					helperText='CSS value, e.g. "0.9em" or "0"' />
				<Divider />
				<Typography variant="caption" color="text.secondary"
					sx={{ fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>
					Scene Break
				</Typography>
				<Box>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>Style</Typography>
					<ToggleButtonGroup value={show.sceneBreakStyle} exclusive size="small" disabled={!isOwn}
						onChange={(_, val) => { if (val) set('sceneBreakStyle', val) }} sx={{ width: '100%' }}>
						<ToggleButton value="* * *" sx={{ flex: 1, fontSize: '0.75rem', letterSpacing: '0.15em' }}>* * *</ToggleButton>
						<ToggleButton value="#" sx={{ flex: 1, fontSize: '0.85rem' }}>#</ToggleButton>
						<ToggleButton value="rule" sx={{ flex: 1 }}><HorizontalRuleIcon fontSize="small" /></ToggleButton>
					</ToggleButtonGroup>
				</Box>
				<TextField label="Spacing Above Break" size="small" fullWidth disabled={!isOwn}
					value={show.sceneBreakSpacingAbove} onChange={e => set('sceneBreakSpacingAbove', e.target.value)}
					helperText='CSS value, e.g. "2em" or "24px"' />
				<TextField label="Spacing Below Break" size="small" fullWidth disabled={!isOwn}
					value={show.sceneBreakSpacingBelow} onChange={e => set('sceneBreakSpacingBelow', e.target.value)}
					helperText='CSS value, e.g. "2em" or "24px"' />
			</Stack>
		</OverrideShell>
	)
}

// ── Page Layout tab ────────────────────────────────────────────────────────────

const PL_FIELDS = ['pageLayoutEnabled', 'pageSizePreset', 'pageWidthIn', 'pageHeightIn',
	'pageMarginTopIn', 'pageMarginBottomIn', 'pageMarginInnerIn', 'pageMarginOuterIn']

function pickLayout(data) {
	const out = {}
	for (const k of PL_FIELDS) out[k] = data[k]
	return out
}

function PageLayoutTab({ scope, id, ownScope, scopeWord }) {
	const q = usePageLayout(scope, id)
	const save = useSavePageLayout(scope, id)
	const remove = useRemovePageLayout(scope, id)
	if (q.isLoading || !q.data) return <Loading />
	const data = q.data
	const isOwn = data.scope === ownScope
	const busy = save.isPending || remove.isPending
	return (
		<PageLayoutBody
			key={`${id}:${data.scope}`}
			data={data} isOwn={isOwn} scopeWord={scopeWord} busy={busy}
			inheritedFrom={inheritedFromLabel(data.scope)}
			upsert={(body, opts) => save.mutate(body, opts)}
			remove={() => remove.mutate()}
		/>
	)
}

function PageLayoutBody({ data, isOwn, scopeWord, busy, inheritedFrom, upsert, remove }) {
	const seed = pickLayout(data)
	const [v, setV] = useState(seed)
	const [dirty, setDirty] = useState(false)
	const show = isOwn ? v : seed
	const set = (patch) => { setV(prev => ({ ...prev, ...patch })); setDirty(true) }
	const onToggle = (on) => (on ? upsert(seed) : remove())
	const onSave = () => upsert(v, { onSuccess: () => setDirty(false) })

	const onPreset = (val) => {
		const preset = PAGE_SIZE_PRESETS.find(p => p.value === val)
		set(preset?.width != null
			? { pageSizePreset: val, pageWidthIn: preset.width, pageHeightIn: preset.height }
			: { pageSizePreset: val })
	}

	return (
		<OverrideShell label="page layout" scopeWord={scopeWord} isOwn={isOwn}
			inheritedFrom={inheritedFrom} busy={busy} dirty={dirty} onToggle={onToggle} onSave={onSave}>
			<Stack spacing={2}>
				<Alert severity="info" icon={false} sx={{ py: 0.5 }}>
					Page size and margins affect export and the page-layout preview only — not the editor.
				</Alert>
				<FormControlLabel
					control={<Switch size="small" checked={!!show.pageLayoutEnabled} disabled={!isOwn}
						onChange={e => set({ pageLayoutEnabled: e.target.checked })} />}
					label={<Typography variant="body2">{show.pageLayoutEnabled ? 'Applied in export/preview' : 'Not applied (plain export)'}</Typography>}
				/>
				<FormControl size="small" fullWidth disabled={!isOwn}>
					<InputLabel>Page Size</InputLabel>
					<Select value={show.pageSizePreset ?? 'LETTER'} label="Page Size" onChange={e => onPreset(e.target.value)}>
						{PAGE_SIZE_PRESETS.map(p => <MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>)}
					</Select>
				</FormControl>
				{show.pageSizePreset === 'CUSTOM' && (
					<Stack direction="row" spacing={1}>
						<TextField label="Width (in)" size="small" type="number" fullWidth disabled={!isOwn}
							value={show.pageWidthIn ?? ''} onChange={e => set({ pageWidthIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
							inputProps={{ step: 0.125, min: 3, max: 12 }} />
						<TextField label="Height (in)" size="small" type="number" fullWidth disabled={!isOwn}
							value={show.pageHeightIn ?? ''} onChange={e => set({ pageHeightIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
							inputProps={{ step: 0.125, min: 4, max: 18 }} />
					</Stack>
				)}
				<Typography variant="caption" color="text.secondary">Margins (inches)</Typography>
				<Stack direction="row" spacing={1}>
					<TextField label="Top" size="small" type="number" fullWidth disabled={!isOwn}
						value={show.pageMarginTopIn ?? ''} onChange={e => set({ pageMarginTopIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
						inputProps={{ step: 0.125, min: 0.25, max: 3 }} />
					<TextField label="Bottom" size="small" type="number" fullWidth disabled={!isOwn}
						value={show.pageMarginBottomIn ?? ''} onChange={e => set({ pageMarginBottomIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
						inputProps={{ step: 0.125, min: 0.25, max: 3 }} />
				</Stack>
				<Stack direction="row" spacing={1}>
					<TextField label="Inner" size="small" type="number" fullWidth disabled={!isOwn}
						value={show.pageMarginInnerIn ?? ''} onChange={e => set({ pageMarginInnerIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
						inputProps={{ step: 0.125, min: 0.25, max: 3 }} helperText="Binding side" />
					<TextField label="Outer" size="small" type="number" fullWidth disabled={!isOwn}
						value={show.pageMarginOuterIn ?? ''} onChange={e => set({ pageMarginOuterIn: e.target.value === '' ? null : parseFloat(e.target.value) })}
						inputProps={{ step: 0.125, min: 0.25, max: 3 }} />
				</Stack>
			</Stack>
		</OverrideShell>
	)
}

// ── AI tab ─────────────────────────────────────────────────────────────────────

/**
 * AiTab — single "Use {scope}-specific AI prompts" switch wrapping all four AI
 * prompt subtabs, mirroring the Document and Page Layout tabs' OverrideShell
 * pattern. Unlike those tabs, the AI prompts aren't one row of settings — they
 * are five independently-resolved artifacts (review instructions, memory
 * template, chapter-summary prompt, book-summary prompt, editorial prompt),
 * each on its own book → project → user → system cascade. So this tab fetches
 * all five and drives the switch off their combined `hasOwnOverride` state
 * rather than delegating to a single OverrideShell/onSave pair:
 *
 *   - Off (none of the five have an override at this scope): the tab shows an
 *     "inheriting" message only. All four AI subtabs come from whatever this
 *     scope currently resolves to (project/user/system) — nothing book- or
 *     project-specific is shown or editable here.
 *   - On: creates a copy-on-write override for every one of the five,
 *     seeded from their current resolved (inherited) content, then shows the
 *     normal editable AiPromptSubtabs — which still has its own per-artifact
 *     Save/Remove, so individual prompts can be fine-tuned or reverted
 *     without leaving override mode.
 *
 * Turning the switch back off removes the override for every one of the five
 * that currently has one, reverting this tab fully back to inherited.
 */
function AiTab({ scope, id, scopeWord }) {
	const reviewsQ = useAiFormInstructions(scope, id)
	const memoryQ = useMemoryTemplate(scope, id)
	const chapterSummaryQ = useAiPromptTemplate('chapterSummary', scope, id)
	const bookSummaryQ = useAiPromptTemplate('bookSummary', scope, id)
	const editorialQ = useAiPromptTemplate('editorial', scope, id)

	const saveReviews = useSaveAiFormInstructions(scope, id)
	const removeReviews = useRemoveAiFormInstructions(scope, id)
	const saveMemory = useSaveMemoryTemplate(scope, id)
	const removeMemory = useRemoveMemoryTemplate(scope, id)
	const saveChapterSummary = useSaveAiPromptTemplate('chapterSummary', scope, id)
	const removeChapterSummary = useRemoveAiPromptTemplate('chapterSummary', scope, id)
	const saveBookSummary = useSaveAiPromptTemplate('bookSummary', scope, id)
	const removeBookSummary = useRemoveAiPromptTemplate('bookSummary', scope, id)
	const saveEditorial = useSaveAiPromptTemplate('editorial', scope, id)
	const removeEditorial = useRemoveAiPromptTemplate('editorial', scope, id)

	const queries = [reviewsQ, memoryQ, chapterSummaryQ, bookSummaryQ, editorialQ]
	if (queries.some(q => q.isLoading || !q.data)) return <Loading />

	const isOwn = queries.some(q => q.data.hasOwnOverride)
	const busy = [
		saveReviews, removeReviews, saveMemory, removeMemory,
		saveChapterSummary, removeChapterSummary, saveBookSummary, removeBookSummary,
		saveEditorial, removeEditorial,
	].some(m => m.isPending)

	// The five artifacts share the same cascade, so their resolved scopes
	// normally agree; the review instructions' resolved scope is a
	// representative choice for the "inheriting from" message.
	const inheritedFrom = inheritedFromLabel(reviewsQ.data.scope)

	const handleToggle = (on) => {
		if (on) {
			if (!reviewsQ.data.hasOwnOverride) saveReviews.mutate(reviewsQ.data.instructions ?? '')
			if (!memoryQ.data.hasOwnOverride) saveMemory.mutate(memoryQ.data.content ?? '')
			if (!chapterSummaryQ.data.hasOwnOverride) saveChapterSummary.mutate(chapterSummaryQ.data.content ?? '')
			if (!bookSummaryQ.data.hasOwnOverride) saveBookSummary.mutate(bookSummaryQ.data.content ?? '')
			if (!editorialQ.data.hasOwnOverride) saveEditorial.mutate(editorialQ.data.content ?? '')
		} else {
			if (reviewsQ.data.hasOwnOverride) removeReviews.mutate()
			if (memoryQ.data.hasOwnOverride) removeMemory.mutate()
			if (chapterSummaryQ.data.hasOwnOverride) removeChapterSummary.mutate()
			if (bookSummaryQ.data.hasOwnOverride) removeBookSummary.mutate()
			if (editorialQ.data.hasOwnOverride) removeEditorial.mutate()
		}
	}

	return (
		<Box>
			<FormControlLabel
				control={<Switch checked={isOwn} disabled={busy} onChange={(e) => handleToggle(e.target.checked)} />}
				label={`Use ${scopeWord}-specific AI prompts`}
			/>
			{!isOwn && (
				<Alert severity="info" icon={false} sx={{ my: 1, py: 0.5 }}>
					Inheriting AI prompts (reviews, memory, summary, and editorial) from {inheritedFrom}.
					Turn on to customize prompts for this {scopeWord}.
				</Alert>
			)}
			{isOwn && (
				<Box sx={{ mt: 2 }}>
					<AiPromptSubtabs scope={scope} id={id} />
				</Box>
			)}
		</Box>
	)
}

/**
 * The four AI prompt subtabs rendered once the AiTab switch above is on,
 * scoped to this book or project. Mirrors SettingsDialog's AiPromptSubtabs
 * (global scope) — same four subtabs, same underlying shared editor
 * components, just pointed at `scope`/`id` instead of `'global'`. Each subtab
 * is mounted fresh when selected so its form state resets cleanly.
 */
function AiPromptSubtabs({ scope, id }) {
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
				<AiFormInstructionsEditor scope={scope} id={id} />
			)}

			{aiTab === 'memory' && (
				<MemoryTemplateEditor scope={scope} id={id} />
			)}

			{aiTab === 'summary' && (
				<Box>
					<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
						Chapter Summary Prompt
					</Typography>
					<AiPromptTemplateEditor
						key={`chapterSummary:${scope}:${id}`}
						templateType="chapterSummary"
						scope={scope}
						id={id}
						description="The instruction the AI follows when it writes a one-paragraph summary of a chapter. These summaries are gathered in book order as the sole input when generating the book summary."
						placeholder="You are summarizing one chapter of a novel. Write a single, clear, human-readable paragraph…"
					/>

					<Divider sx={{ my: 3 }} />

					<Typography variant="subtitle2" sx={{ mb: 1.5 }}>
						Book Summary Prompt
					</Typography>
					<AiPromptTemplateEditor
						key={`bookSummary:${scope}:${id}`}
						templateType="bookSummary"
						scope={scope}
						id={id}
						description="The instruction the AI follows when it synthesizes the chapter summaries into a whole-book synopsis. The book summary is built entirely from chapter summaries — never the manuscript prose directly."
						placeholder="You are writing a synopsis of an entire novel from the per-chapter summaries…"
					/>
				</Box>
			)}

			{aiTab === 'editorial' && (
				<AiPromptTemplateEditor
					key={`editorial:${scope}:${id}`}
					templateType="editorial"
					scope={scope}
					id={id}
					description="The developmental-editor persona the AI adopts when it gives its overall impressionistic read of a chapter — tone, genre drift, character arcs, and storyline evolution. An editorial is not a line-level review and is never consumed by any other AI function."
					placeholder="You are an experienced developmental editor giving the author your overall editorial impression…"
				/>
			)}
		</Box>
	)
}

// ── Dialog shell ───────────────────────────────────────────────────────────────

function Content({ scope, projectId, bookId, scopeLabel, onEditGlobal, onTabChange }) {
	const [tab, setTab] = useState('document')
	const id = scope === 'book' ? bookId : projectId
	const scopeWord = scope === 'book' ? 'book' : 'project'
	const ownScope = scope === 'book' ? 'BOOK' : 'PROJECT'

	const handleTabChange = (_, v) => {
		setTab(v)
		onTabChange?.(v)
	}

	return (
		<>
			<Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
				<Typography variant="body2" color="text.secondary">{scopeLabel}</Typography>
				<Button size="small" onClick={onEditGlobal}>Global defaults…</Button>
			</Box>
			<Tabs value={tab} onChange={handleTabChange} sx={{ mb: 2 }}>
				<Tab value="document" label="Document" />
				<Tab value="layout" label="Page Layout" />
				<Tab value="ai" label="AI" />
			</Tabs>
			<Box sx={{ height: TAB_CONTENT_HEIGHT, overflowY: 'auto', pr: 0.5 }}>
				{tab === 'document' && <DocumentTab scope={scope} id={id} ownScope={ownScope} scopeWord={scopeWord} />}
				{tab === 'layout' && <PageLayoutTab scope={scope} id={id} ownScope={ownScope} scopeWord={scopeWord} />}
				{tab === 'ai' && <AiTab scope={scope} id={id} scopeWord={scopeWord} />}
			</Box>
		</>
	)
}

/**
 * Map each context-settings tab to the most relevant help topic.
 */
const CTX_TAB_HELP_TOPICS = {
	document: 'editor.styles',
	layout: 'editor.page-layout',
	ai: 'ai.form-instructions',
}

/**
 * EditorSettingsDialog — context settings for the selected scope.
 *
 * Props:
 *   open        {boolean}
 *   scope       {'project'|'book'}
 *   projectId   {string|null}
 *   bookId      {string|null}
 *   scopeLabel  {string}       e.g. "Book settings" / "Project settings"
 *   onEditGlobal {() => void}  open the global defaults dialog
 *   onClose     {() => void}
 */
export default function EditorSettingsDialog({ open, scope, projectId, bookId, scopeLabel, onEditGlobal, onClose }) {
	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			{open && scope && (
				<EditorSettingsDialogBody
					scope={scope} projectId={projectId} bookId={bookId}
					scopeLabel={scopeLabel} onEditGlobal={onEditGlobal} onClose={onClose}
				/>
			)}
		</Dialog>
	)
}

// Mounted fresh only while the dialog is open (see the `{open && …}` guard
// above), so `activeTab` always starts at 'document' on each open without
// needing a ref to detect the open transition — accessing `ref.current`
// during render is unsafe (see https://react.dev/reference/react/useRef).
function EditorSettingsDialogBody({ scope, projectId, bookId, scopeLabel, onEditGlobal, onClose }) {
	const [activeTab, setActiveTab] = useState('document')
	const helpTopic = CTX_TAB_HELP_TOPICS[activeTab] ?? 'editor.overview'

	return (
		<>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				{scope === 'book' ? 'Book Settings' : 'Project Settings'}
				<Box sx={{ flex: 1 }} />
				<HelpButton topic={helpTopic} />
			</DialogTitle>
			<DialogContent dividers>
				<Content scope={scope} projectId={projectId} bookId={bookId}
					scopeLabel={scopeLabel} onEditGlobal={onEditGlobal}
					onTabChange={setActiveTab} />
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</>
	)
}