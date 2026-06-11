import Paragraph from '@tiptap/extension-paragraph'

/**
 * Replaces the default Paragraph node (StarterKit must have paragraph: false).
 *
 * Inline-style attributes stored directly on <p> tags (manual overrides):
 *   indent           → margin-left
 *   firstLineIndent  → text-indent  (null = inherit project default via CSS custom prop)
 *   fontFamily       → font-family  (null = inherit project default)
 *   fontSize         → font-size    (null = inherit project default)
 *   spacingBefore    → margin-top
 *   spacingAfter     → margin-bottom
 *
 * Plus a semantic style label:
 *   styleKey         → data-style="<key>"  (e.g. 'report', 'chapter_title')
 *
 * Cascade (lowest → highest priority):
 *   1. style definition  — CSS keyed on p[data-style="<key>"] (resolved per book/project)
 *   2. paragraph overrides — explicit inline styles on <p> (the attrs above)
 *   3. inline marks        — bold/italic/FontSize on a run of text
 *
 * data-style is a real attribute (so CSS can target it); the formatting attrs
 * use rendered:false and are emitted as inline style inside renderHTML.
 */
export const StyledParagraph = Paragraph.extend({
	name: 'paragraph',

	addAttributes() {
		return {
			indent:          { default: null, rendered: false },
			firstLineIndent: { default: null, rendered: false },
			fontFamily:      { default: null, rendered: false },
			fontSize:        { default: null, rendered: false },
			spacingBefore:   { default: null, rendered: false },
			spacingAfter:    { default: null, rendered: false },
			styleKey:        { default: null, rendered: false },
		}
	},

	renderHTML({ node, HTMLAttributes }) {
		const { indent, firstLineIndent, fontFamily, fontSize, spacingBefore, spacingAfter, styleKey } = node.attrs

		const ownStyles = [
			indent          && `margin-left: ${indent}`,
			firstLineIndent !== null && firstLineIndent !== undefined && `text-indent: ${firstLineIndent}`,
			fontFamily      && `font-family: ${fontFamily}`,
			fontSize        && `font-size: ${fontSize}`,
			spacingBefore   && `margin-top: ${spacingBefore}`,
			spacingAfter    && `margin-bottom: ${spacingAfter}`,
		].filter(Boolean).join('; ')

		// HTMLAttributes may contain style: 'text-align: center' injected by TextAlign extension.
		const existingStyle = HTMLAttributes.style ? String(HTMLAttributes.style) : ''
		const finalStyle = [existingStyle, ownStyles].filter(Boolean).join('; ')

		const attrs = { ...HTMLAttributes }
		if (finalStyle) {
			attrs.style = finalStyle
		} else {
			delete attrs.style
		}
		if (styleKey) {
			attrs['data-style'] = styleKey
		} else {
			delete attrs['data-style']
		}

		return ['p', attrs, 0]
	},

	parseHTML() {
		return [
			{
				tag: 'p',
				getAttrs(el) {
					const s = el.style
					return {
						indent:          s.marginLeft   || null,
						firstLineIndent: s.textIndent   || null,
						fontFamily:      s.fontFamily   || null,
						fontSize:        s.fontSize     || null,
						spacingBefore:   s.marginTop    || null,
						spacingAfter:    s.marginBottom || null,
						styleKey:        el.getAttribute('data-style') || null,
					}
				},
			},
		]
	},
})
