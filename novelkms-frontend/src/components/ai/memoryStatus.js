// Helpers for chapter memory-document state, shared by the rail Memory tab and
// the pre-review dialog. Values mirror the backend ChapterMemoryStatus.state.
export const MEMORY_STATE = {
	OK: 'OK',
	MISSING: 'MISSING',
	STALE_CONTENT: 'STALE_CONTENT',
	OUT_OF_SEQUENCE: 'OUT_OF_SEQUENCE',
}

export function isFlagged(state) {
	return state === MEMORY_STATE.MISSING
		|| state === MEMORY_STATE.STALE_CONTENT
		|| state === MEMORY_STATE.OUT_OF_SEQUENCE
}

// MUI Chip color for a state.
export function stateColor(state) {
	switch (state) {
		case MEMORY_STATE.OK:              return 'success'
		case MEMORY_STATE.STALE_CONTENT:   return 'warning'
		case MEMORY_STATE.OUT_OF_SEQUENCE: return 'warning'
		default:                           return 'default'
	}
}

// Short chip label.
export function stateLabel(state) {
	switch (state) {
		case MEMORY_STATE.OK:              return 'Up to date'
		case MEMORY_STATE.MISSING:         return 'No memory'
		case MEMORY_STATE.STALE_CONTENT:   return 'Stale'
		case MEMORY_STATE.OUT_OF_SEQUENCE: return 'Out of sequence'
		default:                           return state ?? ''
	}
}

// One-line explanation for tooltips / dialog rows.
export function stateExplanation(state) {
	switch (state) {
		case MEMORY_STATE.MISSING:
			return 'No memory document yet — this chapter will contribute no continuity context.'
		case MEMORY_STATE.STALE_CONTENT:
			return 'The chapter was edited after its memory document was generated.'
		case MEMORY_STATE.OUT_OF_SEQUENCE:
			return 'An earlier chapter has a newer memory document — this one may be behind.'
		case MEMORY_STATE.OK:
			return 'Memory document is current.'
		default:
			return ''
	}
}

export function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

// Flagged chapters that PRECEDE the given chapter in book order. `rows` is the
// book-wide status list (already in linear order); preceding = everything before
// the target's position. Used to gate a chapter review and chapter-memory
// generation when earlier memory documents are missing or behind.
export function flaggedPreceding(rows, chapterId) {
	if (!rows || rows.length === 0) return []
	const idx = rows.findIndex(s => s.chapterId === chapterId)
	if (idx <= 0) return []
	return rows.slice(0, idx).filter(s => isFlagged(s.state))
}
