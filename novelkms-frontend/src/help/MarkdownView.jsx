import { useCallback } from 'react'
import { Box } from '@mui/material'
import { renderMarkdown } from './miniMarkdown'

/**
 * MarkdownView — renders a help topic's markdown body to styled HTML and
 * intercepts in-content cross-links.
 *
 * Links written as [label](#help:topic.id) are caught here via event
 * delegation and routed through onNavigate instead of changing the browser
 * URL. Ordinary http(s) links open in a new tab (handled in miniMarkdown).
 *
 * dangerouslySetInnerHTML is safe: the content is build-time-bundled markdown
 * authored in this repository (not user input), and miniMarkdown HTML-escapes
 * all text before emitting structural tags — the same trust posture as
 * RichTextPreview.
 *
 * Props:
 *   markdown    {string}              raw markdown body
 *   onNavigate  {(id: string)=>void}  called when a #help: link is clicked
 */
export default function MarkdownView({ markdown, onNavigate }) {
	const handleClick = useCallback(
		(event) => {
			const anchor = event.target.closest?.('a[href^="#help:"]')
			if (!anchor) return
			event.preventDefault()
			const id = anchor.getAttribute('href').slice('#help:'.length)
			if (id && onNavigate) onNavigate(id)
		},
		[onNavigate],
	)

	const html = renderMarkdown(markdown)

	return (
		<Box
			onClick={handleClick}
			sx={{
				fontSize: '0.95rem',
				lineHeight: 1.65,
				color: 'text.primary',
				'& h1': {
					fontFamily: 'Georgia, "Times New Roman", serif',
					fontSize: '1.5rem',
					fontWeight: 600,
					mt: 0,
					mb: 1.5,
				},
				'& h2': {
					fontFamily: 'Georgia, "Times New Roman", serif',
					fontSize: '1.2rem',
					fontWeight: 600,
					mt: 3,
					mb: 1,
				},
				'& h3': {
					fontSize: '1.02rem',
					fontWeight: 700,
					mt: 2.5,
					mb: 0.75,
				},
				'& p': { mt: 0, mb: 1.5 },
				'& ul, & ol': { pl: 3, mt: 0, mb: 1.5 },
				'& li': { mb: 0.5 },
				'& a': {
					color: 'primary.main',
					textDecoration: 'none',
					fontWeight: 600,
					'&:hover': { textDecoration: 'underline' },
				},
				'& code': {
					fontFamily: '"SFMono-Regular", "Consolas", "Liberation Mono", monospace',
					fontSize: '0.85em',
					bgcolor: 'rgba(30, 48, 80, 0.07)',
					px: 0.5,
					py: 0.1,
					borderRadius: 0.5,
				},
				'& pre': {
					bgcolor: 'rgba(30, 48, 80, 0.06)',
					border: '1px solid',
					borderColor: 'divider',
					borderRadius: 1,
					p: 1.5,
					overflow: 'auto',
					mb: 1.5,
					'& code': { bgcolor: 'transparent', p: 0, fontSize: '0.85rem' },
				},
				'& blockquote': {
					borderLeft: '3px solid',
					borderColor: 'secondary.main',
					pl: 1.75,
					ml: 0,
					my: 1.5,
					color: 'text.secondary',
				},
				'& hr': {
					border: 0,
					borderTop: '1px solid',
					borderColor: 'divider',
					my: 2.5,
				},
				'& strong': { fontWeight: 700 },
			}}
			dangerouslySetInnerHTML={{ __html: html }}
		/>
	)
}
