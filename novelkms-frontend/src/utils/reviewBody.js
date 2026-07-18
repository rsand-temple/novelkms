// A Phase-1 human review is a single plain-text document (spec §18). We store it as
// simple paragraph HTML in human_review.content_html so it lines up with every other
// review-network body and so a later rich-text upgrade is a change of editor, not of
// storage. These helpers convert between the textarea's plain text and that HTML.
//
// The extraction direction is also a safety boundary: a review body is authored by
// another user, so when the AUTHOR reads received feedback we render it as PLAIN TEXT
// (htmlToPlain), never as live HTML. Even if a hostile reviewer posted markup or a
// script, the author sees inert text — no sandboxed iframe needed while reviews are
// plain-text only.

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;' }

function escapeHtml(s) {
	return s.replace(/[&<>]/g, c => ESCAPES[c])
}

/** Wraps blank-line-separated paragraphs of plain text into <p> blocks. */
export function plainToHtml(text) {
	if (!text) return ''
	const paras = text
		.replace(/\r\n/g, '\n')
		.split(/\n{2,}/)
		.map(p => p.trim())
		.filter(Boolean)
	if (paras.length === 0) return ''
	// Single newlines inside a paragraph become <br> so line breaks survive a round trip.
	return paras.map(p => `<p>${escapeHtml(p).replace(/\n/g, '<br>')}</p>`).join('')
}

/** Reverses {@link plainToHtml}: paragraph/line breaks back to newlines, tags stripped. */
export function htmlToPlain(html) {
	if (!html) return ''
	const text = html
		.replace(/<\/p>\s*<p[^>]*>/gi, '\n\n')
		.replace(/<br\s*\/?>/gi, '\n')
		.replace(/<\/p>/gi, '')
		.replace(/<p[^>]*>/gi, '')
		.replace(/<[^>]+>/g, '')          // any remaining tags
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&quot;/g, '"')
		.replace(/&#39;/g, "'")
		.replace(/&amp;/g, '&')           // ampersand last, so &amp;lt; -> &lt; not <
		.replace(/\n{3,}/g, '\n\n')
	return text.trim()
}
