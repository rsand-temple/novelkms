export function normalizeForSearch(value, matchCase) {
	const text = value ?? ''
	return matchCase ? text : text.toLocaleLowerCase()
}

export function htmlToPlainText(html) {
	if (!html) return ''
	const doc = new DOMParser().parseFromString(html, 'text/html')
	return doc.body.textContent ?? ''
}

export function countOccurrences(text, query, matchCase = false) {
	if (!query) return 0
	const haystack = normalizeForSearch(text, matchCase)
	const needle = normalizeForSearch(query, matchCase)
	if (!needle) return 0
	let count = 0
	let from = 0
	while (from <= haystack.length - needle.length) {
		const at = haystack.indexOf(needle, from)
		if (at < 0) break
		count += 1
		from = at + Math.max(needle.length, 1)
	}
	return count
}

export function countHtmlOccurrences(html, query, matchCase = false) {
	return countOccurrences(htmlToPlainText(html), query, matchCase)
}
