import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Box, Typography } from '@mui/material'

import { useBookTemplate } from '../../hooks/useTemplates'
import { useBookStyles } from '../../hooks/useStyles'
import { resolveValues, renderPreviewHtml } from '../../utils/tokenUtils'
import { buildStyleSx } from '../../utils/styles'
import { booksApi } from '../../api/books'
import client from '../../api/client'

/**
 * BookCoverPreview
 *
 * Renders the two-page book cover in the page-layout canvas:
 *
 *   Page 1 — full-bleed cover image (or a placeholder when none is uploaded).
 *             No margins, no header, no footer — the image fills the entire
 *             page surface.
 *   Page 2 — cover template with tokens resolved against the real book/project
 *             data, read-only.  Margins are applied; no running header/footer
 *             (cover pages are never paginated in standard publishing).
 *
 * Shown when the user clicks a book in the nav tree and the book has page
 * layout enabled, with no chapter/scene/template active.
 *
 * Props:
 *   bookId    — UUID of the selected book
 *   book      — Book record from useBook()
 *   project   — Project record from useProject()
 *   pageConfig — derived from derivePageConfig(book); never null here
 *   settings  — project settings from useProjectSettings()
 */
export default function BookCoverPreview({ bookId, book, project, pageConfig, settings, embedded = false }) {
	const { data: coverTemplate } = useBookTemplate(bookId, 'COVER', !!bookId)
	const { data: styleSheet } = useBookStyles(bookId, !!bookId)

	// Fetch the total project word count so the WORDS token resolves correctly
	// in the cover template preview (e.g. "About 87,432 words").
	const { data: wordCountData } = useQuery({
		queryKey: ['projects', project?.id, 'word-count'],
		queryFn: () => client.get(`/projects/${project.id}/word-count`).then(r => r.data),
		enabled: !!project?.id,
	})
	const wordCount = wordCountData?.wordCount ?? null

	// Resolve template tokens against real book/project data.
	const values = useMemo(
		() => resolveValues({ scope: 'book', book, project, wordCount }),
		[book, project, wordCount]
	)

	const previewHtml = useMemo(
		() => renderPreviewHtml(coverTemplate?.content, values),
		[coverTemplate, values]
	)

	const styleSx = useMemo(() => buildStyleSx(styleSheet), [styleSheet])

	// Cache-bust the image URL whenever the book record changes (setCoverImage
	// and deleteCoverImage both bump updated_at, which flows back via the
	// BOOK_KEYS.detail invalidation).
	const imageUrl = booksApi.getCoverImageUrl(bookId) +
		`?t=${encodeURIComponent(book?.updatedAt ?? '')}`

	// Shared page "paper" style — white rectangle at the book's exact dimensions.
	const paperSx = {
		width: pageConfig.widthPx,
		height: pageConfig.heightPx,
		bgcolor: 'background.paper',
		flexShrink: 0,
		boxShadow: 3,
		overflow: 'hidden',
		position: 'relative',
	}

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
			{/* ── Page 1: Cover image (full bleed) ─────────────────────────── */}
			<Box sx={paperSx}>
				{book?.hasCoverImage ? (
					<Box
						component="img"
						src={imageUrl}
						alt="Book cover"
						sx={{
							position: 'absolute',
							top: 0,
							left: 0,
							width: '100%',
							height: '100%',
							objectFit: 'cover',
							display: 'block',
						}}
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
							gap: 1,
							color: 'text.disabled',
						}}
					>
						<Typography variant="body2">No cover image</Typography>
						<Typography variant="caption">
							Upload one in Book Properties →
						</Typography>
					</Box>
				)}
			</Box>

			{/* ── Page 2: Cover template (read-only, tokens resolved) ───────── */}
			<Box
				sx={{
					...paperSx,
					// Margins applied; no running header/footer on a cover page.
					pt: `${pageConfig.marginTopPx}px`,
					pb: `${pageConfig.marginBottomPx}px`,
					pl: `${pageConfig.marginInnerPx}px`,
					pr: `${pageConfig.marginOuterPx}px`,

					// Project settings as CSS variables so style definitions can
					// reference them, matching the live editor's rendering context.
					'--nkms-font-family': settings.fontFamily,
					'--nkms-font-size': settings.fontSize,
					'--nkms-line-height': settings.lineHeight,
					'--nkms-text-indent': '0px',   // cover pages: no first-line indent
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
				<Box
					className="tiptap"
					dangerouslySetInnerHTML={{ __html: previewHtml || '<p></p>' }}
				/>
			</Box>
		</Box>
	)
}