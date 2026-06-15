import { Node, mergeAttributes } from '@tiptap/core'
import { TOKEN_LABELS } from '../utils/tokenUtils'

/**
 * TemplateToken — an atomic inline node representing a field placeholder in a
 * page template (cover / part), e.g. Title, Subtitle, Author Full Name.
 *
 * Serialized HTML: <span data-token="TITLE"></span>  (empty — the value is
 * resolved at render/export time against the book/project).
 *
 * `marks: '_'` lets inline marks (bold/italic/underline and the FontSize mark)
 * attach to the field, so a selected field can be styled like text. When a
 * FontSize mark wraps the token the serialized form becomes
 * <span style="font-size: …"><span data-token="TITLE"></span></span>, which
 * the preview/resolver still matches on the inner data-token span.
 *
 * In the editor it renders via a node view as a non-editable pill showing the
 * human label (class `nkms-token`, styled in EditorPanel). Because it is an
 * atom it is selected/deleted as a single unit and cannot be typed into.
 *
 * Inserted by the toolbar Insert-field menu:
 *   editor.chain().focus().insertTemplateToken({ token: 'TITLE' }).run()
 *
 * `priority: 1000` ensures `span[data-token]` is claimed by this node before
 * any generic span-matching mark (e.g. FontSize's `span[style*=font-size]`).
 */
export const TemplateToken = Node.create({
	name: 'templateToken',
	group: 'inline',
	inline: true,
	atom: true,
	selectable: true,
	marks: '_',
	priority: 1000,

	addAttributes() {
		return {
			token: {
				default: null,
				parseHTML: el => el.getAttribute('data-token'),
				renderHTML: attrs => (attrs.token ? { 'data-token': attrs.token } : {}),
			},
		}
	},

	parseHTML() {
		return [{ tag: 'span[data-token]' }]
	},

	renderHTML({ HTMLAttributes }) {
		// No content hole — the stored span is empty by design.
		return ['span', mergeAttributes(HTMLAttributes)]
	},

	renderText({ node }) {
		const t = node.attrs.token
		return `<${TOKEN_LABELS[t] || t || 'field'}>`
	},

	addNodeView() {
		return ({ node }) => {
			const dom = document.createElement('span')
			const token = node.attrs.token ?? ''
			dom.setAttribute('data-token', token)
			dom.setAttribute('contenteditable', 'false')
			dom.className = 'nkms-token'
			dom.textContent = TOKEN_LABELS[token] || token || 'field'
			return { dom }
		}
	},

	addCommands() {
		return {
			insertTemplateToken:
				(attrs) =>
					({ commands }) =>
						commands.insertContent({ type: this.name, attrs: attrs ?? {} }),
		}
	},
})
