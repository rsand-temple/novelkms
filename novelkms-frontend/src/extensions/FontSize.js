import { Mark, mergeAttributes } from '@tiptap/core'

/**
 * Inline mark for per-word/phrase font size overrides.
 * Renders as <span style="font-size: {size}">.
 *
 * Commands:
 *   editor.chain().setFontSize('1.2rem').run()
 *   editor.chain().unsetFontSize().run()
 *
 * This mark is the "bottom" of the cascade — it wins over both project
 * defaults and paragraph-level StyledParagraph fontSize attributes.
 */
export const FontSize = Mark.create({
	name: 'fontSize',

	addAttributes() {
		return {
			size: {
				default: null,
				parseHTML: el => el.style.fontSize || null,
				renderHTML: attrs =>
					attrs.size ? { style: `font-size: ${attrs.size}` } : {},
			},
		}
	},

	parseHTML() {
		return [{ tag: 'span[style*="font-size"]' }]
	},

	renderHTML({ HTMLAttributes }) {
		return ['span', mergeAttributes(HTMLAttributes), 0]
	},

	addCommands() {
		return {
			setFontSize:
				size =>
				({ commands }) =>
					commands.setMark(this.name, { size }),

			unsetFontSize:
				() =>
				({ commands }) =>
					commands.unsetMark(this.name),
		}
	},
})
