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

export const priorityChipStyles = (priority) => {
	switch ((priority || '').toUpperCase()) {
		case 'HIGH':
			return {
				color: '#7A2E24',
				backgroundColor: '#F3E3DE',
				borderColor: '#C99A90',
			}

		case 'MEDIUM':
			return {
				color: '#7A5B16',
				backgroundColor: '#F6EED7',
				borderColor: '#D6BE7A',
			}

		case 'LOW':
			return {
				color: '#304C63',
				backgroundColor: '#E8EDF1',
				borderColor: '#A9B8C5',
			}

		default:
			return {
				color: '#3E4652',
				backgroundColor: '#EEE9DF',
				borderColor: '#D7CDBD',
			}
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

// ── Review scope / origin ────────────────────────────────────────────────────
// A chapter review and a scene review are the same artifact differing only in
// scope. The backend derives and returns review.scope ('CHAPTER' | 'SCENE' |
// 'BOOK'); these helpers tolerate older payloads that predate the field.

export function reviewScope(review) {
	if (!review) return 'CHAPTER'
	if (review.scope) return review.scope
	if (review.sceneId) return 'SCENE'
	if (!review.chapterId) return 'BOOK'
	return 'CHAPTER'
}

// Human-readable origin of a review, e.g. "Chapter", "Scene 2", or
// "Scene 2: The Reckoning". `scenes` is the parent chapter's scene list (from
// useScenes) used to resolve a scene's number and title.
export function originLabel(review, scenes) {
	const scope = reviewScope(review)
	if (scope === 'BOOK') return 'Book'
	if (scope !== 'SCENE') return 'Chapter'

	const list = scenes ?? []
	const idx = list.findIndex(s => s.id === review?.sceneId)
	if (idx < 0) return 'Scene'

	const n = idx + 1
	const title = (list[idx]?.title ?? '').trim()
	return title ? `Scene ${n}: ${title}` : `Scene ${n}`
}
