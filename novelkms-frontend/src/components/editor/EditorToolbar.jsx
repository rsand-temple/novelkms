import { useState, useRef, useMemo } from 'react'
import { useEditorState } from '@tiptap/react'
import { NodeSelection } from '@tiptap/pm/state'
import {
	Box, Toolbar, IconButton, Tooltip, Divider,
	Select, MenuItem, Menu, CircularProgress, Typography,
} from '@mui/material'
import FormatBoldIcon from '@mui/icons-material/FormatBold'
import FormatItalicIcon from '@mui/icons-material/FormatItalic'
import FormatUnderlinedIcon from '@mui/icons-material/FormatUnderlined'
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted'
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered'
import FormatIndentIncreaseIcon from '@mui/icons-material/FormatIndentIncrease'
import FormatIndentDecreaseIcon from '@mui/icons-material/FormatIndentDecrease'
import FormatAlignLeftIcon from '@mui/icons-material/FormatAlignLeft'
import FormatAlignCenterIcon from '@mui/icons-material/FormatAlignCenter'
import FormatAlignRightIcon from '@mui/icons-material/FormatAlignRight'
import HorizontalRuleIcon from '@mui/icons-material/HorizontalRule'
import SettingsIcon from '@mui/icons-material/Settings'
import FormatQuoteIcon from '@mui/icons-material/FormatQuote'
import DataObjectIcon from '@mui/icons-material/DataObject'
import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate'
import DocSettingsPopover from './DocSettingsPopover'
import { STYLE_ORDER, STYLE_LABELS, HEADING_KEYS } from '../../utils/styles'

// ── font options ───────────────────────────────────────────────────────────────

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
	{ label: '28', value: '1.75rem' },
	{ label: '32', value: '2rem' },
	{ label: '36', value: '2.25rem' },
	{ label: '40', value: '2.5rem' },
]

const SPACING_PRESETS = [
	{ label: 'Default', value: '' },
	{ label: '0 pt',    value: '0pt' },
	{ label: '6 pt',    value: '6pt' },
	{ label: '12 pt',   value: '12pt' },
	{ label: '18 pt',   value: '18pt' },
	{ label: '24 pt',   value: '24pt' },
]

// ── helpers ────────────────────────────────────────────────────────────────────

function parseEm(val) {
	if (!val) return 0
	const m = String(val).match(/^([\d.]+)em$/)
	return m ? parseFloat(m[1]) : 0
}

/**
 * When a TemplateToken atom is clicked it becomes a NodeSelection, so
 * getAttributes('paragraph') returns {} (the selection is *on* the token, not
 * inside its parent block). Walk up from $from to the nearest ancestor
 * paragraph and return its attrs so the toolbar still displays correctly.
 */
function resolveParaAttrs(editor) {
	if (!editor) return {}
	const { selection } = editor.state
	const direct = editor.getAttributes('paragraph')
	if (Object.keys(direct).length > 0 || !(selection instanceof NodeSelection)) {
		return direct
	}
	const $pos = selection.$from
	for (let d = $pos.depth; d >= 0; d--) {
		const node = $pos.node(d)
		if (node.type.name === 'paragraph') return node.attrs
	}
	return {}
}

// ── sub-components ─────────────────────────────────────────────────────────────

/** Compact Select with no underline, suitable for embedding in a toolbar. */
function ToolbarSelect({ value, onChange, children, sx, disabled }) {
	return (
		<Select
			value={value}
			onChange={e => onChange(e.target.value)}
			variant="standard"
			disableUnderline
			size="small"
			disabled={disabled}
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
					sx={active ? {
						bgcolor: 'action.selected',
						borderRadius: 1,
						'&:hover': { bgcolor: 'action.focus' },
					} : undefined}
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
 *   templateMode      — boolean — true when editing a page template
 *   tokenOptions      — [{ token, label }] for the Insert-field menu (template mode)
 *   onInsertToken     — (token) => void — inserts a TemplateToken at the cursor
 *   previewActive     — boolean — true when the resolved preview is showing
 *   onTogglePreview   — () => void — toggles the preview
 */
export default function EditorToolbar({
	editor, settings = {}, onSettingsChange, onSceneBreak, isSaving,
	templateMode = false, tokenOptions = [], onInsertToken,
	previewActive = false, onTogglePreview,
	styleSheet = [],
}) {
	const [settingsAnchor, setSettingsAnchor] = useState(null)
	const [fieldAnchor, setFieldAnchor] = useState(null)

	// Hidden file input for image insertion. Triggered via ref so we avoid the
	// MUI Button component="label" nesting edge cases (same pattern as cover image
	// upload in PropertiesPanel).
	const imageInputRef = useRef(null)

	// useEditorState re-renders this component reactively when editor state changes.
	const state = useEditorState({
		editor,
		selector: ctx => {
			const e = ctx.editor
			if (!e) return {}
			const paraAttrs = resolveParaAttrs(e)
			return {
				isBold: e.isActive('bold'),
				isItalic: e.isActive('italic'),
				isUnderline: e.isActive('underline'),
				isBulletList: e.isActive('bulletList'),
				isOrderedList: e.isActive('orderedList'),
				isBlockquote: e.isActive('blockquote'),
				currentStyle:
					e.isActive('heading', { level: 1 }) ? 'h1' :
						e.isActive('heading', { level: 2 }) ? 'h2' :
							e.isActive('heading', { level: 3 }) ? 'h3' :
								(paraAttrs.styleKey || 'normal'),
				paraStyleKey: paraAttrs.styleKey ?? null,
				paraFontFamily: paraAttrs.fontFamily ?? null,
				paraFontSize: paraAttrs.fontSize ?? null,
				paraIndent: paraAttrs.indent ?? null,
				paraFirstLineIndent: paraAttrs.firstLineIndent,
				paraSpacingBefore: paraAttrs.spacingBefore ?? null,
				paraSpacingAfter:  paraAttrs.spacingAfter  ?? null,
				// Inline FontSize mark (used for field-level sizing)
				markFontSize: e.getAttributes('fontSize')?.size ?? null,
				textAlign: paraAttrs.textAlign ?? 'left',
				wordCount: e.storage?.characterCount?.words?.() ?? 0,
			}
		},
	}) ?? {}

	const isPara = !HEADING_KEYS.includes(state.currentStyle)

	// Is a field (or any atom) currently selected?
	const isNodeSelection = editor ? editor.state.selection instanceof NodeSelection : false

	// Look up the resolved definition for the paragraph style at the cursor.
	// This is used to reflect style-level font / bold / italic in the toolbar
	// even when no inline paragraph override is present.
	const currentStyleDef = useMemo(() => {
		if (!Array.isArray(styleSheet) || !state.currentStyle) return null
		const entry = styleSheet.find(s => s.styleKey === state.currentStyle)
		return entry?.definition ?? null
	}, [styleSheet, state.currentStyle])

	// All formatting controls are disabled while preview is showing — the editor
	// is non-editable in that mode so every command would silently do nothing.
	const formatDisabled = previewActive

	// ── paragraph-attribute helpers ──────────────────────────────────────────

	/**
	 * Set attributes on the paragraph that contains the current selection,
	 * resolving the parent paragraph directly. Works under a NodeSelection
	 * (e.g. a selected field) where updateAttributes('paragraph') finds nothing.
	 * Returns false if there is no paragraph ancestor (e.g. a field in a heading).
	 */
	function setParagraphAttrs(patch) {
		if (!editor) return false
		const { state: s } = editor
		const { $from } = s.selection
		for (let d = $from.depth; d >= 1; d--) {
			const node = $from.node(d)
			if (node.type.name === 'paragraph') {
				const pos = $from.before(d)
				editor.view.dispatch(s.tr.setNodeMarkup(pos, undefined, { ...node.attrs, ...patch }))
				editor.commands.focus()
				return true
			}
		}
		editor.commands.focus()
		return false
	}

	/**
	 * Apply a paragraph-attribute patch. For ordinary text selections this uses
	 * the standard updateAttributes path (unchanged behavior, handles multi-
	 * paragraph ranges). For a NodeSelection it resolves the parent paragraph.
	 */
	function applyParaPatch(patch) {
		if (!editor) return
		if (isNodeSelection) {
			setParagraphAttrs(patch)
		} else {
			editor.chain().focus().updateAttributes('paragraph', patch).run()
		}
	}

	// ── handlers ────────────────────────────────────────────────────────────

	function handleStyleChange(val) {
		const chain = editor?.chain().focus()
		if (!chain) return
		if (HEADING_KEYS.includes(val)) {
			const level = parseInt(val[1], 10)
			chain.setHeading({ level }).run()
			return
		}
		// A paragraph style (normal or a block style). Applying a style clears
		// manual paragraph-level format overrides so the definition shows cleanly.
		const styleKey = val === 'normal' ? null : val
		chain
			.setParagraph()
			.updateAttributes('paragraph', {
				styleKey,
				fontFamily: null, fontSize: null, indent: null,
				firstLineIndent: null, spacingBefore: null, spacingAfter: null,
			})
			.run()
	}

	function handleFontFamilyChange(val) {
		// null = inherit project default (no inline override)
		const attrVal = val === settings?.fontFamily ? null : val
		applyParaPatch({ fontFamily: attrVal })
	}

	function handleFontSizeChange(val) {
		if (!editor) return
		// A selected field (or any atom) is sized inline via the FontSize mark —
		// this works whether the field sits in a paragraph or a heading.
		if (isNodeSelection) {
			if (val === settings?.fontSize) editor.chain().focus().unsetFontSize().run()
			else editor.chain().focus().setFontSize(val).run()
			return
		}
		const attrVal = val === settings?.fontSize ? null : val
		// In heading nodes (common in templates) StyledParagraph attributes are not
		// available. Fall back to the FontSize inline mark, which works on any node.
		if (HEADING_KEYS.includes(state.currentStyle)) {
			if (attrVal === null) editor.chain().focus().unsetFontSize().run()
			else editor.chain().focus().setFontSize(attrVal).run()
		} else {
			editor.chain().focus().updateAttributes('paragraph', { fontSize: attrVal }).run()
		}
	}

	function handleIndent() {
		const cur = parseEm(state.paraIndent)
		applyParaPatch({ indent: `${cur + 1.5}em` })
	}

	function handleOutdent() {
		const cur = parseEm(state.paraIndent)
		const next = Math.max(0, cur - 1.5)
		applyParaPatch({ indent: next > 0 ? `${next}em` : null })
	}

	function handleFirstLineIndentToggle() {
		const isOverriddenOff = state.paraFirstLineIndent === '0'
		applyParaPatch({ firstLineIndent: isOverriddenOff ? null : '0' })
	}

	function handleSpacingBeforeChange(val) {
		applyParaPatch({ spacingBefore: val === '' ? null : val })
	}

	function handleSpacingAfterChange(val) {
		applyParaPatch({ spacingAfter: val === '' ? null : val })
	}

	function handleInsertTokenClick(token) {
		onInsertToken?.(token)
		setFieldAnchor(null)
	}

	/**
	 * Called when the user selects a file from the hidden image input.
	 * Reads the file as a base64 data URL and inserts an Image node at the
	 * current cursor position. The data URL is stored verbatim in the scene
	 * content HTML — no separate image endpoint is used.
	 */
	function handleImageFileSelected(e) {
		const file = e.target.files?.[0]
		if (!file || !editor) return

		// Reset the input so the same file can be re-selected after removal.
		e.target.value = ''

		const reader = new FileReader()
		reader.onload = (ev) => {
			const src = ev.target.result
			if (src) {
				editor.chain().focus().setImage({ src }).run()
			}
		}
		reader.readAsDataURL(file)
	}

	// Display values: inline mark > paragraph override > style definition > project default
	const displayFontFamily =
		state.paraFontFamily ||
		currentStyleDef?.fontFamily ||
		settings?.fontFamily ||
		FONT_FAMILIES[0].value
	const styleDefFontSize = FONT_SIZES.some(f => f.value === currentStyleDef?.fontSize)
		? currentStyleDef.fontSize
		: null
	const displayFontSize =
		state.markFontSize ||
		state.paraFontSize ||
		styleDefFontSize ||
		settings?.fontSize ||
		FONT_SIZES[3].value
	// Bold / italic: depressed when the inline mark is active OR when the
	// resolved style definition declares that property.
	const effectiveBold = state.isBold || (currentStyleDef?.bold ?? false)
	const effectiveItalic = state.isItalic || (currentStyleDef?.italic ?? false)
	const firstLineOverriddenOff = state.paraFirstLineIndent === '0'

	// Spacing before/after: show the inline override value, or '' (Default) when
	// none is set (meaning the paragraph inherits from the style definition or
	// DocSettings global).
	const displaySpacingBefore = state.paraSpacingBefore || ''
	const displaySpacingAfter  = state.paraSpacingAfter  || ''

	const showFieldMenu = templateMode && tokenOptions.length > 0

	// ── render ───────────────────────────────────────────────────────────────

	return (
		<Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>

			{/* ── Row 1: style / font / size / b-i-u / fields / preview / settings ── */}
			<Toolbar variant="dense" disableGutters sx={{ px: 1, gap: 0.25, minHeight: 38 }}>

				{/* Paragraph style */}
				<ToolbarSelect
					value={state.currentStyle || 'normal'}
					onChange={handleStyleChange}
					sx={{ minWidth: 132 }}
					disabled={formatDisabled}
				>
					{STYLE_ORDER.flatMap((key) => {
						const item = (
							<MenuItem
								key={key}
								value={key}
								sx={{
									fontSize: HEADING_KEYS.includes(key) ? '0.95rem' : '0.85rem',
									fontWeight: HEADING_KEYS.includes(key) ? 700 : 400,
								}}
							>
								{STYLE_LABELS[key]}
							</MenuItem>
						)
						// Separate the heading group from the paragraph styles.
						return key === 'h3' ? [item, <Divider key="style-div" sx={{ my: 0.5 }} />] : [item]
					})}
				</ToolbarSelect>

				<VDivider />

				{/* Font family */}
				<ToolbarSelect
					value={displayFontFamily}
					onChange={handleFontFamilyChange}
					sx={{ minWidth: 100 }}
					disabled={formatDisabled}
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
					disabled={formatDisabled}
				>
					{FONT_SIZES.map(s => (
						<MenuItem key={s.value} value={s.value} sx={{ fontSize: '0.85rem' }}>{s.label}</MenuItem>
					))}
				</ToolbarSelect>

				<VDivider />

				{/* Bold / Italic / Underline */}
				<TBtn title="Bold (Ctrl+B)"
					active={effectiveBold}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleBold().run()}
				>
					<FormatBoldIcon fontSize="small" />
				</TBtn>
				<TBtn title="Italic (Ctrl+I)"
					active={effectiveItalic}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleItalic().run()}
				>
					<FormatItalicIcon fontSize="small" />
				</TBtn>
				<TBtn title="Underline (Ctrl+U)"
					active={state.isUnderline}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleUnderline().run()}
				>
					<FormatUnderlinedIcon fontSize="small" />
				</TBtn>

				{/* Right group — ml:auto pushes it right; flexShrink:0 prevents clipping */}
				<Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.25, flexShrink: 0 }}>

					{/* Insert field (template mode only) */}
					{showFieldMenu && (
						<>
							<TBtn
								title="Insert field"
								onClick={e => setFieldAnchor(e.currentTarget)}
								disabled={previewActive}
							>
								<DataObjectIcon fontSize="small" />
							</TBtn>
							<Menu
								anchorEl={fieldAnchor}
								open={!!fieldAnchor}
								onClose={() => setFieldAnchor(null)}
							>
								<MenuItem disabled sx={{ fontSize: '0.72rem', opacity: 0.7 }}>Insert field</MenuItem>
								{tokenOptions.map(opt => (
									<MenuItem
										key={opt.token}
										onClick={() => handleInsertTokenClick(opt.token)}
										sx={{ fontSize: '0.85rem' }}
									>
										{opt.label}
									</MenuItem>
								))}
							</Menu>
						</>
					)}

					{/* Preview toggle (template mode only) */}
					{templateMode && (
						<TBtn
							title={previewActive ? 'Back to editing' : 'Preview with sample values'}
							active={previewActive}
							onClick={onTogglePreview}
						>
							{previewActive ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
						</TBtn>
					)}

					<VDivider />
					{/* Doc settings */}
					<TBtn title="Document settings"
						onClick={e => setSettingsAnchor(e.currentTarget)}
					>
						<SettingsIcon fontSize="small" />
					</TBtn>
				</Box>
			</Toolbar>

			{/* ── Row 2: lists / indent / align / scene break / image ───────── */}
			<Toolbar variant="dense" disableGutters sx={{ px: 1, gap: 0.25, minHeight: 34, borderTop: 1, borderColor: 'divider' }}>

				{/* Lists */}
				<TBtn title="Bullet list"
					active={state.isBulletList}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleBulletList().run()}
				>
					<FormatListBulletedIcon fontSize="small" />
				</TBtn>
				<TBtn title="Numbered list"
					active={state.isOrderedList}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleOrderedList().run()}
				>
					<FormatListNumberedIcon fontSize="small" />
				</TBtn>
				<TBtn title="Block quote"
					active={state.isBlockquote}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().toggleBlockquote().run()}
				>
					<FormatQuoteIcon fontSize="small" />
				</TBtn>

				<VDivider />

				{/* Block indent / outdent */}
				<TBtn title="Increase indent" onClick={handleIndent} disabled={formatDisabled || !isPara}>
					<FormatIndentIncreaseIcon fontSize="small" />
				</TBtn>
				<TBtn title="Decrease indent" onClick={handleOutdent} disabled={formatDisabled || !isPara}>
					<FormatIndentDecreaseIcon fontSize="small" />
				</TBtn>

				{/* First-line indent toggle */}
				<Tooltip title={firstLineOverriddenOff ? 'Restore first-line indent' : 'Remove first-line indent'} disableInteractive>
					<IconButton
						size="small"
						onClick={handleFirstLineIndentToggle}
						color={firstLineOverriddenOff ? 'primary' : 'default'}
						disabled={formatDisabled || !isPara}
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
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().setTextAlign('left').run()}
				>
					<FormatAlignLeftIcon fontSize="small" />
				</TBtn>
				<TBtn title="Align center"
					active={state.textAlign === 'center'}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().setTextAlign('center').run()}
				>
					<FormatAlignCenterIcon fontSize="small" />
				</TBtn>
				<TBtn title="Align right"
					active={state.textAlign === 'right'}
					disabled={formatDisabled}
					onClick={() => editor?.chain().focus().setTextAlign('right').run()}
				>
					<FormatAlignRightIcon fontSize="small" />
				</TBtn>

				<VDivider />

				{/* Space before / after paragraph — inline override; 'Default' clears
				    the override and defers to the paragraph style definition or the
				    project-level DocSettings global. Only active on paragraph nodes
				    (not headings, which use native TipTap heading nodes). */}
				<Tooltip title="Space before paragraph" disableInteractive>
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
						<Typography sx={{ fontSize: '0.7rem', color: 'text.secondary', lineHeight: 1, userSelect: 'none' }}>↑</Typography>
						<ToolbarSelect
							value={displaySpacingBefore}
							onChange={handleSpacingBeforeChange}
							sx={{ minWidth: 72 }}
							disabled={formatDisabled || !isPara}
						>
							{SPACING_PRESETS.map(p => (
								<MenuItem
									key={p.value === '' ? '__default' : p.value}
									value={p.value}
									sx={{ fontSize: '0.85rem' }}
								>
									{p.label}
								</MenuItem>
							))}
						</ToolbarSelect>
					</Box>
				</Tooltip>
				<Tooltip title="Space after paragraph" disableInteractive>
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
						<Typography sx={{ fontSize: '0.7rem', color: 'text.secondary', lineHeight: 1, userSelect: 'none' }}>↓</Typography>
						<ToolbarSelect
							value={displaySpacingAfter}
							onChange={handleSpacingAfterChange}
							sx={{ minWidth: 72 }}
							disabled={formatDisabled || !isPara}
						>
							{SPACING_PRESETS.map(p => (
								<MenuItem
									key={p.value === '' ? '__default' : p.value}
									value={p.value}
									sx={{ fontSize: '0.85rem' }}
								>
									{p.label}
								</MenuItem>
							))}
						</ToolbarSelect>
					</Box>
				</Tooltip>

				<VDivider />

				{/* Scene break */}
				<TBtn
					title={onSceneBreak ? 'Scene break' : 'Not available here'}
					onClick={onSceneBreak ?? undefined}
					disabled={!onSceneBreak}
				>
					<HorizontalRuleIcon fontSize="small" />
				</TBtn>

				<VDivider />

				{/* Insert image — triggers a hidden file input via ref.
				    Disabled when the editor is not active (page preview modes,
				    template preview). Base64 data URL is embedded directly in
				    scene/template content HTML; no image storage endpoint is used. */}
				<TBtn
					title="Insert image"
					onClick={() => imageInputRef.current?.click()}
					disabled={!editor}
				>
					<AddPhotoAlternateIcon fontSize="small" />
				</TBtn>
				<input
					ref={imageInputRef}
					type="file"
					accept="image/*"
					style={{ display: 'none' }}
					onChange={handleImageFileSelected}
				/>

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
		</Box>
	)
}