import { IconButton, Tooltip } from '@mui/material'
import { useHelp } from './HelpProvider'

/**
 * HelpButton — the single reusable context-help control.
 *
 * Drop one next to any control or in any dialog title:
 *
 *   <HelpButton topic="ai.review.rail" />
 *   <HelpButton topic="editor.templates" label="About templates" size="medium" />
 *
 * It renders a small circular "?" affordance (a styled glyph rather than an
 * icon-font import, so it can never break the build on a missing icon) and
 * opens the Help Center straight to `topic`.
 *
 * Props:
 *   topic  {string}  required — the topic id to open (validated by scripts/check-help.mjs)
 *   label  {string}  tooltip text (default "Help")
 *   size   {'small'|'medium'}  icon button size (default 'small')
 *   sx     {object}  extra styles merged onto the IconButton
 */
export default function HelpButton({ topic, label = 'Help', size = 'small', sx }) {
	const { openHelp } = useHelp()

	const dim = size === 'medium' ? 22 : 18
	const font = size === 'medium' ? '0.82rem' : '0.7rem'

	return (
		<Tooltip title={label}>
			<IconButton
				size={size}
				aria-label={label}
				onClick={(event) => {
					event.stopPropagation()
					openHelp(topic)
				}}
				sx={{
					p: 0,
					width: dim,
					height: dim,
					color: 'text.secondary',
					border: '1.5px solid',
					borderColor: 'currentColor',
					fontSize: font,
					fontWeight: 700,
					lineHeight: 1,
					'&:hover': { color: 'primary.main', bgcolor: 'transparent' },
					...sx,
				}}
			>
				?
			</IconButton>
		</Tooltip>
	)
}
