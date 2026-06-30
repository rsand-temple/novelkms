/**
 * helpSections — the master list of help sections, in display order.
 *
 * This is the ONE place that controls how the Help Center's table of contents
 * is grouped and ordered. To add a section: add an entry here and tag topics
 * into it with `section: <id>` in their frontmatter. To reorder sections:
 * reorder this array. Topics within a section are ordered by their frontmatter
 * `order` (then title) — see helpRegistry.js.
 *
 * A topic whose `section` does not match any id here still loads, but falls
 * into the catch-all "More" group at the bottom; the validator warns about it.
 */
export const HELP_SECTIONS = [
	{ id: 'getting-started', title: 'Getting Started' },
	{ id: 'manuscript', title: 'Manuscript & Navigation' },
	{ id: 'editor', title: 'Editor & Formatting' },
	{ id: 'ai', title: 'AI Review & Summaries' },
	{ id: 'codex', title: 'Codex' },
	{ id: 'import-export', title: 'Import & Export' },
	{ id: 'account', title: 'Account & Billing' },
]

/** Id used for topics whose section does not match any defined section. */
export const ORPHAN_SECTION_ID = '__more__'
export const ORPHAN_SECTION_TITLE = 'More'

export default HELP_SECTIONS
