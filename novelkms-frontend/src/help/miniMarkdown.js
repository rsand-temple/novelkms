/**
 * miniMarkdown — a small, dependency-free Markdown → HTML renderer scoped to
 * exactly the subset NovelKMS help documents use. It exists so the entire help
 * feature ships with no extra npm install and stays statically verifiable.
 *
 * Supported syntax:
 *   - ATX headings            # .. ######
 *   - Bold / italic           **bold**  *italic*  _italic_
 *   - Inline code             `code`
 *   - Fenced code blocks      ```lang ... ```
 *   - Links                   [label](url)   incl. cross-links [x](#help:topic.id)
 *   - Unordered lists         -  /  *
 *   - Ordered lists           1.  2.  ...
 *   - Blockquotes             > quote
 *   - Horizontal rules        --- / ***
 *   - Paragraphs / line breaks
 *
 * Output is rendered through dangerouslySetInnerHTML in MarkdownView, the same
 * trust boundary RichTextPreview already uses. All text is HTML-escaped first,
 * so author markup cannot inject live HTML; only the structural tags this
 * renderer emits are produced. If richer Markdown is ever needed, this single
 * file can be replaced with `marked` without touching anything else.
 *
 * NOTE: this module is intentionally pure (no imports) so scripts/check-help.mjs
 * can load it directly in Node for offline rendering checks if ever wanted.
 */

function escapeHtml(text) {
	return text
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}

function escapeAttr(text) {
	return escapeHtml(text).replace(/'/g, '&#39;')
}

/**
 * Inline formatting, applied to already-HTML-escaped text. Order matters:
 * code spans are pulled out first so their contents are never re-processed.
 */
function renderInline(escaped) {
	const codeSpans = []
	let out = escaped.replace(/`([^`]+)`/g, (_m, code) => {
		const token = `\u0000CODE${codeSpans.length}\u0000`
		codeSpans.push(`<code>${code}</code>`)
		return token
	})

	// Links: [label](href). href is already escaped; quote it safely.
	out = out.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, label, href) => {
		const cleanHref = href.trim()
		const isHelp = cleanHref.startsWith('#help:')
		const isExternal = /^https?:\/\//i.test(cleanHref)
		const rel = isExternal ? ' target="_blank" rel="noopener noreferrer"' : ''
		return `<a href="${escapeAttr(cleanHref)}"${rel}>${label}</a>`
	})

	// Bold then italic. Bold uses ** or __; italic uses * or _.
	out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
	out = out.replace(/__([^_]+)__/g, '<strong>$1</strong>')
	out = out.replace(/(^|[^*])\*([^*\s][^*]*?)\*/g, '$1<em>$2</em>')
	out = out.replace(/(^|[^_])_([^_\s][^_]*?)_/g, '$1<em>$2</em>')

	// Restore code spans.
	out = out.replace(/\u0000CODE(\d+)\u0000/g, (_m, i) => codeSpans[Number(i)])
	return out
}

/**
 * Render a Markdown string to an HTML string.
 */
export function renderMarkdown(markdown) {
	const src = String(markdown ?? '').replace(/\r\n?/g, '\n')
	const lines = src.split('\n')
	const html = []

	let i = 0
	let paragraph = []

	function flushParagraph() {
		if (paragraph.length === 0) return
		const text = paragraph.join('\n').trim()
		if (text) {
			html.push(`<p>${renderInline(escapeHtml(text)).replace(/\n/g, '<br />')}</p>`)
		}
		paragraph = []
	}

	while (i < lines.length) {
		const line = lines[i]

		// Fenced code block.
		const fence = line.match(/^```(.*)$/)
		if (fence) {
			flushParagraph()
			const code = []
			i++
			while (i < lines.length && !/^```\s*$/.test(lines[i])) {
				code.push(lines[i])
				i++
			}
			i++ // skip closing fence
			html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
			continue
		}

		// Blank line ends a paragraph.
		if (/^\s*$/.test(line)) {
			flushParagraph()
			i++
			continue
		}

		// Horizontal rule.
		if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
			flushParagraph()
			html.push('<hr />')
			i++
			continue
		}

		// Heading.
		const heading = line.match(/^(#{1,6})\s+(.*)$/)
		if (heading) {
			flushParagraph()
			const level = heading[1].length
			html.push(`<h${level}>${renderInline(escapeHtml(heading[2].trim()))}</h${level}>`)
			i++
			continue
		}

		// Blockquote (one or more consecutive > lines).
		if (/^\s*>\s?/.test(line)) {
			flushParagraph()
			const quote = []
			while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
				quote.push(lines[i].replace(/^\s*>\s?/, ''))
				i++
			}
			html.push(`<blockquote>${renderInline(escapeHtml(quote.join('\n'))).replace(/\n/g, '<br />')}</blockquote>`)
			continue
		}

		// Unordered list.
		if (/^\s*[-*]\s+/.test(line)) {
			flushParagraph()
			const items = []
			while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
				items.push(lines[i].replace(/^\s*[-*]\s+/, ''))
				i++
			}
			html.push(`<ul>${items.map((it) => `<li>${renderInline(escapeHtml(it.trim()))}</li>`).join('')}</ul>`)
			continue
		}

		// Ordered list.
		if (/^\s*\d+\.\s+/.test(line)) {
			flushParagraph()
			const items = []
			while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
				items.push(lines[i].replace(/^\s*\d+\.\s+/, ''))
				i++
			}
			html.push(`<ol>${items.map((it) => `<li>${renderInline(escapeHtml(it.trim()))}</li>`).join('')}</ol>`)
			continue
		}

		// Otherwise accumulate into the current paragraph.
		paragraph.push(line)
		i++
	}

	flushParagraph()
	return html.join('\n')
}

export default renderMarkdown
