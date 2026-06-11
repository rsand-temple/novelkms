// Paragraph-style roster (must match backend StyleDefaults.STYLE_KEYS) plus
// helpers to turn resolved style definitions into editor CSS.

export const STYLE_ORDER = [
	'normal', 'h1', 'h2', 'h3',
	'blockquote', 'emphasis', 'report',
	'chapter_title', 'chapter_subtitle',
	'cover_title', 'cover_subtitle',
	'part_title', 'part_subtitle',
]

export const STYLE_LABELS = {
	normal:           'Normal',
	h1:               'Heading 1',
	h2:               'Heading 2',
	h3:               'Heading 3',
	blockquote:       'Block Quote',
	emphasis:         'Emphasis',
	report:           'Report',
	chapter_title:    'Chapter Title',
	chapter_subtitle: 'Chapter Subtitle',
	cover_title:      'Cover Title',
	cover_subtitle:   'Cover Subtitle',
	part_title:       'Part Title',
	part_subtitle:    'Part Subtitle',
}

// Styles that map to native TipTap heading nodes rather than paragraph styleKeys.
export const HEADING_KEYS = ['h1', 'h2', 'h3']

/** A StyleDefinition → a CSS-in-JS (sx) object. */
export function definitionToCss(def) {
	if (!def) return {}
	const css = {
		fontWeight: def.bold ? 700 : 400,
		fontStyle:  def.italic ? 'italic' : 'normal',
	}
	if (def.fontFamily)              css.fontFamily   = def.fontFamily
	if (def.fontSize)                css.fontSize     = def.fontSize
	if (def.firstLineIndent != null) css.textIndent   = def.firstLineIndent  // first-line indent
	if (def.textIndent)              css.marginLeft   = def.textIndent        // block / left indent
	if (def.spacingBefore != null)   css.marginTop    = def.spacingBefore
	if (def.spacingAfter != null)    css.marginBottom = def.spacingAfter
	return css
}

/**
 * Builds an sx fragment of selector → css from a resolved stylesheet
 * (array of Style objects). Headings target the native node; other keys target
 * `p[data-style="key"]`. `normal` is intentionally skipped — base paragraphs
 * stay driven by project (DocSettings) defaults in this step to avoid two
 * overlapping controls for body text.
 */
export function buildStyleSx(sheet) {
	if (!Array.isArray(sheet)) return {}
	const sx = {}
	for (const style of sheet) {
		const key = style?.styleKey
		if (!key || key === 'normal') continue
		const css = definitionToCss(style.definition)
		if (HEADING_KEYS.includes(key)) {
			sx[`& .tiptap ${key}`] = css
		} else {
			sx[`& .tiptap p[data-style="${key}"]`] = css
		}
	}
	return sx
}
