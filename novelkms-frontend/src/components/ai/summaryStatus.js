// Helpers for chapter-summary state, shared by the book-summary dialog and the
// pre-generation coverage dialog. Values mirror the backend
// ChapterSummaryStatus.state. Unlike memory documents there is no
// OUT_OF_SEQUENCE state — chapter summaries are independent paragraphs.
export const SUMMARY_STATE = {
	OK: 'OK',
	MISSING: 'MISSING',
	STALE_CONTENT: 'STALE_CONTENT',
}

// Flagged = would weaken the book summary if consumed as-is (missing or drifted).
export function isFlagged(state) {
	return state === SUMMARY_STATE.MISSING
		|| state === SUMMARY_STATE.STALE_CONTENT
}

// MUI Chip color for a state.
export function stateColor(state) {
	switch (state) {
		case SUMMARY_STATE.OK:            return 'success'
		case SUMMARY_STATE.STALE_CONTENT: return 'warning'
		default:                          return 'default'
	}
}

// Short chip label.
export function stateLabel(state) {
	switch (state) {
		case SUMMARY_STATE.OK:            return 'Up to date'
		case SUMMARY_STATE.MISSING:       return 'No summary'
		case SUMMARY_STATE.STALE_CONTENT: return 'Stale'
		default:                          return state ?? ''
	}
}

// One-line explanation for tooltips / dialog rows.
export function stateExplanation(state) {
	switch (state) {
		case SUMMARY_STATE.MISSING:
			return 'No summary yet — this chapter will contribute nothing to the book summary.'
		case SUMMARY_STATE.STALE_CONTENT:
			return 'The chapter was edited after its summary was generated.'
		case SUMMARY_STATE.OK:
			return 'Summary is current.'
		default:
			return ''
	}
}

export function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

// Chapter rows that are missing or stale, in book order. `rows` is the book-wide
// aggregated list. Used to warn before generating the book summary.
export function flaggedChapters(rows) {
	if (!rows || rows.length === 0) return []
	return rows.filter(s => isFlagged(s.state))
}
