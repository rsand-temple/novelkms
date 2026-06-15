import { useMemo } from 'react'
import { Box, Typography } from '@mui/material'

import { usePart, useParts } from '../../hooks/useParts'
import { useBookTemplate } from '../../hooks/useTemplates'
import { useBookStyles } from '../../hooks/useStyles'
import { resolveValuesForPart, renderPreviewHtml } from '../../utils/tokenUtils'
import { buildStyleSx } from '../../utils/styles'

/**
 * PartPagePreview
 *
 * Renders the part page for a specific part as a read-only page-layout canvas.
 * The PART template is resolved with real values for this part: PART_NUMBER
 * (ordinal word derived from position among all parts in the book), PART_TITLE,
 * PART_SUBTITLE, and all book/project tokens.
 *
 * Shown when the user clicks a part in the nav tree and the book has page
 * layout enabled, with no chapter/scene/template active.
 *
 * Props:
 *   partId    — UUID of the selected part
 *   bookId    — UUID of the book containing the part
 *   book      — Book record (already fetched by EditorPanel)
 *   project   — Project record (already fetched by EditorPanel)
 *   pageConfig — derived from derivePageConfig(book); never null here
 *   settings  — project settings from useProjectSettings()
 */
export default function PartPagePreview({ partId, bookId, book, project, pageConfig, settings, embedded = false }) {
	// Individual part record for title/subtitle tokens.
	const { data: part } = usePart(partId)

	// All parts for the book — needed to compute this part's ordinal position.
	const { data: parts } = useParts(bookId)

	// 1-based position of this part within the book (sorted by display_order,
	// which is the order useParts returns them).
	const partNumber = useMemo(() => {
		if (!parts || !partId) return null
		const idx = parts.findIndex(p => p.id === partId)
		return idx >= 0 ? idx + 1 : null
	}, [parts, partId])

	const { data: partTemplate } = useBookTemplate(bookId, 'PART', !!bookId)
	const { data: styleSheet } = useBookStyles(bookId, !!bookId)

	const values = useMemo(
		() => resolveValuesForPart({ part, partNumber, book, project }),
		[part, partNumber, book, project]
	)

	const previewHtml = useMemo(
		() => renderPreviewHtml(partTemplate?.content, values),
		[partTemplate, values]
	)

	const styleSx = useMemo(() => buildStyleSx(styleSheet), [styleSheet])

	return (
		<Box
			sx={{
				flex: embedded ? '0 0 auto' : 1,
				overflowY: embedded ? 'visible' : 'auto',
				bgcolor: embedded ? 'transparent' : 'grey.400',
				display: 'flex',
				flexDirection: 'column',
				alignItems: 'center',
				gap: 4,
				py: 4,
			}}
		>
			{/* Single page — part template with tokens resolved */}
			<Box
				sx={{
					width: pageConfig.widthPx,
					height: pageConfig.heightPx,
					bgcolor: 'background.paper',
					flexShrink: 0,
					boxShadow: 3,
					overflow: 'hidden',
					position: 'relative',

					// Margins applied; part pages carry no running header/footer.
					pt: `${pageConfig.marginTopPx}px`,
					pb: `${pageConfig.marginBottomPx}px`,
					pl: `${pageConfig.marginInnerPx}px`,
					pr: `${pageConfig.marginOuterPx}px`,

					// Project settings as CSS variables — matches the live editor's
					// rendering context so the preview is visually identical.
					'--nkms-font-family': settings.fontFamily,
					'--nkms-font-size': settings.fontSize,
					'--nkms-line-height': settings.lineHeight,
					'--nkms-text-indent': '0px',  // part pages: no first-line indent
					'--nkms-spacing-after': settings.spacingAfter,

					'& p': {
						textIndent: 'var(--nkms-text-indent)',
						marginBottom: 'var(--nkms-spacing-after)',
						marginTop: 0,
						fontFamily: 'var(--nkms-font-family)',
						fontSize: 'var(--nkms-font-size)',
						lineHeight: 'var(--nkms-line-height)',
					},
					'& h1': { fontSize: '1.6rem', fontWeight: 700, mt: 2, mb: 0.5 },
					'& h2': { fontSize: '1.3rem', fontWeight: 700, mt: 2, mb: 0.5 },
					'& h3': { fontSize: '1.1rem', fontWeight: 600, mt: 1.5, mb: 0.5 },

					// Per-style overrides from the resolved stylesheet cascade.
					...styleSx,
				}}
			>
				{partTemplate ? (
					<Box
						className="tiptap"
						dangerouslySetInnerHTML={{ __html: previewHtml || '<p></p>' }}
					/>
				) : (
					<Box
						sx={{
							width: '100%',
							height: '100%',
							display: 'flex',
							flexDirection: 'column',
							alignItems: 'center',
							justifyContent: 'center',
							color: 'text.disabled',
							gap: 1,
						}}
					>
						<Typography variant="body2">No part template defined</Typography>
						<Typography variant="caption">
							Edit one via Templates → Part Page
						</Typography>
					</Box>
				)}
			</Box>
		</Box>
	)
}