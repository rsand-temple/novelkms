/**
 * pageEstimate.js
 *
 * Rough "~N pages" estimate for the editor status bar — a closed-form
 * calculation from word count, paragraph count, and page size, deliberately
 * NOT a real pagination pass (no rendering the whole document on every
 * navigation). Modeled on the one body-text convention NovelKMS already has —
 * ExportService's "standard manuscript format" (12pt Times New Roman,
 * double-spaced, 0.5" first-line indent) — so the estimate roughly tracks the
 * page count of an actual export. Being off by one at borderline word counts
 * is expected and fine.
 *
 * Formula, given the usable (margin-excluded) page area:
 *   charsPerLine  = usableWidthPt / avgCharWidthPt   (avg glyph ≈ 0.5em for a
 *                   proportional serif font)
 *   wordsPerLine  = charsPerLine / avgCharsPerWord    (~5-letter word + space)
 *   linesPerPage  = usableHeightPt / lineHeightPt     (double-spaced 12pt)
 *   totalLines    = wordCount / wordsPerLine
 *                   + paragraphCount * HALF_LINE_PER_PARAGRAPH
 *                   (each paragraph break leaves its last line partly empty,
 *                   on average about half a line "wasted")
 *   pages         = ceil(totalLines / linesPerPage)
 */

import { PX_PER_IN } from './pageConfig'

const PT_PER_IN = 72
const FONT_SIZE_PT = 12
const AVG_CHAR_WIDTH_PT = FONT_SIZE_PT * 0.5   // ~0.5em average glyph width, Times New Roman
const AVG_CHARS_PER_WORD = 6                    // ~5-letter average English word + 1 space
const LINE_HEIGHT_PT = FONT_SIZE_PT * 2         // double-spaced, matches ExportService
const HALF_LINE_PER_PARAGRAPH = 0.5

/**
 * @param {number} wordCount
 * @param {number} paragraphCount
 * @param {{ widthPx:number, heightPx:number, marginTopPx:number, marginBottomPx:number,
 *           marginInnerPx:number, marginOuterPx:number }|null} pageConfig — the
 *   same shape produced by utils/pageConfig.js's derivePageConfig() / DEFAULT_PAGE_CONFIG
 * @returns {number|null} estimated page count (minimum 1), or null when there's
 *   nothing to estimate (no page config, or no words yet)
 */
export function estimatePages(wordCount, paragraphCount, pageConfig) {
	if (!pageConfig || !(wordCount > 0)) return null

	const usableWidthIn = (pageConfig.widthPx - pageConfig.marginInnerPx - pageConfig.marginOuterPx) / PX_PER_IN
	const usableHeightIn = (pageConfig.heightPx - pageConfig.marginTopPx - pageConfig.marginBottomPx) / PX_PER_IN
	if (usableWidthIn <= 0 || usableHeightIn <= 0) return null

	const charsPerLine = (usableWidthIn * PT_PER_IN) / AVG_CHAR_WIDTH_PT
	const wordsPerLine = Math.max(1, charsPerLine / AVG_CHARS_PER_WORD)
	const linesPerPage = (usableHeightIn * PT_PER_IN) / LINE_HEIGHT_PT
	if (linesPerPage <= 0) return null

	const totalLines = (wordCount / wordsPerLine) + ((paragraphCount ?? 0) * HALF_LINE_PER_PARAGRAPH)
	return Math.max(1, Math.ceil(totalLines / linesPerPage))
}
