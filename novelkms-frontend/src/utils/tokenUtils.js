// Template field tokens — catalog, labels, resolution, and preview rendering.
//
// A token is stored in template HTML as <span data-token="TITLE"></span> (an
// atomic inline node; see src/extensions/TemplateToken.js). At render/export
// time the span is replaced by the resolved value for the book/project/part.

export const TOKEN_LABELS = {
	TITLE: 'Title',
	SUBTITLE: 'Subtitle',
	SHORT_TITLE: 'Short Title',
	// AUTHOR_FULL_NAME resolves to the project Display Name field when set;
	// falls back to first+last concatenation when it is blank.
	AUTHOR_FULL_NAME: 'Author Full Name',
	AUTHOR_LAST_NAME: 'Author Last Name',
	EMAIL: 'Email Address',
	PHONE: 'Phone Number',
	COPYRIGHT: 'Copyright',
	WORDS: 'Word Count',
	PART_NUMBER: 'Part Number',
	PART_TITLE: 'Part Title',
	PART_SUBTITLE: 'Part Subtitle',
}

// Tokens offered in the Insert-field menu, per template type.
export const TOKENS_BY_TYPE = {
	cover: [
		'TITLE', 'SUBTITLE', 'SHORT_TITLE',
		'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME',
		'EMAIL', 'PHONE', 'COPYRIGHT', 'WORDS',
	],
	part: [
		'PART_NUMBER', 'PART_TITLE', 'PART_SUBTITLE',
		'TITLE', 'AUTHOR_FULL_NAME', 'AUTHOR_LAST_NAME',
		'COPYRIGHT',
	],
}

/** [{ token, label }] for the Insert-field menu, given a template type. */
export function tokensForType(type) {
	const list = TOKENS_BY_TYPE[String(type || '').toLowerCase()] || []
	return list.map(token => ({ token, label: TOKEN_LABELS[token] || token }))
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

/**
 * Formats a word count for display on cover/part templates.
 * Rounds to the nearest 500 and prefixes "About " — standard manuscript
 * submission convention so agents/publishers get an approximate figure.
 * Example: 76,432 → "About 76,500"
 */
function formatWordCount(n) {
	if (n == null) return ''
	// Defensive: anything that isn't a finite number renders blank rather than
	// printing "About NaN" onto a cover page. This guards against a caller
	// passing the whole { wordCount, paragraphCount } DTO instead of the number.
	const num = Number(n)
	if (!Number.isFinite(num)) return ''
	const rounded = Math.round(num / 500) * 500
	return 'About ' + rounded.toLocaleString('en-US')
}

// ── Value resolution ──────────────────────────────────────────────────────────

/**
 * Resolves AUTHOR_FULL_NAME: prefers the project Display Name field (a single
 * formatted string the author controls), falls back to first+last concatenation.
 */
function resolveAuthorFullName(project) {
	if (project?.displayName?.trim()) return project.displayName.trim()
	const parts = [project?.authorFirstName, project?.authorLastName].filter(Boolean)
	return parts.join(' ') || null
}

function realValues(book, project, wordCount) {
	return {
		TITLE: book?.title || null,
		SUBTITLE: book?.subtitle || null,
		SHORT_TITLE: book?.shortTitle || null,
		AUTHOR_FULL_NAME: resolveAuthorFullName(project),
		AUTHOR_LAST_NAME: project?.authorLastName || null,
		EMAIL: project?.emailAddress || null,
		PHONE: project?.phoneNumber || null,
		COPYRIGHT: project?.copyright || null,
		WORDS: wordCount != null ? formatWordCount(wordCount) : null,
		PART_NUMBER: null,
		PART_TITLE: null,
		PART_SUBTITLE: null,
	}
}

/**
 * Returns a { token: displayString } map for template editor preview.
 * Every unset field resolves to '' — tokens render blank when data is not
 * available rather than showing placeholder text.
 *
 * @param {string}  scope      - 'book' | 'global'
 * @param {object}  book       - Book record
 * @param {object}  project    - Project record
 * @param {number?} wordCount  - Total book word count (null = blank)
 */
export function resolveValues({ scope, book, project, wordCount = null }) {
	const real = scope === 'book' ? realValues(book, project, wordCount) : {}
	const out = {}
	Object.keys(TOKEN_LABELS).forEach(token => {
		out[token] = real[token] ?? ''
	})
	return out
}

/**
 * Returns a { token: displayString } map resolved against a specific part.
 * Used by PartPagePreview to render the part template with real data.
 *
 * @param {object}  part       - Part record
 * @param {number}  partNumber - 1-based ordinal position within the book
 * @param {object}  book       - Book record
 * @param {object}  project    - Project record
 * @param {number?} wordCount  - Total book word count (null = blank)
 */
export function resolveValuesForPart({ part, partNumber, book, project, wordCount = null }) {
	const out = {}
	Object.keys(TOKEN_LABELS).forEach(token => {
		switch (token) {
			case 'PART_NUMBER':
				out[token] = partNumber ? toRoman(partNumber) : ''
				break
			case 'PART_TITLE':
				out[token] = part?.title || ''
				break
			case 'PART_SUBTITLE':
				out[token] = part?.subtitle || ''
				break
			case 'TITLE':
				out[token] = book?.title || ''
				break
			case 'SUBTITLE':
				out[token] = book?.subtitle || ''
				break
			case 'SHORT_TITLE':
				out[token] = book?.shortTitle || ''
				break
			case 'AUTHOR_FULL_NAME':
				out[token] = resolveAuthorFullName(project) || ''
				break
			case 'AUTHOR_LAST_NAME':
				out[token] = project?.authorLastName || ''
				break
			case 'EMAIL':
				out[token] = project?.emailAddress || ''
				break
			case 'PHONE':
				out[token] = project?.phoneNumber || ''
				break
			case 'COPYRIGHT':
				out[token] = project?.copyright || ''
				break
			case 'WORDS':
				out[token] = wordCount != null ? formatWordCount(wordCount) : ''
				break
			default:
				out[token] = ''
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