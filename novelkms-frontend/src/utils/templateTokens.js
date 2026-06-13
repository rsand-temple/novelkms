// Template field tokens — catalog, labels, sample values, resolution, and
// preview rendering.
//
// A token is stored in template HTML as <span data-token="TITLE"></span> (an
// atomic inline node; see src/extensions/TemplateToken.js). At render/export
// time the span is replaced by the resolved value for the book/project/part.

export const TOKEN_LABELS = {
	TITLE: 'Title',
	SUBTITLE: 'Subtitle',
	SHORT_TITLE: 'Short Title',
	AUTHOR_FULL_NAME: 'Author Full Name',
	AUTHOR_LAST_NAME: 'Author Last Name',
	COPYRIGHT: 'Copyright',
	PART_NUMBER: 'Part Number',
	PART_TITLE: 'Part Title',
	PART_SUBTITLE: 'Part Subtitle',
}

// Tokens offered in the Insert-field menu, per template type.
export const TOKENS_BY_TYPE = {
	cover: ['TITLE', 'SUBTITLE', 'SHORT_TITLE', 'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME', 'COPYRIGHT'],
	part: ['PART_NUMBER', 'PART_TITLE', 'PART_SUBTITLE', 'TITLE', 'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME', 'COPYRIGHT'],
}

/** [{ token, label }] for the Insert-field menu, given a template type. */
export function tokensForType(type) {
	const list = TOKENS_BY_TYPE[String(type || '').toLowerCase()] || []
	return list.map(token => ({ token, label: TOKEN_LABELS[token] || token }))
}

// Sample values — used in the template editor's preview toggle so the author
// can see where each token will render without needing real data.
export const SAMPLE_VALUES = {
	TITLE: 'The Alone Man',
	SUBTITLE: 'A Novel',
	SHORT_TITLE: 'Alone Man',
	AUTHOR_FULL_NAME: 'Richard Sand',
	AUTHOR_LAST_NAME: 'Sand',
	COPYRIGHT: '© 2026 Richard Sand',
	PART_NUMBER: 'II',
	PART_TITLE: 'The Gathering Storm',
	PART_SUBTITLE: 'In which it begins',
}

// ── Roman numeral conversion ──────────────────────────────────────────────────

const ROMAN_VALUES = [1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1]
const ROMAN_SYMBOLS = ['M', 'CM', 'D', 'CD', 'C', 'XC', 'L', 'XL', 'X', 'IX', 'V', 'IV', 'I']

function toRoman(n) {
	if (!n || n < 1) return ''
	let result = ''
	let remaining = n
	for (let i = 0; i < ROMAN_VALUES.length; i++) {
		while (remaining >= ROMAN_VALUES[i]) {
			result += ROMAN_SYMBOLS[i]
			remaining -= ROMAN_VALUES[i]
		}
	}
	return result
}

// ── Value resolution ──────────────────────────────────────────────────────────

function realValues(book, project) {
	const full = [project?.authorFirstName, project?.authorLastName].filter(Boolean).join(' ')
	return {
		TITLE: book?.title || null,
		SUBTITLE: book?.subtitle || null,
		SHORT_TITLE: book?.shortTitle || null,
		AUTHOR_FULL_NAME: full || null,
		AUTHOR_LAST_NAME: project?.authorLastName || null,
		COPYRIGHT: project?.copyright || null,
		PART_NUMBER: null,
		PART_TITLE: null,
		PART_SUBTITLE: null,
	}
}

/**
 * Returns a { token: displayString } map for template editor preview.
 *   scope 'book'   — real book/project values where available, sample otherwise.
 *   scope 'global' — all sample values.
 *
 * Part tokens (PART_NUMBER, PART_TITLE, PART_SUBTITLE) always use sample values
 * here because a single part template renders for every part; there is no single
 * "real" value to bind to. Use resolveValuesForPart() for real part rendering.
 */
export function resolveValues({ scope, book, project }) {
	const real = scope === 'book' ? realValues(book, project) : {}
	const out = {}
	Object.keys(TOKEN_LABELS).forEach(token => {
		out[token] = real[token] || SAMPLE_VALUES[token] || ''
	})
	return out
}

/**
 * Returns a { token: displayString } map resolved against a specific part.
 * Used by PartPagePreview to render the part template with real data.
 *
 * PART_NUMBER  — Roman numeral (I, II, III, …) derived from the part's 1-based
 *                ordinal position in the book.
 * PART_SUBTITLE — empty string when the part has no subtitle, so the token
 *                 renders blank on the page rather than showing sample text.
 *
 * @param {object} part        - Part record (title, subtitle, …)
 * @param {number} partNumber  - 1-based ordinal position within the book
 * @param {object} book        - Book record
 * @param {object} project     - Project record
 */
export function resolveValuesForPart({ part, partNumber, book, project }) {
	const full = [project?.authorFirstName, project?.authorLastName].filter(Boolean).join(' ')

	const out = {}
	Object.keys(TOKEN_LABELS).forEach(token => {
		switch (token) {
			case 'PART_NUMBER':
				// Roman numeral; falls back to sample only when ordinal is unknown.
				out[token] = partNumber ? toRoman(partNumber) : SAMPLE_VALUES.PART_NUMBER
				break
			case 'PART_TITLE':
				// Intentionally blank (not sample) when the part has no custom title —
				// the nav tree and editor show "Part I/II/III" from partNumber instead.
				out[token] = part?.title || ''
				break
			case 'PART_SUBTITLE':
				// Intentionally blank (not sample) when no subtitle is defined.
				out[token] = part?.subtitle || ''
				break
			case 'TITLE':
				out[token] = book?.title || SAMPLE_VALUES.TITLE
				break
			case 'SUBTITLE':
				out[token] = book?.subtitle || SAMPLE_VALUES.SUBTITLE
				break
			case 'SHORT_TITLE':
				out[token] = book?.shortTitle || SAMPLE_VALUES.SHORT_TITLE
				break
			case 'AUTHOR_FULL_NAME':
				out[token] = full || SAMPLE_VALUES.AUTHOR_FULL_NAME
				break
			case 'AUTHOR_LAST_NAME':
				out[token] = project?.authorLastName || SAMPLE_VALUES.AUTHOR_LAST_NAME
				break
			case 'COPYRIGHT':
				out[token] = project?.copyright || SAMPLE_VALUES.COPYRIGHT
				break
			default:
				out[token] = SAMPLE_VALUES[token] ?? ''
		}
	})
	return out
}

// ── Preview rendering ─────────────────────────────────────────────────────────

function escapeHtml(s) {
	return String(s ?? '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
}

/**
 * Replaces every <span data-token="X"></span> in template HTML with the
 * resolved value. All other markup is left untouched.
 */
export function renderPreviewHtml(html, values) {
	if (!html) return ''
	return html.replace(
		/<span[^>]*\bdata-token="([^"]+)"[^>]*>\s*<\/span>/gi,
		(_match, token) => escapeHtml(values?.[token] ?? '')
	)
}