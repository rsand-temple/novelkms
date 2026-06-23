import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

export const reviewHighlightKey = new PluginKey('nkmsReviewHighlight')

/**
 * Walks text nodes inside each textblock and concatenates them so a search
 * query that spans inline-mark boundaries (bold/italic splits) is still found.
 * Returns the first match as { from, to } in document coordinates, or null.
 */
function findFirstMatch(doc, text) {
	if (!text) return null
	const needle = text.toLocaleLowerCase()
	let result = null

	doc.descendants((node, pos) => {
		if (result) return false
		if (!node.isTextblock) return // descend into blocks but skip atoms
		// Collect all text segments within this textblock.
		let fullText = ''
		const segments = [] // { start: docPos, length }
		node.forEach((child, offset) => {
			if (child.isText && child.text) {
				segments.push({ start: pos + 1 + offset, length: child.text.length })
				fullText += child.text
			}
		})
		const source = fullText.toLocaleLowerCase()
		const at = source.indexOf(needle)
		if (at >= 0) {
			const from = offsetToDocPos(segments, at)
			const to = offsetToDocPos(segments, at + text.length)
			if (from !== null && to !== null) result = { from, to }
		}
		return false // already processed children
	})
	return result
}

function offsetToDocPos(segments, offset) {
	let accumulated = 0
	for (const seg of segments) {
		if (offset <= accumulated + seg.length) {
			return seg.start + (offset - accumulated)
		}
		accumulated += seg.length
	}
	return null
}

/**
 * ReviewHighlight — a TipTap extension that applies a single transient
 * decoration when the user clicks a recommendation card. The highlight auto-
 * clears after 3 seconds. It is entirely separate from SearchHighlight so the
 * two can coexist and have independent lifecycles.
 *
 * Commands:
 *   highlightAnchor(text)   — find the first match and highlight + scroll.
 *   clearReviewHighlight()  — remove the highlight immediately.
 */
export const ReviewHighlight = Extension.create({
	name: 'reviewHighlight',

	addCommands() {
		return {
			highlightAnchor: (text) => ({ tr, dispatch, view }) => {
				if (dispatch) {
					tr.setMeta(reviewHighlightKey, { type: 'highlight', text })
				}
				// Scroll to the decoration after it renders.
				if (view) {
					setTimeout(() => {
						const el = view.dom.querySelector('.nkms-review-highlight')
						if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
					}, 50)
				}
				return true
			},
			clearReviewHighlight: () => ({ tr, dispatch }) => {
				if (dispatch) tr.setMeta(reviewHighlightKey, { type: 'clear' })
				return true
			},
		}
	},

	addProseMirrorPlugins() {
		let clearTimer = null
		return [new Plugin({
			key: reviewHighlightKey,
			state: {
				init: () => ({
					text: '', match: null, decorations: DecorationSet.empty,
				}),
				apply: (tr, previous, _oldState, newState) => {
					const meta = tr.getMeta(reviewHighlightKey)
					if (meta?.type === 'clear') {
						return { text: '', match: null, decorations: DecorationSet.empty }
					}
					if (meta?.type === 'highlight') {
						const match = findFirstMatch(newState.doc, meta.text)
						if (!match) {
							return { text: meta.text, match: null, decorations: DecorationSet.empty }
						}
						const decorations = DecorationSet.create(newState.doc, [
							Decoration.inline(match.from, match.to, {
								class: 'nkms-review-highlight',
							}),
						])
						return { text: meta.text, match, decorations }
					}
					// On doc changes remap existing decorations.
					if (tr.docChanged && previous.match) {
						return {
							...previous,
							decorations: previous.decorations.map(tr.mapping, tr.doc),
						}
					}
					return previous
				},
			},
			props: {
				decorations(state) {
					return reviewHighlightKey.getState(state)?.decorations ?? DecorationSet.empty
				},
			},
			view() {
				return {
					update: (view, prevState) => {
						const prev = reviewHighlightKey.getState(prevState)
						const curr = reviewHighlightKey.getState(view.state)
						// Start auto-clear timer only when a new highlight appears.
						if (curr?.match && (!prev?.match || prev.text !== curr.text)) {
							if (clearTimer !== null) clearTimeout(clearTimer)
							clearTimer = setTimeout(() => {
								clearTimer = null
								view.dispatch(
									view.state.tr.setMeta(reviewHighlightKey, { type: 'clear' }),
								)
							}, 3000)
						}
					},
					destroy: () => {
						if (clearTimer !== null) {
							clearTimeout(clearTimer)
							clearTimer = null
						}
					},
				}
			},
		})]
	},
})
