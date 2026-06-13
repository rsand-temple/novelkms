import { useMemo } from 'react'
import {
	Box, Card, CardActionArea, Typography, CircularProgress, Tooltip,
} from '@mui/material'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import { useBooks }   from '../../hooks/useBooks'
import { useProject } from '../../hooks/useProjects'
import { booksApi }   from '../../api/books'

// Thumbnail dimensions — 5:7 aspect ratio approximates a standard novel page.
const THUMB_W = 140
const THUMB_H = 196

/**
 * ProjectShelf
 *
 * Shown in the center panel when a project is selected but no book is open.
 * Renders a grid of book thumbnails; clicking one selects that book.
 *
 * Props:
 *   projectId    — UUID of the active project
 *   onSelectBook — callback(bookId: string) to open a book
 */
export default function ProjectShelf({ projectId, onSelectBook }) {
	const { data: books, isLoading } = useBooks(projectId)
	const { data: project }          = useProject(projectId)

	const authorName = project
		? [project.authorFirstName, project.authorLastName].filter(Boolean).join(' ')
		: ''

	if (isLoading) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
				<CircularProgress size={28} />
			</Box>
		)
	}

	if (!books?.length) {
		return (
			<Box
				sx={{
					flex:           1,
					display:        'flex',
					flexDirection:  'column',
					alignItems:     'center',
					justifyContent: 'center',
					gap:            1,
					color:          'text.disabled',
				}}
			>
				<MenuBookIcon sx={{ fontSize: 40, opacity: 0.3 }} />
				<Typography variant="body2">No books yet — add one from the nav panel.</Typography>
			</Box>
		)
	}

	return (
		<Box
			sx={{
				flex:          1,
				overflowY:     'auto',
				p:             4,
				display:       'flex',
				flexWrap:      'wrap',
				gap:           3,
				alignContent:  'flex-start',
			}}
		>
			{books.map(book => (
				<BookThumb
					key={book.id}
					book={book}
					authorName={authorName}
					onSelect={() => onSelectBook(book.id)}
				/>
			))}
		</Box>
	)
}

// ── BookThumb ─────────────────────────────────────────────────────────────────

function BookThumb({ book, authorName, onSelect }) {
	const imageUrl = book.hasCoverImage
		? `${booksApi.getCoverImageUrl(book.id)}?t=${encodeURIComponent(book.updatedAt ?? '')}`
		: null

	const label = book.title || 'Untitled'

	return (
		<Tooltip title={label} placement="top" enterDelay={500} disableInteractive>
			<Card elevation={3} sx={{ width: THUMB_W, flexShrink: 0 }}>
				<CardActionArea onClick={onSelect}>

					{/* Thumbnail area */}
					<Box
						sx={{
							width:    THUMB_W,
							height:   THUMB_H,
							overflow: 'hidden',
							position: 'relative',
						}}
					>
						{imageUrl ? (
							<Box
								component="img"
								src={imageUrl}
								alt={label}
								sx={{
									position:  'absolute',
									inset:     0,
									width:     '100%',
									height:    '100%',
									objectFit: 'cover',
									display:   'block',
								}}
							/>
						) : (
							<CoverStub book={book} authorName={authorName} />
						)}
					</Box>

					{/* Title caption below the thumbnail */}
					<Box sx={{ px: 1.25, py: 0.75, borderTop: '1px solid', borderColor: 'divider' }}>
						<Typography
							variant="caption"
							noWrap
							sx={{ display: 'block', fontWeight: 600, fontSize: '0.72rem' }}
						>
							{label}
						</Typography>
						{book.subtitle && (
							<Typography
								variant="caption"
								noWrap
								sx={{
									display:   'block',
									color:     'text.secondary',
									fontSize:  '0.65rem',
									fontStyle: 'italic',
								}}
							>
								{book.subtitle}
							</Typography>
						)}
					</Box>

				</CardActionArea>
			</Card>
		</Tooltip>
	)
}

// ── CoverStub ─────────────────────────────────────────────────────────────────

/**
 * Rendered when no cover image is uploaded.
 * Uses a deterministic hue derived from the book's ID so each book gets a
 * distinct but consistent colour across renders and sessions.
 */
function CoverStub({ book, authorName }) {
	const hue = useMemo(() => {
		let h = 0
		const s = book.id || book.title || ''
		for (let i = 0; i < s.length; i++) {
			h = (h * 31 + s.charCodeAt(i)) & 0xffff
		}
		return h % 360
	}, [book.id, book.title])

	return (
		<Box
			sx={{
				width:          '100%',
				height:         '100%',
				display:        'flex',
				flexDirection:  'column',
				alignItems:     'center',
				justifyContent: 'center',
				gap:            0.75,
				p:              1.5,
				background:     `linear-gradient(160deg, hsl(${hue},35%,28%) 0%, hsl(${hue},25%,18%) 100%)`,
			}}
		>
			<Typography
				sx={{
					color:      `hsl(${hue}, 25%, 90%)`,
					fontSize:   '0.78rem',
					fontWeight: 700,
					textAlign:  'center',
					lineHeight: 1.25,
					wordBreak:  'break-word',
				}}
			>
				{book.title || 'Untitled'}
			</Typography>

			{book.subtitle && (
				<Typography
					sx={{
						color:      `hsl(${hue}, 20%, 70%)`,
						fontSize:   '0.6rem',
						fontStyle:  'italic',
						textAlign:  'center',
						lineHeight: 1.2,
						wordBreak:  'break-word',
					}}
				>
					{book.subtitle}
				</Typography>
			)}

			{authorName && (
				<Typography
					sx={{
						color:     `hsl(${hue}, 15%, 62%)`,
						fontSize:  '0.58rem',
						textAlign: 'center',
						mt:        'auto',
						wordBreak: 'break-word',
					}}
				>
					{authorName}
				</Typography>
			)}
		</Box>
	)
}
