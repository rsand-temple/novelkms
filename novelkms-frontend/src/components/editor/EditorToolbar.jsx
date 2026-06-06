import { useState } from 'react'
import { useEditorState } from '@tiptap/react'
import {
	Box, Paper, Toolbar, IconButton, Tooltip, Divider,
	Select, MenuItem, CircularProgress, Typography,
} from '@mui/material'
import FormatBoldIcon           from '@mui/icons-material/FormatBold'
import FormatItalicIcon         from '@mui/icons-material/FormatItalic'
import FormatUnderlinedIcon     from '@mui/icons-material/FormatUnderlined'
import FormatListBulletedIcon   from '@mui/icons-material/FormatListBulleted'
import FormatListNumberedIcon   from '@mui/icons-material/FormatListNumbered'
import FormatIndentIncreaseIcon from '@mui/icons-material/FormatIndentIncrease'
import FormatIndentDecreaseIcon from '@mui/icons-material/FormatIndentDecrease'
import FormatAlignLeftIcon      from '@mui/icons-material/FormatAlignLeft'
import FormatAlignCenterIcon    from '@mui/icons-material/FormatAlignCenter'
import FormatAlignRightIcon     from '@mui/icons-material/FormatAlignRight'
import HorizontalRuleIcon       from '@mui/icons-material/HorizontalRule'
import SettingsIcon             from '@mui/icons-material/Settings'
import FormatQuoteIcon          from '@mui/icons-material/FormatQuote'
import DocSettingsPopover       from './DocSettingsPopover'

// ── font options ───────────────────────────────────────────────────────────────

const FONT_FAMILIES = [
	{ label: 'Georgia',         value: 'Georgia, serif' },
	{ label: 'Times New Roman', value: '"Times New Roman", Times, serif' },
	{ label: 'Garamond',        value: 'Garamond, serif' },
	{ label: 'Palatino',        value: '"Palatino Linotype", Palatino, serif' },
	{ label: 'Helvetica',       value: 'Helvetica, Arial, sans-serif' },
	{ label: 'Arial',           value: 'Arial, sans-serif' },
	{ label: 'Courier New',     value: '"Courier New", Courier, monospace' },
]

const FONT_SIZES = [
	{ label: '12', value: '0.75rem' },
	{ label: '13', value: '0.8125rem' },
	{ label: '14', value: '0.875rem' },
	{ label: '15', value: '0.9375rem' },
	{ label: '16', value: '1rem' },
	{ label: '17', value: '1.0625rem' },
	{ label: '18', value: '1.125rem' },
	{ label: '20', value: '1.25rem' },
	{ label: '22', value: '1.375rem' },
	{ label: '24', value: '1.5rem' },
]

// ── helpers ────────────────────────────────────────────────────────────────────

function parseEm(val) {
	if (!val) return 0
	const m = String(val).match(/^([\d.]+)em$/)
	return m ? parseFloat(m[1]) : 0
}

// ── sub-components ─────────────────────────────────────────────────────────────

/** Compact Select with no underline, suitable for embedding in a toolbar. */
function ToolbarSelect({ value, onChange, children, sx }) {
	return (
		<Select
			value={value}
			onChange={e => onChange(e.target.value)}
			variant="standard"
			disableUnderline
			size="small"
			sx={{ fontSize: '0.8rem', ...sx }}
			// Prevent the editor from losing focus when the dropdown opens
			onMouseDown={e => e.preventDefault()}
		>
			{children}
		</Select>
	)
}

/** Icon button that highlights when active, with optional tooltip. */
function TBtn({ title, onClick, active, children, disabled }) {
	return (
		<Tooltip title={title} disableInteractive>
			<span>
				<IconButton
					size="small"
					onClick={onClick}
					color={active ? 'primary' : 'default'}
					disabled={disabled}
					// Keep editor focus
					onMouseDown={e => e.preventDefault()}
				>
					{children}
				</IconButton>
			</span>
		</Tooltip>
	)
}

function VDivider() {
	return <Divider orientation="vertical" flexItem sx={{ mx: 0.25 }} />
}

// ── EditorToolbar ──────────────────────────────────────────────────────────────

/**
 * Props:
 *   editor            — TipTap editor instance (from useEditor in EditorPanel)
 *   settings          — current project settings
 *   onSettingsChange  — updateSettings(patch) from useProjectSettings
 *   onSceneBreak      — async callback: creates DB scene then inserts SceneBreak node
 *   isSaving          — boolean — shows saving spinner when true
 */
export default function EditorToolbar({ editor, settings, onSettingsChange, onSceneBreak, isSaving }) {
	const [settingsAnchor, setSettingsAnchor] = useState(null)

	// useEditorState re-renders this component reactively when editor state changes.
	// The selector runs on every transaction; keep it cheap.
	const state = useEditorState({
		editor,
		selector: ctx => {
			const e = ctx.editor
			if (!e) return {}
			const paraAttrs = e.getAttributes('paragraph')
			return {
				isBold:         e.isActive('bold'),
				isItalic:       e.isActive('italic'),
				isUnderline:    e.isActive('underline'),
				isBulletList:   e.isActive('bulletList'),
				isOrderedList:  e.isActive('orderedList'),
				isBlockquote:   e.isActive('blockquote'),
				currentStyle:
					e.isActive('heading', { level: 1 }) ? 'h1' :
					e.isActive('heading', { level: 2 }) ? 'h2' :
					e.isActive('heading', { level: 3 }) ? 'h3' : 'paragraph',
				paraFontFamily:       paraAttrs.fontFamily ?? null,
				paraFontSize:         paraAttrs.fontSize   ?? null,
				paraIndent:           paraAttrs.indent     ?? null,
				paraFirstLineIndent:  paraAttrs.firstLineIndent, // null = inherit
				textAlign:            paraAttrs.textAlign  ?? 'left',
				wordCount:            e.storage?.characterCount?.words?.() ?? 0,
			}
		},
	}) ?? {}

	const isPara = state.currentStyle === 'paragraph'

	// ── handlers ────────────────────────────────────────────────────────────

	function handleStyleChange(val) {
		const chain = editor?.chain().focus()
		if (!chain) return
		if (val === 'paragraph') {
			chain.setParagraph().run()
		} else {
			const level = parseInt(val[1], 10)
			chain.toggleHeading({ level }).run()
		}
	}

	function handleFontFamilyChange(val) {
		// null = inherit project default (no inline override)
		const attrVal = val === settings.fontFamily ? null : val
		editor?.chain().focus().updateAttributes('paragraph', { fontFamily: attrVal }).run()
	}

	function handleFontSizeChange(val) {
		const attrVal = val === settings.fontSize ? null : val
		editor?.chain().focus().updateAttributes('paragraph', { fontSize: attrVal }).run()
	}

	function handleIndent() {
		const cur = parseEm(state.paraIndent)
		editor?.chain().focus()
			.updateAttributes('paragraph', { indent: `${cur + 1.5}em` })
			.run()
	}

	function handleOutdent() {
		const cur = parseEm(state.paraIndent)
		const next = Math.max(0, cur - 1.5)
		editor?.chain().focus()
			.updateAttributes('paragraph', { indent: next > 0 ? `${next}em` : null })
			.run()
	}

	function handleFirstLineIndentToggle() {
		// Toggle between "no indent" override ('0') and "inherit default" (null).
		const isOverriddenOff = state.paraFirstLineIndent === '0'
		editor?.chain().focus()
			.updateAttributes('paragraph', { firstLineIndent: isOverriddenOff ? null : '0' })
			.run()
	}

	// Display values for dropdowns: paragraph override takes priority, then project default
	const displayFontFamily = state.paraFontFamily || settings.fontFamily || FONT_FAMILIES[0].value
	const displayFontSize   = state.paraFontSize   || settings.fontSize   || FONT_SIZES[3].value
	const firstLineOverriddenOff = state.paraFirstLineIndent === '0'

	// ── render ───────────────────────────────────────────────────────────────

	return (
		<Paper elevation={0} square sx={{ borderBottom: 1, borderColor: 'divider' }}>

			{/* ── Row 1: style / font / size / b-i-u / settings ─────────────── */}
			<Toolbar variant="dense" disableGutters sx={{ px: 1, gap: 0.25, minHeight: 38 }}>

				{/* Paragraph style */}
				<ToolbarSelect
					value={state.currentStyle || 'paragraph'}
					onChange={handleStyleChange}
					sx={{ minWidth: 86 }}
				>
					<MenuItem value="paragraph" sx={{ fontSize: '0.85rem' }}>Normal</MenuItem>
					<MenuItem value="h1"        sx={{ fontSize: '1.1rem', fontWeight: 700 }}>Heading 1</MenuItem>
					<MenuItem value="h2"        sx={{ fontSize: '0.85rem', fontWeight: 700 }}>Heading 2</MenuItem>
					<MenuItem value="h3"        sx={{ fontSize: '0.85rem', fontWeight: 600 }}>Heading 3</MenuItem>
				</ToolbarSelect>

				<VDivider />

				{/* Font family */}
				<ToolbarSelect
					value={displayFontFamily}
					onChange={handleFontFamilyChange}
					sx={{ minWidth: 100 }}
				>
					{FONT_FAMILIES.map(f => (
						<MenuItem key={f.value} value={f.value} sx={{ fontFamily: f.value, fontSize: '0.85rem' }}>
							{f.label}
						</MenuItem>
					))}
				</ToolbarSelect>

				{/* Font size */}
				<ToolbarSelect
					value={displayFontSize}
					onChange={handleFontSizeChange}
					sx={{ minWidth: 52 }}
				>
					{FONT_SIZES.map(s => (
						<MenuItem key={s.value} value={s.value} sx={{ fontSize: '0.85rem' }}>{s.label}</MenuItem>
					))}
				</ToolbarSelect>

				<VDivider />

				{/* Bold / Italic / Underline */}
				<TBtn title="Bold (Ctrl+B)"
					active={state.isBold}
					onClick={() => editor?.chain().focus().toggleBold().run()}
				>
					<FormatBoldIcon fontSize="small" />
				</TBtn>
				<TBtn title="Italic (Ctrl+I)"
					active={state.isItalic}
					onClick={() => editor?.chain().focus().toggleItalic().run()}
				>
					<FormatItalicIcon fontSize="small" />
				</TBtn>
				<TBtn title="Underline (Ctrl+U)"
					active={state.isUnderline}
					onClick={() => editor?.chain().focus().toggleUnderline().run()}
				>
					<FormatUnderlinedIcon fontSize="small" />
				</TBtn>

				{/* Right group — ml:auto pushes it right; flexShrink:0 prevents clipping */}
				<Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.25, flexShrink: 0 }}>
					<VDivider />
					{/* Doc settings */}
					<TBtn title="Document settings"
						onClick={e => setSettingsAnchor(e.currentTarget)}
					>
						<SettingsIcon fontSize="small" />
					</TBtn>
				</Box>
			</Toolbar>

			{/* ── Row 2: lists / indent / align / scene break ───────────────── */}
			<Toolbar variant="dense" disableGutters sx={{ px: 1, gap: 0.25, minHeight: 34, borderTop: 1, borderColor: 'divider' }}>

				{/* Lists */}
				<TBtn title="Bullet list"
					active={state.isBulletList}
					onClick={() => editor?.chain().focus().toggleBulletList().run()}
				>
					<FormatListBulletedIcon fontSize="small" />
				</TBtn>
				<TBtn title="Numbered list"
					active={state.isOrderedList}
					onClick={() => editor?.chain().focus().toggleOrderedList().run()}
				>
					<FormatListNumberedIcon fontSize="small" />
				</TBtn>
				<TBtn title="Block quote"
					active={state.isBlockquote}
					onClick={() => editor?.chain().focus().toggleBlockquote().run()}
				>
					<FormatQuoteIcon fontSize="small" />
				</TBtn>

				<VDivider />

				{/* Block indent / outdent */}
				<TBtn title="Increase indent" onClick={handleIndent} disabled={!isPara}>
					<FormatIndentIncreaseIcon fontSize="small" />
				</TBtn>
				<TBtn title="Decrease indent" onClick={handleOutdent} disabled={!isPara}>
					<FormatIndentDecreaseIcon fontSize="small" />
				</TBtn>

				{/* First-line indent toggle: ¶ icon styled like a toolbar button */}
				<Tooltip title={firstLineOverriddenOff ? 'Restore first-line indent' : 'Remove first-line indent'} disableInteractive>
					<IconButton
						size="small"
						onClick={handleFirstLineIndentToggle}
						color={firstLineOverriddenOff ? 'primary' : 'default'}
						disabled={!isPara}
						onMouseDown={e => e.preventDefault()}
						sx={{ fontSize: '0.85rem', fontWeight: 700, px: 0.75 }}
					>
						¶
					</IconButton>
				</Tooltip>

				<VDivider />

				{/* Text align */}
				<TBtn title="Align left"
					active={state.textAlign === 'left' || !state.textAlign}
					onClick={() => editor?.chain().focus().setTextAlign('left').run()}
				>
					<FormatAlignLeftIcon fontSize="small" />
				</TBtn>
				<TBtn title="Align center"
					active={state.textAlign === 'center'}
					onClick={() => editor?.chain().focus().setTextAlign('center').run()}
				>
					<FormatAlignCenterIcon fontSize="small" />
				</TBtn>
				<TBtn title="Align right"
					active={state.textAlign === 'right'}
					onClick={() => editor?.chain().focus().setTextAlign('right').run()}
				>
					<FormatAlignRightIcon fontSize="small" />
				</TBtn>

				<VDivider />

				{/* Scene break — async: creates DB scene first, then inserts node */}
				<TBtn title="Scene break (· · ·)"
					onClick={onSceneBreak}
				>
					<HorizontalRuleIcon fontSize="small" />
				</TBtn>

				{/* Word count + save indicator pushed right */}
				<Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.75, flexShrink: 0 }}>
					{isSaving && <CircularProgress size={12} />}
					<Typography variant="caption" color="text.secondary">
						{state.wordCount ?? 0} words
					</Typography>
				</Box>
			</Toolbar>

			{/* Doc settings popover */}
			<DocSettingsPopover
				anchorEl={settingsAnchor}
				onClose={() => setSettingsAnchor(null)}
				settings={settings}
				onSave={onSettingsChange}
			/>
		</Paper>
	)
}
