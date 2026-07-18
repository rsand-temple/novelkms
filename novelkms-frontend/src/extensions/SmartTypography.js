import { Extension, textInputRule } from '@tiptap/core'

/**
 * Word-style autocorrect: typing two hyphens converts them to an em dash.
 *
 * "word--word" -> "word—word"
 *
 * Fires live as the second hyphen is typed. Does not affect pasted or
 * imported content (DOCX import, etc.) — only live keystrokes in the
 * TipTap editor.
 */
export const SmartTypography = Extension.create({
	name: 'smartTypography',

	addInputRules() {
		return [
			textInputRule({
				find: /--$/,
				replace: '—',
			}),
		]
	},
})
