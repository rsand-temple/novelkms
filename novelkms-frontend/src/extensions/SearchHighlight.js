import { Extension } from '@tiptap/core'
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

export const searchHighlightKey = new PluginKey('nkmsSearchHighlight')

function findMatches(doc, query, matchCase) {
	if (!query) return []
	const needle = matchCase ? query : query.toLocaleLowerCase()
	const matches = []
	doc.descendants((node, pos) => {
		if (!node.isText || !node.text) return
		const source = matchCase ? node.text : node.text.toLocaleLowerCase()
		let from = 0
		while (from <= source.length - needle.length) {
			const at = source.indexOf(needle, from)
			if (at < 0) break
			matches.push({ from: pos + at, to: pos + at + query.length })
			from = at + Math.max(query.length, 1)
		}
	})
	return matches
}

function buildDecorations(doc, matches, activeIndex) {
	return DecorationSet.create(doc, matches.map((match, index) =>
		Decoration.inline(match.from, match.to, {
			class: index === activeIndex ? 'nkms-search-match nkms-search-active' : 'nkms-search-match',
		})
	))
}

export const SearchHighlight = Extension.create({
	name: 'searchHighlight',

	addCommands() {
		return {
			setSearch: ({ query = '', matchCase = false, activeIndex = -1 }) => ({ tr, dispatch }) => {
				if (dispatch) tr.setMeta(searchHighlightKey, { type: 'set', query, matchCase, activeIndex })
				return true
			},
			setSearchActiveIndex: (activeIndex) => ({ tr, dispatch }) => {
				if (dispatch) tr.setMeta(searchHighlightKey, { type: 'active', activeIndex })
				return true
			},
			clearSearch: () => ({ tr, dispatch }) => {
				if (dispatch) tr.setMeta(searchHighlightKey, { type: 'set', query: '', matchCase: false, activeIndex: -1 })
				return true
			},
			replaceCurrentSearch: (replacement = '') => ({ state, dispatch }) => {
				const pluginState = searchHighlightKey.getState(state)
				const match = pluginState?.matches?.[pluginState.activeIndex]
				if (!match || !dispatch) return false
				dispatch(state.tr.insertText(replacement, match.from, match.to))
				return true
			},
			replaceAllSearch: (replacement = '') => ({ state, dispatch }) => {
				const pluginState = searchHighlightKey.getState(state)
				if (!pluginState?.matches?.length || !dispatch) return false
				let tr = state.tr
				for (let i = pluginState.matches.length - 1; i >= 0; i -= 1) {
					const match = pluginState.matches[i]
					tr = tr.insertText(replacement, match.from, match.to)
				}
				dispatch(tr)
				return true
			},
			scrollToSearchMatch: (index) => ({ state, dispatch }) => {
				const pluginState = searchHighlightKey.getState(state)
				const match = pluginState?.matches?.[index]
				if (!match || !dispatch) return false
				dispatch(state.tr.setSelection(TextSelection.create(state.doc, match.from, match.to)).scrollIntoView())
				return true
			},
		}
	},

	addProseMirrorPlugins() {
		return [new Plugin({
			key: searchHighlightKey,
			state: {
				init: (_, state) => ({
					query: '', matchCase: false, activeIndex: -1, matches: [],
					decorations: DecorationSet.empty,
				}),
				apply: (tr, previous, _oldState, newState) => {
					const meta = tr.getMeta(searchHighlightKey)
					let query = previous.query
					let matchCase = previous.matchCase
					let activeIndex = previous.activeIndex
					if (meta?.type === 'set') {
						query = meta.query
						matchCase = meta.matchCase
						activeIndex = meta.activeIndex
					} else if (meta?.type === 'active') {
						activeIndex = meta.activeIndex
					}
					const mustRebuild = tr.docChanged || meta?.type === 'set'
					const matches = mustRebuild ? findMatches(newState.doc, query, matchCase) : previous.matches
					if (!matches.length) activeIndex = -1
					else if (activeIndex < 0 || activeIndex >= matches.length) activeIndex = 0
					return { query, matchCase, activeIndex, matches, decorations: buildDecorations(newState.doc, matches, activeIndex) }
				},
			},
			props: {
				decorations(state) { return searchHighlightKey.getState(state)?.decorations ?? DecorationSet.empty },
			},
		})]
	},
})
