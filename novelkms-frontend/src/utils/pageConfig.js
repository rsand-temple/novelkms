/**
 * pageConfig.js
 *
 * Central source of truth for page size presets, runtime page config
 * derivation, and the fixed pixel heights of page UI elements.
 *
 * Place in: src/utils/pageConfig.js
 */

/** CSS reference pixels per inch (fixed in the CSS spec). */
export const PX_PER_IN = 96

/**
 * Fixed pixel heights for the non-editable page header and footer strips.
 * These constants are shared between PageHeader.jsx, PageFooter.jsx, and
 * PageRulerOverlay.jsx so all three components use identical heights.
 */
export const PAGE_HEADER_H = 38   // px
export const PAGE_FOOTER_H = 38   // px

/** Gray gap rendered between the physical bottom of one page and the top
 *  of the next — represents the desk surface visible between pages. */
export const PAGE_GAP_H = 20      // px

/**
 * Canonical page size presets.
 * widthIn / heightIn are in inches.
 */
export const PAGE_SIZE_PRESETS = {
  LETTER:           { widthIn: 8.50,  heightIn: 11.00, label: 'Letter (8.5\u2033 \u00D7 11\u2033)' },
  A4:               { widthIn: 8.27,  heightIn: 11.69, label: 'A4 (8.27\u2033 \u00D7 11.69\u2033)' },
  TRADE_PAPERBACK:  { widthIn: 6.00,  heightIn:  9.00, label: 'Trade Paperback (6\u2033 \u00D7 9\u2033)' },
  MASS_MARKET:      { widthIn: 4.25,  heightIn:  6.87, label: 'Mass Market (4.25\u2033 \u00D7 6.87\u2033)' },
  HARDBACK:         { widthIn: 6.14,  heightIn:  9.21, label: 'Hardback (6.14\u2033 \u00D7 9.21\u2033)' },
  CUSTOM:           { widthIn: null,  heightIn: null,  label: 'Custom\u2026' },
}

/** Ordered list for rendering the Select in PropertiesPanel. */
export const PAGE_SIZE_PRESET_OPTIONS = Object.entries(PAGE_SIZE_PRESETS).map(
  ([value, { label }]) => ({ value, label })
)

/**
 * Derive a runtime pageConfig from a Book record.
 * Returns null when page layout is disabled or book is falsy.
 *
 * @param {object|null} book  Book record from useBook()
 * @returns {object|null}
 */
export function derivePageConfig(book) {
  if (!book?.pageLayoutEnabled) return null

  const preset =
    book.pageSizePreset === 'CUSTOM'
      ? { widthIn: book.pageWidthIn, heightIn: book.pageHeightIn }
      : PAGE_SIZE_PRESETS[book.pageSizePreset] ?? PAGE_SIZE_PRESETS.LETTER

  if (!preset.widthIn || !preset.heightIn) return null

  const marginTopIn    = book.pageMarginTopIn    || 1.0
  const marginBottomIn = book.pageMarginBottomIn || 1.0
  const marginInnerIn  = book.pageMarginInnerIn  || 1.25
  const marginOuterIn  = book.pageMarginOuterIn  || 1.0

  const widthPx        = preset.widthIn  * PX_PER_IN
  const heightPx       = preset.heightIn * PX_PER_IN
  const marginTopPx    = marginTopIn    * PX_PER_IN
  const marginBottomPx = marginBottomIn * PX_PER_IN
  const marginInnerPx  = marginInnerIn  * PX_PER_IN
  const marginOuterPx  = marginOuterIn  * PX_PER_IN

  // Pure text area height — excludes all margins, headers, and footers.
  // This is the interval between successive page-seam overlay zones.
  const contentHeightPx = heightPx - marginTopPx - marginBottomPx
                          - PAGE_HEADER_H - PAGE_FOOTER_H

  if (contentHeightPx <= 0) return null

  return {
    widthPx,
    heightPx,
    contentHeightPx,
    marginTopPx,
    marginBottomPx,
    marginInnerPx,
    marginOuterPx,
    widthIn:        preset.widthIn,
    heightIn:       preset.heightIn,
    marginTopIn,
    marginBottomIn,
    marginInnerIn,
    marginOuterIn,
  }
}