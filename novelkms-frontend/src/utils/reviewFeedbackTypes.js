// Requested feedback categories for a human-review request.
//
// The KEY (stable UPPER_SNAKE) is what the backend stores in
// review_request.feedback_types; the label is presentation only. Keeping the two
// apart means a wording change is never a data migration — the stored keys never
// move, so relabeling here is safe on already-published requests.
export const FEEDBACK_TYPES = [
	{ key: 'GENERAL_REACTION', label: 'General reader reaction' },
	{ key: 'PACING',           label: 'Pacing' },
	{ key: 'CHARACTER',        label: 'Character' },
	{ key: 'DIALOGUE',         label: 'Dialogue' },
	{ key: 'PLOT_CLARITY',     label: 'Plot clarity' },
	{ key: 'CONTINUITY',       label: 'Continuity' },
	{ key: 'WORLDBUILDING',    label: 'Worldbuilding' },
	{ key: 'TONE',             label: 'Tone' },
	{ key: 'PROSE',            label: 'Line-level prose' },
	{ key: 'GRAMMAR',          label: 'Grammar & mechanics' },
	{ key: 'DEVELOPMENTAL',    label: 'Developmental feedback' },
	{ key: 'BETA_READ',        label: 'Beta reading' },
]

const LABEL_BY_KEY = Object.fromEntries(FEEDBACK_TYPES.map(t => [t.key, t.label]))

/**
 * Label for a stored key. Unknown keys (e.g. an older key retired from the list)
 * fall back to a humanized form rather than rendering nothing, so a request never
 * shows a blank chip.
 */
export function feedbackTypeLabel(key) {
	if (!key) return ''
	return LABEL_BY_KEY[key] ?? key.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}
