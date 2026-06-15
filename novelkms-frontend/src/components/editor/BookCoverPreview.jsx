import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Typography } from '@mui/material'

import { useBookTemplate, useDeleteBookTemplate } from '../../hooks/useTemplates'
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
 * An amber warning badge appears between the two pages when the book has a
 * BOOK-scope template override (meaning it is no longer inheriting from the
 * global cover template).  A "Reset to global" button in the badge deletes the
 * BOOK override so the book reverts to the global default.  A two-step
 * confirmation flow prevents accidental resets.
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
	const { data: coverTemplate } = useBookTemplate(bookId, 'cover', !!bookId)
	const { data: styleSheet } = useBookStyles(bookId, !!bookId)
	const { mutate: deleteBookTemplate, isPending: isResetting } = useDeleteBookTemplate()

	// Two-step confirmation: first click shows a confirm prompt in the badge;
	// second click (Confirm) fires the mutation.  Navigating away or cancelling
	// resets the flow.
	const [confirmingReset, setConfirmingReset] = useState(false)

	const handleResetClick = () => setConfirmingReset(true)
	const handleResetCancel = () => setConfirmingReset(false)
	const handleResetConfirm = () => {
		deleteBookTemplate({ bookId, type: 'cover' })
		setConfirmingReset(false)
	}

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

	// Whether this book is using a book-specific override (vs the global default).
	// scope is 'BOOK' when an override row exists; 'GLOBAL' when resolveForBook
	// fell back to the global template.
	const hasBookOverride = coverTemplate?.scope === 'BOOK'

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

			{/* ── Override detection badge ──────────────────────────────────── */}
			{/*
			    Shown when the book has a BOOK-scope template that is shadowing
			    the global.  This makes the otherwise-invisible fork discoverable
			    and provides a one-click (confirmed) path back to the global.

			    Not shown when scope === 'GLOBAL' — that is the normal/default
			    state and needs no annotation.
			*/}
			{hasBookOverride && (
				<Box sx={{ width: pageConfig.widthPx }}>
					{!confirmingReset ? (
						<Alert
							severity="warning"
							action={
								<Button
									size="small"
									color="inherit"
									disabled={isResetting}
									onClick={handleResetClick}
								>
									Reset to global →
								</Button>
							}
						>
							This book has a template override — edits here are book-specific
							and won't affect other books. The global cover template is not
							used for this book.
						</Alert>
					) : (
						<Alert
							severity="error"
							action={
								<Box sx={{ display: 'flex', gap: 1 }}>
									<Button
										size="small"
										color="inherit"
										onClick={handleResetCancel}
									>
										Cancel
									</Button>
									<Button
										size="small"
										color="inherit"
										disabled={isResetting}
										onClick={handleResetConfirm}
										sx={{ fontWeight: 700 }}
									>
										Delete override
									</Button>
								</Box>
							}
						>
							Delete this book's template override? The global cover template
							will be used instead, and any book-specific edits will be lost.
						</Alert>
					)}
				</Box>
			)}

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