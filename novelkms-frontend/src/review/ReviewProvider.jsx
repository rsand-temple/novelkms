import { useCallback, useMemo, useState } from 'react'
import { ReviewContext } from './ReviewContext'

/**
 * ReviewProvider — owns the editor "Review Mode" rail state.
 *
 * Review Mode is a layer on top of the normal chapter editing surface: the
 * manuscript stays primary and fully editable, and a collapsible rail on the
 * right edge of the editor area shows the selected chapter's AI review.
 *
 * The rail is always bound to whatever manuscript chapter is currently
 * selected — EditorPanel decides whether to render it (it only appears for a
 * manuscript chapter, never for codex entries or non-chapter selections). The
 * per-chapter state (which past review is shown, dialog state, etc.) lives in
 * the rail itself and is reset by remounting it with key={chapterId}.
 *
 * This provider therefore only tracks whether the rail is open and whether it
 * is collapsed to its thin strip.
 */
export function ReviewProvider({ children }) {
	const [open, setOpen] = useState(false)
	const [collapsed, setCollapsed] = useState(false)

	// Enter review mode for the currently selected chapter. Always expands the
	// rail so a fresh "Run Review" is one click away.
	const openReview = useCallback(() => {
		setOpen(true)
		setCollapsed(false)
	}, [])

	const closeReview = useCallback(() => {
		setOpen(false)
	}, [])

	const collapse = useCallback(() => setCollapsed(true), [])
	const expand = useCallback(() => setCollapsed(false), [])
	const toggleCollapsed = useCallback(() => setCollapsed(c => !c), [])

	const value = useMemo(() => ({
		open,
		collapsed,
		openReview,
		closeReview,
		collapse,
		expand,
		toggleCollapsed,
	}), [open, collapsed, openReview, closeReview, collapse, expand, toggleCollapsed])

	return <ReviewContext.Provider value={value}>{children}</ReviewContext.Provider>
}
