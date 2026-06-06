import Paragraph from '@tiptap/extension-paragraph'

/**
 * Replaces the default Paragraph node (StarterKit must have paragraph: false).
 *
 * Adds six optional inline-style attributes that are stored directly on <p> tags:
 *   indent           → margin-left
 *   firstLineIndent  → text-indent  (null = inherit project default via CSS custom prop)
 *   fontFamily       → font-family  (null = inherit project default)
 *   fontSize         → font-size    (null = inherit project default)
 *   spacingBefore    → margin-top
 *   spacingAfter     → margin-bottom
 *
 * Cascade: CSS custom properties on the editor container set project defaults.
 *          Explicit inline styles on <p> override them at paragraph level.
 *          Bold/italic/FontSize marks override at the word level.
 *
 * All attributes use rendered: false so TipTap does NOT emit data-* attributes.
 * Rendering is handled entirely inside renderHTML.
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
		}
	},

	renderHTML({ node, HTMLAttributes }) {
		const { indent, firstLineIndent, fontFamily, fontSize, spacingBefore, spacingAfter } = node.attrs

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
					}
				},
			},
		]
	},
})
