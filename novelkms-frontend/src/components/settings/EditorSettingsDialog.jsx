import { useRef, useState } from 'react'
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
		case 'USER':    return 'your global default'
		case 'SYSTEM':  return 'the built-in default'
		default:        return 'the inherited value'
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

function AiTab({ scope, id, scopeWord }) {
	const q = useAiFormInstructions(scope, id)
	const save = useSaveAiFormInstructions(scope, id)
	const remove = useRemoveAiFormInstructions(scope, id)
	if (q.isLoading || !q.data) return <Loading />
	const data = q.data
	const busy = save.isPending || remove.isPending
	return (
		<AiBody
			key={`${id}:${data.scope}:${data.hasOwnOverride}`}
			data={data} isOwn={data.hasOwnOverride} scopeWord={scopeWord} busy={busy}
			inheritedFrom={inheritedFromLabel(data.scope)}
			upsert={(text, opts) => save.mutate(text, opts)}
			remove={() => remove.mutate()}
		/>
	)
}

function AiBody({ data, isOwn, scopeWord, busy, inheritedFrom, upsert, remove }) {
	const seed = data.instructions ?? ''
	const [text, setText] = useState(seed)
	const [dirty, setDirty] = useState(false)
	const show = isOwn ? text : seed
	const onToggle = (on) => (on ? upsert(seed) : remove())
	const onSave = () => upsert(text.trim(), { onSuccess: () => setDirty(false) })

	return (
		<OverrideShell label="review instructions" scopeWord={scopeWord} isOwn={isOwn}
			inheritedFrom={inheritedFrom} busy={busy} dirty={dirty && !!text.trim()} onToggle={onToggle} onSave={onSave}>
			<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
				Editorial guidance sent to the AI (its role and constraints). The response format the app
				relies on is fixed automatically and is not part of this text.
			</Typography>
			<TextField value={show} onChange={e => { setText(e.target.value); setDirty(true) }}
				fullWidth multiline minRows={8} size="small" disabled={!isOwn}
				placeholder="You are an experienced editor reviewing a section of a novel…" />
		</OverrideShell>
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
	const [activeTab, setActiveTab] = useState('document')

	// Reset when the dialog opens so the HelpButton matches the initial tab.
	const prevOpen = useRef(false)
	if (open && !prevOpen.current) {
		if (activeTab !== 'document') {
			setActiveTab('document')
		}
	}
	prevOpen.current = open

	const helpTopic = CTX_TAB_HELP_TOPICS[activeTab] ?? 'editor.overview'

	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				{scope === 'book' ? 'Book Settings' : 'Project Settings'}
				<Box sx={{ flex: 1 }} />
				<HelpButton topic={helpTopic} />
			</DialogTitle>
			<DialogContent dividers>
				{open && scope && (
					<Content scope={scope} projectId={projectId} bookId={bookId}
						scopeLabel={scopeLabel} onEditGlobal={onEditGlobal}
						onTabChange={setActiveTab} />
				)}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
