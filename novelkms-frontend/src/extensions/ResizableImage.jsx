import Image from '@tiptap/extension-image'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import { useState, useRef, useCallback, useEffect } from 'react'
import { Box, Paper, IconButton, Divider, Typography } from '@mui/material'
import FormatAlignLeftIcon   from '@mui/icons-material/FormatAlignLeft'
import FormatAlignCenterIcon from '@mui/icons-material/FormatAlignCenter'
import FormatAlignRightIcon  from '@mui/icons-material/FormatAlignRight'

// ── Node view component ───────────────────────────────────────────────────────

/**
 * ResizableImageView
 *
 * Rendered by ReactNodeViewRenderer for every image node in the document.
 *
 * When the node is selected (clicked):
 *   • A floating control bar appears above the image with:
 *       – Left / Center / Right alignment toggle
 *       – Width input (px); blank = natural width up to 100% of content column
 *   • A drag handle appears at the bottom-right corner.  Dragging it to the
 *     right increases the image width; dragging left decreases it.
 *
 * Width and alignment are stored as HTML attributes on the <img> tag
 * (width="N" data-align="center") so they round-trip through autosave
 * without any backend changes.
 */
function ResizableImageView({ node, updateAttributes, selected }) {
	const { src, alt, title, width, align = 'center' } = node.attrs
	const imgRef = useRef(null)

	// Local controlled state for the width text field.  We commit to the node
	// attribute on blur / Enter rather than on every keystroke, so the user can
	// type freely without the field jumping.  When the attribute changes
	// externally (e.g. during a drag) the effect below syncs it back.
	const [widthInput, setWidthInput] = useState(width ?? '')
	useEffect(() => { setWidthInput(width ?? '') }, [width])

	// ── drag-to-resize ────────────────────────────────────────────────────────

	const handleResizeStart = useCallback((e) => {
		e.preventDefault()
		e.stopPropagation()

		// Capture the image's rendered width at the moment the drag begins.
		// Falls back to the stored attribute width, then a safe default.
		const startX     = e.clientX
		const startWidth = imgRef.current?.offsetWidth ?? width ?? 300

		const onMouseMove = (ev) => {
			const newWidth = Math.max(50, Math.round(startWidth + (ev.clientX - startX)))
			updateAttributes({ width: newWidth })
		}
		const onMouseUp = () => {
			window.removeEventListener('mousemove', onMouseMove)
			window.removeEventListener('mouseup',   onMouseUp)
		}
		window.addEventListener('mousemove', onMouseMove)
		window.addEventListener('mouseup',   onMouseUp)
	}, [width, updateAttributes])

	// ── width field helpers ───────────────────────────────────────────────────

	const commitWidth = useCallback(() => {
		const v = parseInt(String(widthInput), 10)
		// Blank or invalid input → null (auto width).
		updateAttributes({ width: isNaN(v) || v < 50 ? null : v })
	}, [widthInput, updateAttributes])

	const handleWidthKeyDown = useCallback((e) => {
		if (e.key === 'Enter') { e.preventDefault(); commitWidth() }
	}, [commitWidth])

	// ── alignment helpers ─────────────────────────────────────────────────────

	// Flex justification that positions the image within the full-width block.
	const justifyContent =
		align === 'left'  ? 'flex-start' :
		align === 'right' ? 'flex-end'   :
		'center'

	const alignBtn = (dir, Icon) => (
		<IconButton
			key={dir}
			size="small"
			sx={{ p: 0.5 }}
			color={align === dir ? 'primary' : 'default'}
			// preventDefault keeps the editor focused; we update attrs directly.
			onMouseDown={e => { e.preventDefault(); updateAttributes({ align: dir }) }}
		>
			<Icon sx={{ fontSize: 16 }} />
		</IconButton>
	)

	// ── render ────────────────────────────────────────────────────────────────

	return (
		<NodeViewWrapper style={{ display: 'block' }}>

			{/* ── Control bar (selected only) ──────────────────────────────── */}
			{selected && (
				<Box sx={{ display: 'flex', justifyContent: 'center', mb: 0.75, userSelect: 'none' }}>
					<Paper
						elevation={3}
						sx={{ display: 'flex', alignItems: 'center', px: 0.75, py: 0.25, borderRadius: 1.5, gap: 0.25 }}
					>
						{/* Alignment */}
						{alignBtn('left',   FormatAlignLeftIcon)}
						{alignBtn('center', FormatAlignCenterIcon)}
						{alignBtn('right',  FormatAlignRightIcon)}

						<Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

						{/* Width */}
						<Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>
							W
						</Typography>
						<input
							type="number"
							min={50}
							max={2000}
							value={widthInput}
							placeholder="auto"
							// stopPropagation prevents TipTap from intercepting keyboard events
							// typed into this input (e.g. Delete triggering node deletion).
							onMouseDown={e => e.stopPropagation()}
							onKeyDown={e => { e.stopPropagation(); handleWidthKeyDown(e) }}
							onChange={e => setWidthInput(e.target.value)}
							onBlur={commitWidth}
							style={{
								width:         56,
								fontSize:      '0.75rem',
								border:        'none',
								outline:       'none',
								background:    'transparent',
								textAlign:     'right',
								padding:       '0 2px',
								// Hide the browser spinner arrows; they take up too much space.
								MozAppearance: 'textfield',
							}}
						/>
						<Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>
							px
						</Typography>
					</Paper>
				</Box>
			)}

			{/* ── Image row ────────────────────────────────────────────────── */}
			<Box sx={{ display: 'flex', justifyContent, my: '0.75em' }}>

				{/* Inner wrapper: position: relative so the resize handle is anchored,
				    lineHeight: 0 so there's no gap below the img baseline. */}
				<Box sx={{ position: 'relative', display: 'inline-block', lineHeight: 0 }}>

					<Box
						component="img"
						ref={imgRef}
						src={src}
						alt={alt || ''}
						title={title || undefined}
						sx={{
							display:       'block',
							width:         width ? `${width}px` : 'auto',
							maxWidth:      '100%',
							height:        'auto',
							borderRadius:  '4px',
							// Selection outline drawn here rather than via
							// ProseMirror-selectednode so we control colour and offset.
							outline:       selected ? '3px solid' : 'none',
							outlineColor:  'primary.main',
							outlineOffset: '2px',
						}}
					/>

					{/* ── Drag handle (selected only) ──────────────────────── */}
					{selected && (
						<Box
							sx={{
								position:    'absolute',
								bottom:      -5,
								right:       -5,
								width:       12,
								height:      12,
								bgcolor:     'primary.main',
								border:      '2px solid',
								borderColor: 'background.paper',
								borderRadius:'2px',
								cursor:      'se-resize',
								zIndex:      1,
							}}
							onMouseDown={handleResizeStart}
						/>
					)}
				</Box>
			</Box>
		</NodeViewWrapper>
	)
}

// ── Extension ─────────────────────────────────────────────────────────────────

/**
 * ResizableImage
 *
 * Extends TipTap's built-in Image extension with:
 *   width  — stored as the <img width="N"> attribute (px integer, null = auto)
 *   align  — stored as data-align="left|center|right"
 *
 * Uses the same node name ('image') so all existing stored content (plain
 * <img src="..."> tags) is parsed correctly without migration.
 *
 * allowBase64: true is inherited via .configure() so base64 data URLs are
 * accepted in the src attribute (required for our embedding strategy).
 */
export const ResizableImage = Image.extend({
	name: 'image', // Must match the built-in name for backward compatibility.

	addAttributes() {
		return {
			// Inherit src, alt, title from the parent Image extension.
			...this.parent?.(),

			width: {
				default: null,
				parseHTML: el => {
					const w = el.getAttribute('width')
					return w ? parseInt(w, 10) : null
				},
				renderHTML: attrs =>
					attrs.width ? { width: String(attrs.width) } : {},
			},

			align: {
				default: 'center',
				parseHTML: el => el.getAttribute('data-align') || 'center',
				renderHTML: attrs => ({ 'data-align': attrs.align || 'center' }),
			},
		}
	},

	addNodeView() {
		return ReactNodeViewRenderer(ResizableImageView)
	},

// allowBase64 permits data: URLs in src — required for the base64 embedding
// strategy.  inline: false keeps images as block nodes (own line).
}).configure({ inline: false, allowBase64: true })
