import { HELP_SECTIONS, ORPHAN_SECTION_ID, ORPHAN_SECTION_TITLE } from './helpSections'

/**
 * helpRegistry — auto-discovers every help topic at build time and exposes
 * lookups for the Help Center.
 *
 * AUTHORING: drop a `.md` file anywhere under `src/help/content/` with simple
 * frontmatter and it becomes a live topic. No registration step.
 *
 *   ---
 *   id: ai.review.rail
 *   title: The AI Review Rail
 *   section: ai
 *   order: 10
 *   ---
 *   Body markdown here. Cross-link with [other topic](#help:editor.templates).
 *
 * `id` is the stable address used by HelpButton(topic=...) and #help: links.
 * `section` must be one of the ids in helpSections.js. `order` (number) sorts
 * topics within a section; ties break on title.
 */

// Vite eager glob: every markdown file under content/, imported as raw text.
const modules = import.meta.glob('./content/**/*.md', {
	eager: true,
	query: '?raw',
	import: 'default',
})

/**
 * Parse leading `--- ... ---` frontmatter. Returns { data, body }.
 * Minimal key: value parser — values are trimmed strings; numeric strings are
 * left as strings and coerced by callers where needed.
 */
function parseFrontmatter(raw) {
	const text = String(raw ?? '').replace(/\r\n?/g, '\n')
	const match = text.match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/)
	if (!match) {
		return { data: {}, body: text }
	}
	const data = {}
	for (const line of match[1].split('\n')) {
		const kv = line.match(/^([A-Za-z0-9_-]+)\s*:\s*(.*)$/)
		if (kv) {
			let value = kv[2].trim()
			if (
				(value.startsWith('"') && value.endsWith('"')) ||
				(value.startsWith("'") && value.endsWith("'"))
			) {
				value = value.slice(1, -1)
			}
			data[kv[1]] = value
		}
	}
	return { data, body: match[2] }
}

function buildTopics() {
	const byId = new Map()
	const duplicates = []

	for (const [path, raw] of Object.entries(modules)) {
		const { data, body } = parseFrontmatter(raw)
		const id = (data.id || '').trim()
		if (!id) {
			// A file with no id is unaddressable; skip but surface in dev.
			if (import.meta.env?.DEV) {
				console.warn(`[help] ${path} has no \`id\` in frontmatter; skipped.`)
			}
			continue
		}
		const orderNum = Number(data.order)
		const topic = {
			id,
			title: (data.title || id).trim(),
			section: (data.section || '').trim(),
			order: Number.isFinite(orderNum) ? orderNum : 1000,
			body: body.trim(),
			path,
		}
		if (byId.has(id)) {
			duplicates.push(id)
		}
		byId.set(id, topic)
	}

	if (duplicates.length && import.meta.env?.DEV) {
		console.warn(`[help] duplicate topic id(s): ${[...new Set(duplicates)].join(', ')}`)
	}

	return byId
}

const TOPICS = buildTopics()

/** Look up a single topic by id. Returns undefined if missing. */
export function getTopic(id) {
	return TOPICS.get(id)
}

/** True if a topic id exists. */
export function hasTopic(id) {
	return TOPICS.has(id)
}

/** All topics as a flat array. */
export function allTopics() {
	return [...TOPICS.values()]
}

/**
 * The table of contents: sections (in helpSections order) each carrying their
 * topics (sorted by order, then title). Empty sections are omitted. Topics
 * whose section is unknown collect under a trailing "More" group.
 */
export function getTableOfContents() {
	const orphans = []
	const grouped = new Map(HELP_SECTIONS.map((s) => [s.id, []]))

	for (const topic of TOPICS.values()) {
		if (grouped.has(topic.section)) {
			grouped.get(topic.section).push(topic)
		} else {
			orphans.push(topic)
		}
	}

	const sortTopics = (list) =>
		list.sort((a, b) => a.order - b.order || a.title.localeCompare(b.title))

	const toc = HELP_SECTIONS
		.map((section) => ({ ...section, topics: sortTopics(grouped.get(section.id)) }))
		.filter((section) => section.topics.length > 0)

	if (orphans.length) {
		toc.push({ id: ORPHAN_SECTION_ID, title: ORPHAN_SECTION_TITLE, topics: sortTopics(orphans) })
	}

	return toc
}

/**
 * The id of the topic shown when the Help Center opens with no specific target.
 * Prefers an explicit `index` topic, else the first topic of the first section.
 */
export function getDefaultTopicId() {
	if (TOPICS.has('index')) return 'index'
	const toc = getTableOfContents()
	return toc[0]?.topics[0]?.id ?? null
}

/**
 * Case-insensitive search over title and body. Returns matching topics with a
 * crude relevance ordering (title hits first). Empty/short query returns [].
 */
export function searchTopics(query) {
	const q = String(query ?? '').trim().toLowerCase()
	if (q.length < 2) return []
	const results = []
	for (const topic of TOPICS.values()) {
		const titleHit = topic.title.toLowerCase().includes(q)
		const bodyHit = topic.body.toLowerCase().includes(q)
		if (titleHit || bodyHit) {
			results.push({ topic, score: titleHit ? 0 : 1 })
		}
	}
	results.sort((a, b) => a.score - b.score || a.topic.title.localeCompare(b.topic.title))
	return results.map((r) => r.topic)
}
