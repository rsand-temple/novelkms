import { Box, Typography } from '@mui/material'

/**
 * RichTextPreview — read-only render of authored HTML (memory docs, chapter/book
 * summaries) for the "peek" surfaces kept alongside the real editing surface
 * (the document's own nav node in EditorPanel). Renders the actual markup
 * rather than a plain-text dump, with a minimal CSS subset mirroring
 * EditorPanel's own template-preview rendering.
 *
 * dangerouslySetInnerHTML is safe here: this only ever renders content the
 * current authenticated user already authored and saved themselves (same
 * trust boundary as EditorPanel's existing template preview).
 *
 * Props:
 *   html  {string}  authored HTML content
 */
export default function RichTextPreview({ html }) {
	if (!html?.trim()) {
		return (
			<Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
				Nothing here yet.
			</Typography>
		)
	}

	return (
		<Box
			sx={{
				fontSize: '0.875rem',
				lineHeight: 1.6,
				'& p': { mt: 0, mb: 1 },
				'& p:last-child': { mb: 0 },
				'& ul, & ol': { pl: 3, mb: 1 },
				'& blockquote': {
					borderLeft: '3px solid',
					borderColor: 'divider',
					pl: 1.5,
					ml: 0,
					color: 'text.secondary',
					fontStyle: 'italic',
				},
				'& h1, & h2, & h3': { fontWeight: 700, mt: 1, mb: 0.5 },
			}}
			dangerouslySetInnerHTML={{ __html: html }}
		/>
	)
}
