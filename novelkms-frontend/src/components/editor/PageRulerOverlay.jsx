import { useState, useEffect } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { PAGE_HEADER_H, PAGE_FOOTER_H, PAGE_GAP_H } from '../../utils/pageConfig'

/**
 * PageRulerOverlay
 *
 * Renders full "page seam" zones between every pair of pages in the editor.
 * Each seam zone contains, top to bottom:
 *
 *   ┌─────────────────────────────────────────┐
 *   │  bottom margin  (white, hides gutter)   │  marginBottomPx (snapped)
 *   ├─────────────────────────────────────────┤
 *   │  footer of ending page                  │  PAGE_FOOTER_H
 *   ├─────────────────────────────────────────┤
 *   │  gray gap  (desk surface between pages) │  gapH (adjusted for line-snap)
 *   ├─────────────────────────────────────────┤
 *   │  header of starting page                │  PAGE_HEADER_H
 *   ├─────────────────────────────────────────┤
 *   │  top margin  (white, hides gutter)      │  marginTopPx (snapped)
 *   └─────────────────────────────────────────┘
 *
 * LINE-HEIGHT SNAPPING
 * Both contentHeightPx (where the seam starts) and betweenPageH (seam
 * height) are snapped to multiples of the rendered line height derived from
 * settings. This guarantees seam zone boundaries always fall between text
 * lines rather than mid-character, preventing words from being clipped.
 *
 * The gray gap absorbs any rounding (it grows slightly to align betweenPageH
 * to the next line boundary). All other zone heights are preserved exactly.
 *
 * Props:
 *   contentRef      React ref on the div wrapping <EditorContent>
 *   pageConfig      Derived page config object from derivePageConfig()
 *   book            Book record (for short title / title)
 *   settings        Project settings (fontSize, lineHeight) for line-snap
 *   authorLastName  For header text
 *   authorFullName  For footer copyright line
 *   startPage       Page number of the first page in this chapter (default 1)
 */

/** Parse line height in CSS pixels from project settings. */
function getLineHeightPx(settings) {
  if (!settings) return 24
  const fontSize = settings.fontSize || '1rem'
  let fontSizePx = 16  // fallback
  const remMatch = fontSize.match(/^([\d.]+)rem$/)
  const pxMatch  = fontSize.match(/^([\d.]+)px$/)
  if (remMatch) fontSizePx = parseFloat(remMatch[1]) * 16
  else if (pxMatch) fontSizePx = parseFloat(pxMatch[1])
  const lhMultiplier = parseFloat(settings.lineHeight || '1.6') || 1.6
  return Math.max(fontSizePx * lhMultiplier, 8)
}

export default function PageRulerOverlay({
  contentRef,
  pageConfig,
  book,
  settings,
  authorLastName = '',
  authorFullName = '',
  startPage = 1,
}) {
  const [contentHeight, setContentHeight] = useState(0)

  useEffect(() => {
    const el = contentRef?.current
    if (!el) return
    setContentHeight(el.getBoundingClientRect().height)
    const ro = new ResizeObserver(([entry]) => {
      setContentHeight(entry.contentRect.height)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [contentRef])

  if (!contentHeight || !pageConfig) return null

  const { contentHeightPx, marginTopPx, marginBottomPx } = pageConfig

  // ── Line-height snapping ───────────────────────────────────────────────────
  // Snap contentHeightPx DOWN so the seam always starts at a line boundary.
  // Snap betweenPageH UP (via gapH) so visible content after the seam also
  // starts at a line boundary. The gray gap absorbs the rounding difference.

  const lineH = getLineHeightPx(settings)

  const snappedContentH = Math.floor(contentHeightPx / lineH) * lineH

  const nominalBetweenH = marginBottomPx + PAGE_FOOTER_H + PAGE_GAP_H + PAGE_HEADER_H + marginTopPx
  const snappedBetweenH = Math.ceil(nominalBetweenH / lineH) * lineH

  // gapH replaces PAGE_GAP_H in the seam zone — it is >= PAGE_GAP_H.
  const gapH = snappedBetweenH - marginBottomPx - PAGE_FOOTER_H - PAGE_HEADER_H - marginTopPx

  // ── Shared display strings ─────────────────────────────────────────────────
  const displayTitle = book?.shortTitle || book?.title || ''
  const year         = new Date().getFullYear()
  const footerText   = authorFullName
    ? `\u00A9${year} ${authorFullName}. All rights reserved.`
    : `\u00A9${year} All rights reserved.`

  // ── Seam list ──────────────────────────────────────────────────────────────
  // Seam i (0-indexed) starts at:
  //   marginTopPx + (i+1) * snappedContentH + i * snappedBetweenH
  const seams = []
  for (let i = 0; ; i++) {
    const seamTop = marginTopPx + (i + 1) * snappedContentH + i * snappedBetweenH
    if (seamTop >= contentHeight + snappedBetweenH) break
    if (i > 500) break
    const pageNum     = startPage + i + 1
    const headerParts = [authorLastName, displayTitle, `p. ${pageNum}`].filter(Boolean)
    seams.push({ seamTop, pageNum, headerText: headerParts.join(' \u2002/\u2002 ') })
  }

  if (!seams.length) return null

  return (
    <>
      {seams.map(({ seamTop, pageNum, headerText }) => (
        <Box
          key={pageNum}
          aria-hidden="true"
          sx={{
            position:      'absolute',
            top:           `${seamTop}px`,
            left:          0,
            right:         0,
            height:        `${snappedBetweenH}px`,
            pointerEvents: 'none',
            zIndex:        100,
            bgcolor:       'background.paper',
            display:       'flex',
            flexDirection: 'column',
            overflow:      'hidden',
          }}
        >
          {/* Bottom margin of the ending page */}
          <Box sx={{ height: marginBottomPx, flexShrink: 0, bgcolor: 'background.paper' }} />

          {/* Footer of the ending page */}
          <Box
            sx={{
              height:         PAGE_FOOTER_H,
              flexShrink:     0,
              bgcolor:        'action.hover',
              borderTop:      '1px solid',
              borderColor:    'divider',
              display:        'flex',
              alignItems:     'center',
              justifyContent: 'center',
              px:             2,
            }}
          >
            <Typography
              variant="caption"
              sx={{ color: 'text.disabled', userSelect: 'none', fontFamily: 'inherit', letterSpacing: 0.3 }}
            >
              {footerText}
            </Typography>
          </Box>

          {/* Gray gap — gapH is >= PAGE_GAP_H to absorb line-snap rounding */}
          <Box sx={{ height: gapH, flexShrink: 0, bgcolor: 'grey.400' }} />

          {/* Header of the starting page */}
          <Box
            sx={{
              height:         PAGE_HEADER_H,
              flexShrink:     0,
              bgcolor:        'action.hover',
              borderBottom:   '1px solid',
              borderColor:    'divider',
              display:        'flex',
              alignItems:     'center',
              justifyContent: 'flex-end',
              px:             2,
            }}
          >
            <Typography
              variant="caption"
              sx={{ color: 'text.disabled', userSelect: 'none', fontFamily: 'inherit', letterSpacing: 0.3 }}
            >
              {headerText}
            </Typography>
          </Box>

          {/* Top margin of the starting page */}
          <Box sx={{ height: marginTopPx, flexShrink: 0, bgcolor: 'background.paper' }} />
        </Box>
      ))}
    </>
  )
}
