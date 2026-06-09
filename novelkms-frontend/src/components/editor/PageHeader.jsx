import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { PAGE_HEADER_H } from '../../utils/pageConfig'

/**
 * PageHeader
 *
 * Non-editable header shown at the very top of the page canvas (page 1).
 * Interior page headers are rendered by PageRulerOverlay seam zones.
 *
 * Height is fixed to PAGE_HEADER_H so PageRulerOverlay can use the same
 * constant when computing seam zone positions.
 */
export default function PageHeader({ book, authorLastName, startPage = 1 }) {
  const displayTitle = book?.shortTitle || book?.title || ''
  const parts = [authorLastName || null, displayTitle || null, `p. ${startPage}`].filter(Boolean)
  const headerText = parts.join(' \u2002/\u2002 ')

  return (
    <Box
      aria-hidden="true"
      sx={{
        height:         PAGE_HEADER_H,
        flexShrink:     0,
        display:        'flex',
        justifyContent: 'flex-end',
        alignItems:     'center',
        px:             2,
        borderBottom:   '1px solid',
        borderColor:    'divider',
        bgcolor:        'action.hover',
        color:          'text.disabled',
        userSelect:     'none',
        pointerEvents:  'none',
      }}
    >
      <Typography variant="caption" sx={{ fontFamily: 'inherit', letterSpacing: 0.3 }}>
        {headerText}
      </Typography>
    </Box>
  )
}
