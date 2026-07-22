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

// ── Recommendation lifecycle ────────────────────────────────────────────────
// Bug-tracker style: the author's goal is to clear the Active queue.
//
//   OPEN       new / undecided
//   DEFERRED   valid, but not now — parked in its own Deferred tab so Active
//              stays clear; unfinished by definition
//   DONE       acted on, or the manuscript now addresses it
//   DISMISSED  disagree / false positive / stylistic / not applicable
//   PROMOTED   converted into a Codex entry (inert; auditable)
//   DELETED    legacy / admin cleanup only; never set from the finding UI
//
// Tabs:  Active = OPEN    Deferred = DEFERRED    Resolved = DONE + DISMISSED + PROMOTED

export const STATUS = {
	OPEN: 'OPEN',
	DEFERRED: 'DEFERRED',
	DONE: 'DONE',
	DISMISSED: 'DISMISSED',
	PROMOTED: 'PROMOTED',
	DELETED: 'DELETED',
}

export const ACTIVE_STATUSES   = new Set([STATUS.OPEN])
export const DEFERRED_STATUSES = new Set([STATUS.DEFERRED])
export const RESOLVED_STATUSES = new Set([STATUS.DONE, STATUS.DISMISSED, STATUS.PROMOTED])

// Statuses that should never appear in any working tab (History/admin only).
export const HIDDEN_STATUSES = new Set([STATUS.DELETED])

export function normalizeStatus(value) {
	const s = (value ?? '').toUpperCase().trim()
	return STATUS[s] ? s : STATUS.OPEN
}

export function isActiveStatus(value) {
	return ACTIVE_STATUSES.has(normalizeStatus(value))
}

export function isDeferredStatus(value) {
	return DEFERRED_STATUSES.has(normalizeStatus(value))
}

export function isResolvedStatus(value) {
	return RESOLVED_STATUSES.has(normalizeStatus(value))
}

// Short label + chip color for a finding's current status.
export const STATUS_META = {
	OPEN: { label: 'Open', color: 'default' },
	DEFERRED: { label: 'Deferred', color: 'secondary' },
	DONE: { label: 'Done', color: 'success' },
	DISMISSED: { label: 'Dismissed', color: 'default' },
	PROMOTED: { label: 'Promoted', color: 'info' },
	DELETED: { label: 'Deleted', color: 'default' },
}

export function statusMeta(value) {
	return STATUS_META[normalizeStatus(value)] ?? STATUS_META.OPEN
}

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

// ── Promotion target picker (E8) ─────────────────────────────────────────────
// Promotion targets a project Codex Type. When the project already has a codex,
// the author picks a real Type by id (so any project Type is reachable,
// including author-created ones with no system key). When the project has no
// codex yet, we fall back to the seven broad seed categories keyed by system
// key; the backend seeds the codex and maps the key to the matching seeded Type.

// Build the dropdown options from the project's Codex types (each { id, title,
// codexCategory }). Empty/absent list → the broad seed-category fallback.
export function buildPromoteOptions(codexTypes) {
	const types = Array.isArray(codexTypes) ? codexTypes : []
	if (types.length > 0) {
		return types.map(t => ({
			kind: 'type',
			value: t.id,
			label: (t.title ?? '').trim() || 'Untitled type',
			systemKey: (t.codexCategory ?? null),
		}))
	}
	return CATEGORY_OPTIONS.map(o => ({
		kind: 'category',
		value: o.key,
		label: o.label,
		systemKey: o.key,
	}))
}

// Initial selection for a recommendation: the option whose system key matches
// the AI's broad category, else the first option. Returns an option value
// (a Type id, or a category key in fallback mode).
export function defaultPromoteValue(options, aiCategory) {
	if (!options || options.length === 0) return ''
	const want = normalizeCategory(aiCategory)
	const match = options.find(o => (o.systemKey ?? '').toUpperCase() === want)
	return (match ?? options[0]).value
}

// Resolve a chosen option value into the promote request payload. A real Type
// sends codexTypeId; a fallback seed category sends codexCategory. The backend
// prefers codexTypeId when present.
export function promoteTarget(options, value) {
	const opt = (options ?? []).find(o => o.value === value)
	if (!opt) return { codexTypeId: null, codexCategory: null }
	return opt.kind === 'type'
		? { codexTypeId: opt.value, codexCategory: null }
		: { codexTypeId: null, codexCategory: opt.value }
}
