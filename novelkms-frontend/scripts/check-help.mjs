#!/usr/bin/env node
/**
 * check-help.mjs — static validator for the NovelKMS help system.
 *
 * Run from novelkms-frontend/:  node scripts/check-help.mjs
 *
 * FAILS (exit 1) on:
 *   - a [..](#help:ID) cross-link whose ID has no topic file
 *   - a HelpButton topic="ID" / openHelp('ID') in code whose ID has no topic
 *   - a duplicate topic id
 *   - a markdown file with no `id` in frontmatter
 *
 * WARNS (exit 0) on:
 *   - a topic whose `section` is not declared in helpSections.js (lands in "More")
 *
 * Zero dependencies; walks the filesystem directly so it does not need Vite.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const frontendRoot = join(here, '..')
const contentDir = join(frontendRoot, 'src/help/content')
const srcDir = join(frontendRoot, 'src')
const sectionsFile = join(frontendRoot, 'src/help/helpSections.js')

const errors = []
const warnings = []

function walk(dir, filter) {
	const out = []
	for (const name of readdirSync(dir)) {
		const full = join(dir, name)
		const st = statSync(full)
		if (st.isDirectory()) {
			out.push(...walk(full, filter))
		} else if (filter(full)) {
			out.push(full)
		}
	}
	return out
}

function parseFrontmatter(raw) {
	const text = raw.replace(/\r\n?/g, '\n')
	const m = text.match(/^---\n([\s\S]*?)\n---\n?([\s\S]*)$/)
	if (!m) return { data: {}, body: text }
	const data = {}
	for (const line of m[1].split('\n')) {
		const kv = line.match(/^([A-Za-z0-9_-]+)\s*:\s*(.*)$/)
		if (kv) {
			let v = kv[2].trim()
			if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
				v = v.slice(1, -1)
			}
			data[kv[1]] = v
		}
	}
	return { data, body: m[2] }
}

// --- Load declared sections ---------------------------------------------------
let sectionIds = new Set()
try {
	const mod = await import(pathToFileURL(sectionsFile).href)
	const sections = mod.HELP_SECTIONS || mod.default || []
	sectionIds = new Set(sections.map((s) => s.id))
} catch (e) {
	errors.push(`Could not load helpSections.js: ${e.message}`)
}

// --- Index topics -------------------------------------------------------------
const mdFiles = walk(contentDir, (f) => f.endsWith('.md'))
const topics = new Map() // id -> { path, section }
for (const file of mdFiles) {
	const rel = relative(frontendRoot, file)
	const { data, body } = parseFrontmatter(readFileSync(file, 'utf-8'))
	const id = (data.id || '').trim()
	if (!id) {
		errors.push(`${rel}: missing \`id\` in frontmatter`)
		continue
	}
	if (topics.has(id)) {
		errors.push(`${rel}: duplicate topic id "${id}" (also in ${topics.get(id).path})`)
		continue
	}
	topics.set(id, { path: rel, section: (data.section || '').trim(), body })
	const section = (data.section || '').trim()
	if (section && sectionIds.size && !sectionIds.has(section)) {
		warnings.push(`${rel}: section "${section}" is not declared in helpSections.js (will fall into "More")`)
	}
}

const validId = (id) => topics.has(id)

// --- Check cross-links in bodies ---------------------------------------------
const linkRe = /\(#help:([A-Za-z0-9._-]+)\)/g
for (const [id, t] of topics) {
	let m
	while ((m = linkRe.exec(t.body)) !== null) {
		if (!validId(m[1])) {
			errors.push(`${t.path}: cross-link #help:${m[1]} points to a topic that does not exist`)
		}
	}
}

// --- Check topic= / openHelp() references in code ----------------------------
const codeFiles = walk(srcDir, (f) => /\.(jsx?|tsx?)$/.test(f) && !f.includes('/help/content/'))
const refRes = [
	/\btopic\s*=\s*["']([A-Za-z0-9._-]+)["']/g, // <HelpButton topic="x.y" />
	/\bopenHelp\(\s*["']([A-Za-z0-9._-]+)["']\s*\)/g, // openHelp('x.y')
]
for (const file of codeFiles) {
	const rel = relative(frontendRoot, file)
	const text = readFileSync(file, 'utf-8')
	for (const re of refRes) {
		re.lastIndex = 0
		let m
		while ((m = re.exec(text)) !== null) {
			// Only check ids that look like help topic ids (contain a dot or match a topic),
			// so generic open('...') calls elsewhere are not falsely flagged.
			const candidate = m[1]
			if (candidate.includes('.') || validId(candidate)) {
				if (!validId(candidate)) {
					errors.push(`${rel}: references help topic "${candidate}" which does not exist`)
				}
			}
		}
	}
}

// --- Report -------------------------------------------------------------------
console.log(`help: ${topics.size} topics across ${sectionIds.size} sections`)
for (const w of warnings) console.log(`  WARN  ${w}`)
if (errors.length) {
	for (const e of errors) console.error(`  ERROR ${e}`)
	console.error(`\nhelp validation FAILED with ${errors.length} error(s).`)
	process.exit(1)
}
console.log(`help validation passed${warnings.length ? ` (${warnings.length} warning(s))` : ''}.`)
