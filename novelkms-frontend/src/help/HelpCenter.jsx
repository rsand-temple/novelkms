import { useMemo, useState } from 'react'
import {
	Box, Dialog, IconButton, InputAdornment, List, ListItemButton,
	ListItemText, TextField, Tooltip, Typography, Divider,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import CloseIcon from '@mui/icons-material/Close'
import SearchIcon from '@mui/icons-material/Search'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import { useHelp } from './HelpProvider'
import { getTableOfContents, getTopic, searchTopics } from './helpRegistry'
import MarkdownView from './MarkdownView'

/**
 * HelpCenter — the master help surface (D2: centered modal).
 *
 * Left pane: the table of contents, grouped by section (helpSections.js order),
 * with a live search filter over titles + body. Right pane: the selected topic
 * rendered from markdown, with in-content #help: cross-links routed back through
 * the provider so a Back button can retrace the trail.
 *
 * Mounted once near the app root; visibility/topic come entirely from useHelp().
 */
export default function HelpCenter() {
	const { isOpen, topicId, canGoBack, close, navigate, back } = useHelp()
	const [query, setQuery] = useState('')

	const toc = useMemo(() => getTableOfContents(), [])
	const results = useMemo(() => (query.trim() ? searchTopics(query) : null), [query])
	const topic = topicId ? getTopic(topicId) : null

	function go(id) {
		setQuery('')
		navigate(id)
	}

	return (
		<Dialog
			open={isOpen}
			onClose={close}
			maxWidth="lg"
			fullWidth
			slotProps={{ paper: { sx: { height: '82vh', maxHeight: 760 } } }}
		>
			<Box sx={{ display: 'flex', height: '100%', minHeight: 0 }}>
				{/* TOC / search pane */}
				<Box
					sx={{
						width: 280,
						flexShrink: 0,
						borderRight: '1px solid',
						borderColor: 'divider',
						bgcolor: 'background.default',
						display: 'flex',
						flexDirection: 'column',
						minHeight: 0,
					}}
				>
					<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1.5 }}>
						<MenuBookIcon fontSize="small" color="primary" />
						<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
							Help
						</Typography>
					</Box>
					<Box sx={{ px: 1.5, pb: 1 }}>
						<TextField
							size="small"
							fullWidth
							placeholder="Search help…"
							value={query}
							onChange={(e) => setQuery(e.target.value)}
							slotProps={{
								input: {
									startAdornment: (
										<InputAdornment position="start">
											<SearchIcon fontSize="small" />
										</InputAdornment>
									),
								},
							}}
						/>
					</Box>
					<Divider />
					<Box sx={{ overflowY: 'auto', flex: 1, minHeight: 0, py: 0.5 }}>
						{results ? (
							results.length === 0 ? (
								<Typography
									variant="body2"
									color="text.secondary"
									sx={{ px: 2, py: 1.5, fontStyle: 'italic' }}
								>
									No matches for “{query.trim()}”.
								</Typography>
							) : (
								<List dense disablePadding>
									{results.map((t) => (
										<ListItemButton
											key={t.id}
											selected={t.id === topicId}
											onClick={() => go(t.id)}
										>
											<ListItemText
												primary={t.title}
												slotProps={{ primary: { sx: { fontSize: '0.875rem' } } }}
											/>
										</ListItemButton>
									))}
								</List>
							)
						) : (
							toc.map((section) => (
								<Box key={section.id} sx={{ mb: 0.5 }}>
									<Typography
										variant="overline"
										sx={{
											display: 'block',
											px: 2,
											pt: 1,
											pb: 0.25,
											color: 'text.secondary',
											fontWeight: 700,
											letterSpacing: 0.6,
										}}
									>
										{section.title}
									</Typography>
									<List dense disablePadding>
										{section.topics.map((t) => (
											<ListItemButton
												key={t.id}
												selected={t.id === topicId}
												onClick={() => go(t.id)}
												sx={{ py: 0.4 }}
											>
												<ListItemText
													primary={t.title}
													slotProps={{ primary: { sx: { fontSize: '0.875rem' } } }}
												/>
											</ListItemButton>
										))}
									</List>
								</Box>
							))
						)}
					</Box>
				</Box>

				{/* Content pane */}
				<Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: 0 }}>
					<Box
						sx={{
							display: 'flex',
							alignItems: 'center',
							gap: 0.5,
							px: 2,
							py: 1,
							borderBottom: '1px solid',
							borderColor: 'divider',
							flexShrink: 0,
						}}
					>
						<Tooltip title="Back">
							<span>
								<IconButton size="small" onClick={back} disabled={!canGoBack}>
									<ArrowBackIcon fontSize="small" />
								</IconButton>
							</span>
						</Tooltip>
						<Typography variant="subtitle1" sx={{ fontWeight: 700, flex: 1, minWidth: 0 }} noWrap>
							{topic?.title ?? 'Help'}
						</Typography>
						<IconButton size="small" onClick={close} aria-label="Close help">
							<CloseIcon fontSize="small" />
						</IconButton>
					</Box>
					<Box sx={{ overflowY: 'auto', flex: 1, minHeight: 0, px: 4, py: 3 }}>
						{topic ? (
							<MarkdownView markdown={topic.body} onNavigate={go} />
						) : (
							<Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
								Select a topic from the list to begin.
							</Typography>
						)}
					</Box>
				</Box>
			</Box>
		</Dialog>
	)
}
