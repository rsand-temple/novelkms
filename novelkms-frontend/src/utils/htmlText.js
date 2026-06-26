// Strips HTML markup down to plain text for display-only contexts where a full
// rich-text render would be too heavy (e.g. a dense multi-chapter glance list).
// Not for security-sensitive use — this only ever runs on content the current
// user already authored/saved themselves (memory docs, summaries), the same
// trust boundary EditorPanel's own dangerouslySetInnerHTML preview already
// relies on. DOMParser does not execute scripts, so this is safe either way.
export function stripHtmlToText(html) {
	if (!html) return ''
	const doc = new DOMParser().parseFromString(html, 'text/html')
	return (doc.body.textContent || '').replace(/\s+/g, ' ').trim()
}
