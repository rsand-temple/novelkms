import { useMemo } from 'react'
import {
	Alert,
	Box,
	Button,
	Checkbox,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	FormControlLabel,
	List,
	ListItem,
	Typography,
} from '@mui/material'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import { useCodexAiContext, useSetScenePinned, useSetCategoryPinned } from '../../hooks/useAiContext'

/**
 * ManageAiContextDialog
 *
 * Single source of truth for which Codex entries are shared with the AI as
 * reference context. Opened from the Codex container's right-click menu. Lists
 * every entry grouped by category, each with a checkbox; a category header
 * checkbox bulk-toggles its entries; a running total shows how much is shared.
 *
 * Nothing is shared by default — this is how a detailed worldbuilder includes a
 * handful of canon/voice entries without dumping the whole Codex at the model.
 *
 * Props:
 *   open     {boolean}
 *   onClose  {() => void}
 *   codexId  {string|null}
 *   title    {string}        — codex title for the dialog header
 */
export default function ManageAiContextDialog({ open, onClose, codexId, title }) {
	const { data, isLoading, isError } = useCodexAiContext(codexId, open)
	const { mutate: setScenePinned } = useSetScenePinned()
	const { mutate: setCategoryPinned } = useSetCategoryPinned()

	const entries = data?.entries ?? []

	// Live totals derived from the current entries, so the header updates as soon
	// as a refetch lands after a toggle.
	const { pinnedCount, pinnedWords } = useMemo(() => {
		let c = 0, w = 0
		for (const e of entries) {
			if (e.pinned) { c += 1; w += e.wordCount ?? 0 }
		}
		return { pinnedCount: c, pinnedWords: w }
	}, [entries])

	// Group entries by their category chapter, preserving server order.
	const groups = useMemo(() => {
		const byChapter = new Map()
		for (const e of entries) {
			if (!byChapter.has(e.chapterId)) {
				byChapter.set(e.chapterId, {
					chapterId: e.chapterId,
					label: e.categoryTitle?.trim() || e.category || 'Category',
					items: [],
				})
			}
			byChapter.get(e.chapterId).items.push(e)
		}
		return [...byChapter.values()]
	}, [entries])

	const anyPinned = pinnedCount > 0

	const handleEntryToggle = (entry) => {
		setScenePinned({
			sceneId: entry.sceneId,
			chapterId: entry.chapterId,
			codexId,
			pinned: !entry.pinned,
		})
	}

	const handleCategoryToggle = (group, pinned) => {
		setCategoryPinned({ chapterId: group.chapterId, codexId, pinned })
	}

	const handleClearAll = () => {
		// Bulk-unpin each category that currently has any pinned entry.
		for (const g of groups) {
			if (g.items.some(e => e.pinned)) {
				setCategoryPinned({ chapterId: g.chapterId, codexId, pinned: false })
			}
		}
	}

	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
				<AutoAwesomeIcon fontSize="small" color="primary" />
				Manage AI Context — {title || 'Codex'}
			</DialogTitle>

			<DialogContent dividers>
				<Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
					Shared entries are sent to the AI as established canon and voice the manuscript
					must respect during chapter and scene reviews. Nothing is shared until you opt it in.
				</Typography>

				<Box sx={{ mb: 1.5 }}>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>
						{anyPinned
							? `Shared: ${pinnedCount} ${pinnedCount === 1 ? 'entry' : 'entries'}, ~${pinnedWords} ${pinnedWords === 1 ? 'word' : 'words'}`
							: 'Nothing shared yet'}
					</Typography>
				</Box>

				{isLoading ? (
					<Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
						<CircularProgress size={22} />
					</Box>
				) : isError ? (
					<Alert severity="error">The Codex entries could not be loaded.</Alert>
				) : entries.length === 0 ? (
					<Alert severity="info">This Codex has no entries yet.</Alert>
				) : (
					groups.map((group) => {
						const total = group.items.length
						const pinned = group.items.filter(e => e.pinned).length
						const allPinned = pinned === total
						const somePinned = pinned > 0 && pinned < total
						return (
							<Box key={group.chapterId} sx={{ mb: 1.5 }}>
								<FormControlLabel
									control={
										<Checkbox
											size="small"
											checked={allPinned}
											indeterminate={somePinned}
											onChange={(e) => handleCategoryToggle(group, e.target.checked)}
										/>
									}
									label={
										<Typography variant="subtitle2" sx={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>
											{group.label} ({pinned}/{total})
										</Typography>
									}
								/>
								<List dense disablePadding sx={{ pl: 3 }}>
									{group.items.map((entry) => (
										<ListItem key={entry.sceneId} disableGutters sx={{ py: 0 }}>
											<FormControlLabel
												sx={{ m: 0 }}
												control={
													<Checkbox
														size="small"
														checked={!!entry.pinned}
														onChange={() => handleEntryToggle(entry)}
													/>
												}
												label={
													<Typography variant="body2">
														{entry.title?.trim() || 'Untitled'}
														{entry.wordCount ? (
															<Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.75 }}>
																~{entry.wordCount} {entry.wordCount === 1 ? 'word' : 'words'}
															</Typography>
														) : null}
													</Typography>
												}
											/>
										</ListItem>
									))}
								</List>
								<Divider sx={{ mt: 1 }} />
							</Box>
						)
					})
				)}
			</DialogContent>

			<DialogActions>
				<Button onClick={handleClearAll} disabled={!anyPinned} color="inherit">
					Clear all
				</Button>
				<Box sx={{ flexGrow: 1 }} />
				<Button onClick={onClose} variant="contained">Done</Button>
			</DialogActions>
		</Dialog>
	)
}
