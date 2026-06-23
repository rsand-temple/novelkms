// Shared helpers for rendering AI review recommendations. Pure JS (no JSX) so
// it can be imported by both the rail card and any other recommendation view
// without tripping Vite Fast Refresh's "only export components" rule.

export const CATEGORY_LABELS = {
	CHARACTER: 'Characters',
	VOICE: 'Voices',
	PLOT: 'Plot',
	WORLD: 'World',
	TIMELINE: 'Timeline',
	CANON: 'Canon',
	NOTES: 'Notes',
}

export const CATEGORY_OPTIONS = [
	{ key: 'CHARACTER', label: 'Characters' },
	{ key: 'VOICE', label: 'Voices' },
	{ key: 'PLOT', label: 'Plot' },
	{ key: 'WORLD', label: 'World' },
	{ key: 'TIMELINE', label: 'Timeline' },
	{ key: 'CANON', label: 'Canon' },
	{ key: 'NOTES', label: 'Notes' },
]

// Statuses that should never appear in the active working list.
export const HIDDEN_STATUSES = new Set(['DELETED', 'PROMOTED'])

export function severityColor(severity) {
	switch ((severity ?? '').toUpperCase()) {
		case 'HIGH': return 'error'
		case 'MEDIUM': return 'warning'
		case 'LOW': return 'info'
		default: return 'default'
	}
}

export function normalizeCategory(value) {
	const raw = (value ?? '').trim()
	if (!raw) return 'NOTES'

	const upper = raw.toUpperCase()
	if (CATEGORY_LABELS[upper]) return upper

	const compact = upper.replace(/[^A-Z]/g, '')

	switch (compact) {
		case 'CHARACTER':
		case 'CHARACTERS':
			return 'CHARACTER'
		case 'VOICE':
		case 'VOICES':
			return 'VOICE'
		case 'PLOT':
			return 'PLOT'
		case 'WORLD':
			return 'WORLD'
		case 'TIMELINE':
			return 'TIMELINE'
		case 'CANON':
			return 'CANON'
		case 'NOTE':
		case 'NOTES':
		case 'GENERALNOTE':
		case 'GENERALNOTES':
			return 'NOTES'
		default:
			return 'NOTES'
	}
}

export function codexLabel(key) {
	return CATEGORY_LABELS[normalizeCategory(key)] ?? 'Notes'
}

export function defaultTitle(rec) {
	const title = (rec.codexTitle ?? '').trim()
	if (title) return title

	const text = (rec.recommendation ?? '').trim()
	if (!text) return 'Untitled'
	return text.length <= 80 ? text : text.slice(0, 80).trim()
}

// A short clipboard-friendly rendering of a recommendation ("Copy note").
export function recommendationToText(rec) {
	const lines = []
	const meta = []
	if (rec.category) meta.push(rec.category)
	if (rec.severity) meta.push(rec.severity)
	if (meta.length) lines.push(`[${meta.join(' · ')}]`)
	if (rec.recommendation) lines.push(rec.recommendation.trim())
	if (rec.location) lines.push(`— ${rec.location.trim()}`)
	return lines.join('\n')
}
