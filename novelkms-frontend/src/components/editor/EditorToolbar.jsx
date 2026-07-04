import { useState, useRef, useMemo } from 'react'
import { useEditorState } from '@tiptap/react'
import { NodeSelection } from '@tiptap/pm/state'
import {
	Box, Toolbar, IconButton, Tooltip, Divider,
	Select, MenuItem, Menu, CircularProgress, Typography,
	Popover, TextField, Button, Chip,
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
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import EditNoteIcon from '@mui/icons-material/EditNote'
import { useReview } from '../../review/ReviewContext'
import { STYLE_ORDER, STYLE_LABELS, HEADING_KEYS } from '../../utils/styles'
import AiDocProviderSelect from '../ai/AiDocProviderSelect'

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
	{ label: '0 pt', value: '0pt' },
	{ label: '6 pt', value: '6pt' },
	{ label: '12 pt', value: '12pt' },
	{ label: '18 pt', value: '18pt' },
	{ label: '24 pt', value: '24pt' },
]

// ── helpers ────────────────────────────────────────────────────────────────────

function parseEm(val) {
	if (!val) return 0
	const m = String(val).match(/^([\d.]+)em$/)
	return m ? parseFloat(m[1]) : 0
}

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
			onMouseDown={e => e.preventDefault()}
		>
			{children}
		</Select>
	)
}

function TBtn({ title, onClick, active, children, disabled }) {
	return (
		<Tooltip title={title} disableInteractive>
			<span>
				<IconButton
					size="small"
					onClick={onClick}
					color={active ? 'primary' : 'default'}
					disabled={disabled}
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
 *   onSceneBreak      — async callback: creates DB scene then inserts SceneBreak node
 *   isSaving          — boolean — shows saving spinner when true
 *   templateMode      — boolean — true when editing a page template
 *   tokenOptions      — [{ token, label }] for the Insert-field menu (template mode)
 *   onInsertToken     — (token) => void — inserts a TemplateToken at the cursor
 *   previewActive     — boolean — true when the resolved preview is showing
 *   onTogglePreview   — () => void — toggles the preview
 *   styleSheet        — resolved style definitions array
 *   wordCountOverride — number | null — when set, displayed instead of TipTap's live
 *                       count; used for book/part preview modes where there is no editor
 *   headingWordCount  — number — extra words from chapter/part headings not in the
 *                       TipTap document; added to the live count in chapter mode
 *   canReview         — boolean — true when the selection is a reviewable manuscript
 *                       chapter or scene (enables the AI Review toggle)
 *   isScene           — boolean — true when the selection is a scene (adjusts tooltip)
 *   aiDocMode         — boolean — true when editing a memory document / chapter or
 *                       book summary (not manuscript text)
 *   aiDocTypeLabel    — string — e.g. "Memory document", shown as a tooltip/label
 *   aiDocStatus       — { label, color, tooltip } | null — staleness chip data
 *   aiDocBusy         — boolean — generation in progress
 *   aiDocHasContent   — boolean — whether a document already exists (Generate vs Regenerate)
 *   aiDocGuidance     — string — one-time guidance text for the next generation
 *   onAiDocGuidanceChange — (text) => void
 *   onAiDocGenerate   — () => void — runs (or gates, then runs) generation
 *   aiDocCanGenerate  — boolean — whether the selected provider can be generated
 *                       under (a credential exists for it); Generate is disabled otherwise
 *   aiDocProviderSelect — props object for the per-provider variant selector, or
 *                       null when not in AI-doc mode (see AiDocProviderSelect)
 *   onOpenContextSettings — opens the selected project/book settings dialog
 *   contextSettingsLabel  — label for the selected project/book settings action
 */
export default function EditorToolbar({
	editor, settings = {}, onSceneBreak, isSaving,
	templateMode = false, tokenOptions = [], onInsertToken,
	previewActive = false, onTogglePreview,
	styleSheet = [],
	wordCountOverride = null,
	headingWordCount = 0,
	canReview = false,
	isScene = false,
	aiDocMode = false,
	aiDocTypeLabel = '',
	aiDocStatus = null,
	aiDocBusy = false,
	aiDocHasContent = false,
	aiDocGuidance = '',
	onAiDocGuidanceChange,
	onAiDocGenerate,
	aiDocCanGenerate = true,
	aiDocProviderSelect = null,
	onOpenContextSettings,
	contextSettingsLabel = '',
}) {
	const [fieldAnchor, setFieldAnchor] = useState(null)
	const [guidanceAnchor, setGuidanceAnchor] = useState(null)

	const imageInputRef = useRef(null)

	const review = useReview()

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
				paraSpacingAfter: paraAttrs.spacingAfter ?? null,
				markFontSize: e.getAttributes('fontSize')?.size ?? null,
				textAlign: paraAttrs.textAlign ?? 'left',
				wordCount: e.storage?.characterCount?.words?.() ?? 0,
			}
		},
	}) ?? {}

	const isPara = !HEADING_KEYS.includes(state.currentStyle)
	const isNodeSelection = editor ? editor.state.selection instanceof NodeSelection : false

	const currentStyleDef = useMemo(() => {
		if (!Array.isArray(styleSheet) || !state.currentStyle) return null
		const entry = styleSheet.find(s => s.styleKey === state.currentStyle)
		return entry?.definition ?? null
	}, [styleSheet, state.currentStyle])

	const formatDisabled = previewActive

	// ── word count display ────────────────────────────────────────────────────
	//
	// Priority:
	//   wordCountOverride — for book/part preview modes (no live editor; value
	//     fetched from GET /api/books/{id}/word-count or /api/parts/{id}/word-count)
	//   state.wordCount + headingWordCount — for chapter/scene editor modes;
	//     headingWordCount adds the chapter title/subtitle words that live outside
	//     the TipTap document
	const displayWordCount = wordCountOverride != null
		? wordCountOverride
		: (state.wordCount ?? 0) + (headingWordCount ?? 0)

	// ── paragraph-attribute helpers ──────────────────────────────────────────

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
		const attrVal = val === settings?.fontFamily ? null : val
		applyParaPatch({ fontFamily: attrVal })
	}

	function handleFontSizeChange(val) {
		if (!editor) return
		if (isNodeSelection) {
			if (val === settings?.fontSize) editor.chain().focus().unsetFontSize().run()
			else editor.chain().focus().setFontSize(val).run()
			return
		}
		const attrVal = val === settings?.fontSize ? null : val
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

	function handleImageFileSelected(e) {
		const file = e.target.files?.[0]
		if (!file || !editor) return
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
	const effectiveBold = state.isBold || (currentStyleDef?.bold ?? false)
	const effectiveItalic = state.isItalic || (currentStyleDef?.italic ?? false)
	const firstLineOverriddenOff = state.paraFirstLineIndent === '0'

	const displaySpacingBefore = state.paraSpacingBefore || ''
	const displaySpacingAfter = state.paraSpacingAfter || ''

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

				{/* Right group */}
				<Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.25, flexShrink: 0 }}>

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

					{templateMode && (
						<TBtn
							title={previewActive ? 'Back to editing' : 'Preview with sample values'}
							active={previewActive}
							onClick={onTogglePreview}
						>
							{previewActive ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
						</TBtn>
					)}

					{aiDocMode && (
						<>
							{aiDocProviderSelect && (
								<AiDocProviderSelect {...aiDocProviderSelect} disabled={aiDocBusy} />
							)}
							{aiDocStatus && (
								<Tooltip title={aiDocStatus.tooltip ?? ''}>
									<Chip size="small" color={aiDocStatus.color} variant="outlined" label={aiDocStatus.label} sx={{ mr: 0.5, ml: 0.5 }} />
								</Tooltip>
							)}
							<TBtn
								title={aiDocGuidance.trim() ? 'Edit guidance for the next generation' : 'Add guidance for the next generation (optional)'}
								active={!!aiDocGuidance.trim()}
								onClick={e => setGuidanceAnchor(e.currentTarget)}
							>
								<EditNoteIcon fontSize="small" />
							</TBtn>
							<Popover
								open={!!guidanceAnchor}
								anchorEl={guidanceAnchor}
								onClose={() => setGuidanceAnchor(null)}
								anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
							>
								<Box sx={{ p: 1.5, width: 320 }}>
									<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
										Guidance for the next generation (optional)
									</Typography>
									<TextField
										autoFocus
										value={aiDocGuidance}
										onChange={e => onAiDocGuidanceChange?.(e.target.value)}
										placeholder="e.g. the letter in this chapter is canonically a forgery"
										fullWidth
										multiline
										minRows={2}
										maxRows={6}
										size="small"
									/>
									{aiDocGuidance.trim() && (
										<Button size="small" sx={{ mt: 0.5 }} onClick={() => onAiDocGuidanceChange?.('')}>
											Clear guidance
										</Button>
									)}
								</Box>
							</Popover>
							<TBtn
								title={aiDocBusy
									? 'Working…'
									: !aiDocCanGenerate
										? `Add a key for this provider in Settings to ${aiDocHasContent ? 'regenerate' : 'generate'}`
										: (aiDocHasContent ? `Regenerate this ${aiDocTypeLabel.toLowerCase()}` : `Generate this ${aiDocTypeLabel.toLowerCase()}`)}
								onClick={onAiDocGenerate}
								disabled={aiDocBusy || !aiDocCanGenerate}
							>
								{aiDocBusy ? <CircularProgress size={16} /> : <AutoAwesomeIcon fontSize="small" />}
							</TBtn>
						</>
					)}

					{canReview && (
						<TBtn
							title={review.open
								? 'Hide AI review'
								: (isScene ? 'Review this scene with AI' : 'Review this chapter with AI')}
							active={review.open}
							onClick={() => review.open ? review.closeReview() : review.openReview()}
						>
							<CheckCircleOutlinedIcon fontSize="small" />
						</TBtn>
					)}

					<VDivider />
					<TBtn
						title={contextSettingsLabel || 'Settings unavailable'}
						onClick={onOpenContextSettings}
						disabled={!contextSettingsLabel || !onOpenContextSettings}
					>
						<SettingsIcon fontSize="small" />
					</TBtn>
				</Box>
			</Toolbar>

			{/* ── Row 2: lists / indent / align / scene break / image / word count ── */}
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

				{/* Space before / after paragraph */}
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

				{/* Insert image */}
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

				{/* Word count + save indicator pushed right.
				    displayWordCount is context-sensitive:
				      book/part preview modes → fetched total from API (wordCountOverride)
				      chapter mode           → TipTap live count + heading word count
				      scene mode             → TipTap live count only */}
				<Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.75, flexShrink: 0 }}>
					{isSaving && <CircularProgress size={12} />}
					<Typography variant="caption" color="text.secondary">
						{displayWordCount.toLocaleString('en-US')} words
					</Typography>
				</Box>
			</Toolbar>

		</Box>
	)
}