import { createContext, useContext, useCallback, useMemo, useRef, useState } from 'react'
import { getDefaultTopicId, hasTopic } from './helpRegistry'

/**
 * HelpProvider — single source of truth for the Help Center modal: whether it
 * is open, which topic is showing, and a back-history stack for in-content
 * navigation (#help: links and TOC clicks).
 *
 * Mounted once near the app root (see main.jsx) so any control anywhere —
 * AppBar, dialogs, toolbars — can call useHelp().open('topic.id') without
 * prop-drilling. Mirrors the SearchProvider / ReviewProvider pattern.
 */

const HelpContext = createContext(null)

export function HelpProvider({ children }) {
	const [open, setOpen] = useState(false)
	const [topicId, setTopicId] = useState(null)
	// History of topic ids visited within the current open session (for Back).
	const historyRef = useRef([])
	const [historyLen, setHistoryLen] = useState(0)

	const open_ = useCallback((id) => {
		const target = id && hasTopic(id) ? id : getDefaultTopicId()
		historyRef.current = []
		setHistoryLen(0)
		setTopicId(target)
		setOpen(true)
	}, [])

	const close = useCallback(() => {
		setOpen(false)
	}, [])

	// Navigate to a topic within the open modal, pushing the current onto history.
	const navigate = useCallback((id) => {
		if (!id || !hasTopic(id)) return
		setTopicId((current) => {
			if (current && current !== id) {
				historyRef.current.push(current)
				setHistoryLen(historyRef.current.length)
			}
			return id
		})
	}, [])

	const back = useCallback(() => {
		const prev = historyRef.current.pop()
		setHistoryLen(historyRef.current.length)
		if (prev) setTopicId(prev)
	}, [])

	const value = useMemo(
		() => ({
			isOpen: open,
			topicId,
			canGoBack: historyLen > 0,
			open: open_,
			openHelp: open_,
			close,
			navigate,
			back,
		}),
		[open, topicId, historyLen, open_, close, navigate, back],
	)

	return <HelpContext.Provider value={value}>{children}</HelpContext.Provider>
}

export function useHelp() {
	const ctx = useContext(HelpContext)
	if (!ctx) {
		// Soft fallback so a stray HelpButton outside the provider never crashes
		// the app; it simply no-ops. The validator and dev console catch misuse.
		return {
			isOpen: false,
			topicId: null,
			canGoBack: false,
			openHelp: () => {},
			open: () => {},
			close: () => {},
			navigate: () => {},
			back: () => {},
		}
	}
	return ctx
}
