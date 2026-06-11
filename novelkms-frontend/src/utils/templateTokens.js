// Template field tokens — catalog, labels, sample values, resolution, and
// preview rendering.
//
// A token is stored in template HTML as <span data-token="TITLE"></span> (an
// atomic inline node; see src/extensions/TemplateToken.js). At render/export
// time the span is replaced by the resolved value for the book/project.

export const TOKEN_LABELS = {
	TITLE:            'Title',
	SUBTITLE:         'Subtitle',
	SHORT_TITLE:      'Short Title',
	AUTHOR_FULL_NAME: 'Author Full Name',
	AUTHOR_LAST_NAME: 'Author Last Name',
	COPYRIGHT:        'Copyright',
	PART_NUMBER:      'Part Number',
	PART_TITLE:       'Part Title',
	PART_SUBTITLE:    'Part Subtitle',
}

// Tokens offered in the Insert-field menu, per template type.
export const TOKENS_BY_TYPE = {
	cover: ['TITLE', 'SUBTITLE', 'SHORT_TITLE', 'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME', 'COPYRIGHT'],
	part:  ['PART_NUMBER', 'PART_TITLE', 'PART_SUBTITLE', 'TITLE', 'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME', 'COPYRIGHT'],
}

/** [{ token, label }] for the Insert-field menu, given a template type. */
export function tokensForType(type) {
	const list = TOKENS_BY_TYPE[String(type || '').toLowerCase()] || []
	return list.map(token => ({ token, label: TOKEN_LABELS[token] || token }))
}

// Sample values for global-scope preview (nothing to bind to) and for part
// tokens in a book-scope preview (a part template applies to every part).
export const SAMPLE_VALUES = {
	TITLE:            'The Alone Man',
	SUBTITLE:         'A Novel',
	SHORT_TITLE:      'Alone Man',
	AUTHOR_FULL_NAME: 'Richard Sand',
	AUTHOR_LAST_NAME: 'Sand',
	COPYRIGHT:        '© 2026 Richard Sand',
	PART_NUMBER:      'One',
	PART_TITLE:       'The Gathering Storm',
	PART_SUBTITLE:    'In which it begins',
}

// Real values from book/project; null where unavailable. Part tokens have no
// single value at book/global scope (a part template spans all parts).
function realValues(book, project) {
	const full = [project?.authorFirstName, project?.authorLastName].filter(Boolean).join(' ')
	return {
		TITLE:            book?.title      || null,
		SUBTITLE:         book?.subtitle   || null,
		SHORT_TITLE:      book?.shortTitle || null,
		AUTHOR_FULL_NAME: full             || null,
		AUTHOR_LAST_NAME: project?.authorLastName || null,
		COPYRIGHT:        project?.copyright      || null,
		PART_NUMBER:      null,
		PART_TITLE:       null,
		PART_SUBTITLE:    null,
	}
}

/**
 * Returns a { token: displayString } map for preview.
 *   scope 'book'  — real values where available, otherwise sample.
 *   scope 'global'— all sample.
 */
export function resolveValues({ scope, book, project }) {
	const real = scope === 'book' ? realValues(book, project) : {}
	const out = {}
	Object.keys(TOKEN_LABELS).forEach(token => {
		out[token] = real[token] || SAMPLE_VALUES[token] || ''
	})
	return out
}

function escapeHtml(s) {
	return String(s ?? '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
}

/**
 * Replaces every <span data-token="X"></span> in template HTML with the
 * resolved value, for preview. All other markup is left untouched.
 */
export function renderPreviewHtml(html, values) {
	if (!html) return ''
	return html.replace(
		/<span[^>]*\bdata-token="([^"]+)"[^>]*>\s*<\/span>/gi,
		(_match, token) => escapeHtml(values?.[token] ?? '')
	)
}
