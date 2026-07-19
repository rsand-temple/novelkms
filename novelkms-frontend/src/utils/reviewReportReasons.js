// Reasons a user can attach to a content report (slice 1F).
//
// As with feedback types (reviewFeedbackTypes.js), the KEY is the stable
// UPPER_SNAKE value the backend stores in content_report.reason; the label is
// presentation only. Keeping them apart means relabeling is never a data
// migration — already-filed reports keep their keys, and this list can be reworded
// freely. The key set is fixed by the backend: SPAM, HARASSMENT, COPYRIGHT, HATE,
// EXPLICIT, OTHER.
export const REPORT_REASONS = [
	{ key: 'SPAM',       label: 'Spam' },
	{ key: 'HARASSMENT', label: 'Harassment' },
	{ key: 'COPYRIGHT',  label: 'Copyright infringement' },
	{ key: 'HATE',       label: 'Hate speech' },
	{ key: 'EXPLICIT',   label: 'Explicit content' },
	{ key: 'OTHER',      label: 'Something else' },
]

const LABEL_BY_KEY = Object.fromEntries(REPORT_REASONS.map(r => [r.key, r.label]))

/**
 * Label for a stored reason key. Unknown keys fall back to a humanized form rather
 * than rendering nothing, so a moderation row never shows a blank reason.
 */
export function reportReasonLabel(key) {
	if (!key) return ''
	return LABEL_BY_KEY[key] ?? key.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}
