import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { PAGE_FOOTER_H } from '../../utils/pageConfig'

/**
 * PageFooter
 *
 * Non-editable footer shown at the very bottom of the page canvas (last page).
 * Interior page footers are rendered by PageRulerOverlay seam zones.
 *
 * Height is fixed to PAGE_FOOTER_H so PageRulerOverlay can use the same
 * constant when computing seam zone positions.
 */
export default function PageFooter({ authorFullName }) {
  const year = new Date().getFullYear()
  const name = authorFullName || ''
  const text = name
    ? `\u00A9${year} ${name}. All rights reserved.`
    : `\u00A9${year} All rights reserved.`

  return (
    <Box
      aria-hidden="true"
      sx={{
        height:         PAGE_FOOTER_H,
        flexShrink:     0,
        display:        'flex',
        justifyContent: 'center',
        alignItems:     'center',
        px:             2,
        borderTop:      '1px solid',
        borderColor:    'divider',
        bgcolor:        'action.hover',
        color:          'text.disabled',
        userSelect:     'none',
        pointerEvents:  'none',
      }}
    >
      <Typography variant="caption" sx={{ fontFamily: 'inherit', letterSpacing: 0.3 }}>
        {text}
      </Typography>
    </Box>
  )
}
