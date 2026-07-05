import { useMemo, useState } from 'react'
import {
	Box,
	Button,
	Chip,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	IconButton,
	Menu,
	MenuItem,
	Paper,
	TextField,
	Tooltip,
	Typography,
} from '@mui/material'
import {
	CATEGORY_OPTIONS,
	codexLabel,
	defaultTitle,
	normalizeCategory,
	normalizeStatus,
	recommendationToText,
	priorityChipStyles,
	statusMeta,
	STATUS,
} from './recommendationUtils'

/**
 * ReviewCard — one AI recommendation in the editor review rail.
 *
 * Lifecycle is bug-tracker style; the face actions depend on the finding's
 * current status:
 *
 *   OPEN      → [Mark Done] [Dismiss] [Defer] [Codex]
 *   DEFERRED  → [Mark Done] [Dismiss] [Reopen] [Codex]
 *   DONE      → [Reopen] [Codex]
 *   DISMISSED → [Reopen]
 *   PROMOTED  → (inert — promoted to Codex, no lifecycle actions)
 *
 * Overflow (⋯) menu: Add to Codex…, Copy note. There is no per-finding hard
 * delete — Dismiss covers "make it go away," and whole-review deletion goes
 * through the History tab (Trash).
 *
 * Props:
 *   rec                  recommendation record
 *   onSetStatus(rec, s)  set lifecycle status (OPEN|DONE|DISMISSED|DEFERRED)
 *   onPromote(rec, cat, title, note)  promote to codex
 *   promoting            boolean — this card's promote is in flight
 *   onHighlight(anchorText) — scroll the editor to the quoted passage
 */
export default function ReviewCard({
	rec,
	onSetStatus,
	onPromote,
	promoting,
	onHighlight,
}) {
	const initialCategory = useMemo(() => normalizeCategory(rec.codexCategory), [rec.codexCategory])

	const [menuAnchor, setMenuAnchor] = useState(null)
	const menuOpen = Boolean(menuAnchor)
	const [addOpen, setAddOpen] = useState(false)
	const [draftTitle, setDraftTitle] = useState(() => defaultTitle(rec))
	const [draftCategory, setDraftCategory] = useState(initialCategory)
	const [draftNote, setDraftNote] = useState('')
	const [copied, setCopied] = useState(false)

	const status = normalizeStatus(rec.status)
	const meta = statusMeta(status)

	// State-aware lifecycle actions.
	const showDone = status === STATUS.OPEN || status === STATUS.DEFERRED
	const showDismiss = status === STATUS.OPEN || status === STATUS.DEFERRED
	const showDefer = status === STATUS.OPEN
	const showReopen = status !== STATUS.OPEN && status !== STATUS.PROMOTED

	// Promotion is meaningless once dismissed or already promoted.
	const addDisabled = status === STATUS.DISMISSED || status === STATUS.PROMOTED
	const showCodex = !addDisabled

	const closeMenu = () => setMenuAnchor(null)

	const openAddDialog = () => {
		closeMenu()
		setDraftTitle(defaultTitle(rec))
		setDraftCategory(initialCategory)
		setDraftNote(rec.recommendation ?? '')
		setAddOpen(true)
	}

	const confirmAdd = () => {
		const title = draftTitle.trim() || defaultTitle(rec)
		const note  = draftNote.trim()
		setAddOpen(false)
		onPromote(rec, draftCategory, title, note)
	}

	const copyNote = async () => {
		closeMenu()
		try {
			await navigator.clipboard.writeText(recommendationToText(rec))
			setCopied(true)
			window.setTimeout(() => setCopied(false), 1200)
		} catch {
			// Clipboard may be unavailable (insecure context); fail quietly.
		}
	}

	return (
		<Paper variant="outlined" sx={{ p: 1.25, mb: 1 }}>
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5, flexWrap: 'wrap' }}>
				<Typography variant="caption" color="text.secondary">#{rec.seq}</Typography>
				{rec.category && <Chip label={rec.category} size="small" variant="outlined" />}
				{rec.severity &&
					<Chip
						label={rec.severity}
						size="small"
						sx={priorityChipStyles(rec.severity)}
					/>
				}
				{status !== STATUS.OPEN && (
					<Chip label={meta.label} size="small" color={meta.color} variant="outlined" />
				)}
				<Box sx={{ flexGrow: 1 }} />
				{copied && <Typography variant="caption" color="text.secondary">Copied</Typography>}
				<Tooltip title="More actions">
					<IconButton
						size="small"
						aria-label="More actions"
						aria-haspopup="menu"
						aria-expanded={menuOpen ? 'true' : undefined}
						onClick={(e) => setMenuAnchor(e.currentTarget)}
						sx={{ lineHeight: 1, fontWeight: 700 }}
					>
						<Box component="span" sx={{ fontSize: '1.1rem', lineHeight: 1, mt: '-4px' }}>⋯</Box>
					</IconButton>
				</Tooltip>
			</Box>

			<Typography variant="body2" sx={{ mb: rec.location ? 0.25 : 1 }}>
				{rec.recommendation}
			</Typography>

			{rec.location && (
				<Typography
					variant="caption"
					color="text.secondary"
					onClick={rec.anchorText ? () => onHighlight?.(rec.anchorText) : undefined}
					sx={{
						display: 'block',
						mb: 1,
						fontStyle: 'italic',
						...(rec.anchorText ? {
							cursor: 'pointer',
							'&:hover': { color: 'primary.main', textDecoration: 'underline' },
						} : null),
					}}
				>
					{rec.location}{rec.anchorText ? ' ↗' : ''}
				</Typography>
			)}

			{(showDone || showDismiss || showDefer || showReopen || showCodex) && (
				<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
					{showDone && (
						<Button size="small" color="success" onClick={() => onSetStatus(rec, STATUS.DONE)}>
							Mark Done
						</Button>
					)}
					{showDismiss && (
						<Button size="small" color="inherit" onClick={() => onSetStatus(rec, STATUS.DISMISSED)}>
							Dismiss
						</Button>
					)}
					{showDefer && (
						<Button size="small" color="secondary" onClick={() => onSetStatus(rec, STATUS.DEFERRED)}>
							Defer
						</Button>
					)}
					{showReopen && (
						<Button size="small" onClick={() => onSetStatus(rec, STATUS.OPEN)}>
							Reopen
						</Button>
					)}
					{showCodex && (
						<Button size="small" color="primary" disabled={promoting} onClick={openAddDialog}>
							{promoting ? 'Adding…' : 'Codex'}
						</Button>
					)}
				</Box>
			)}

			<Menu anchorEl={menuAnchor} open={menuOpen} onClose={closeMenu}>
				<MenuItem disabled={addDisabled || promoting} onClick={openAddDialog}>
					{promoting ? 'Adding…' : 'Add to Codex…'}
				</MenuItem>
				<MenuItem onClick={copyNote}>Copy note</MenuItem>
			</Menu>

			<Dialog open={addOpen} onClose={() => setAddOpen(false)} fullWidth maxWidth="sm">
				<DialogTitle>Add AI Finding to Codex</DialogTitle>
				<DialogContent sx={{ pt: 1 }}>
					<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
						Review and adjust the proposed Codex entry before adding it.
					</Typography>

					<TextField
						label="Title"
						fullWidth
						size="small"
						value={draftTitle}
						onChange={(e) => setDraftTitle(e.target.value)}
						sx={{ mb: 2 }}
					/>

					<TextField
						select
						label="Category"
						fullWidth
						size="small"
						value={draftCategory}
						onChange={(e) => setDraftCategory(e.target.value)}
						sx={{ mb: 2 }}
					>
						{CATEGORY_OPTIONS.map(opt => (
							<MenuItem key={opt.key} value={opt.key}>{opt.label}</MenuItem>
						))}
					</TextField>

					<TextField
						label="Finding"
						fullWidth
						size="small"
						multiline
						minRows={3}
						maxRows={10}
						value={draftNote}
						onChange={(e) => setDraftNote(e.target.value)}
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button variant="contained" onClick={confirmAdd} disabled={promoting}>
						Add to {codexLabel(draftCategory)}
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	)
}
