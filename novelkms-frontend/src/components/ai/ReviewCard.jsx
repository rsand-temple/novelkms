import { useMemo, useState } from 'react'
import {
	Box,
	Button,
	Chip,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Checkbox,
	FormControlLabel,
	IconButton,
	Menu,
	MenuItem,
	Paper,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Tooltip,
	Typography,
} from '@mui/material'
import {
	CATEGORY_OPTIONS,
	codexLabel,
	defaultTitle,
	normalizeCategory,
	recommendationToText,
	priorityChipStyles,
} from './recommendationUtils'

/**
 * ReviewCard — one AI recommendation in the editor review rail.
 *
 * Face actions: Accept / Reject / Defer (FUTURE) / Copy note.
 * Overflow (⋯) menu: Add to Codex…, Delete.
 *
 * Promote and Delete each open a confirm/edit dialog. Delete honors the
 * "don't ask again" preference passed down from the list.
 *
 * Props:
 *   rec                  recommendation record
 *   onSetStatus(rec, s)  set lifecycle status (ACCEPTED|REJECTED|FUTURE|DELETED|OPEN)
 *   onPromote(rec, cat, title)  promote to codex
 *   promoting            boolean — this card's promote is in flight
 *   skipDeleteConfirm    boolean — suppress the delete confirm dialog
 *   setSkipDeleteConfirm fn(bool) — persist the suppression preference
 *   onHighlight(anchorText) — scroll the editor to the quoted passage
 */
export default function ReviewCard({
	rec,
	onSetStatus,
	onPromote,
	promoting,
	skipDeleteConfirm,
	setSkipDeleteConfirm,
	onHighlight,
}) {
	const initialCategory = useMemo(() => normalizeCategory(rec.codexCategory), [rec.codexCategory])

	const [menuAnchor, setMenuAnchor] = useState(null)
	const menuOpen = Boolean(menuAnchor)
	const [addOpen, setAddOpen] = useState(false)
	const [deleteOpen, setDeleteOpen] = useState(false)
	const [draftTitle, setDraftTitle] = useState(() => defaultTitle(rec))
	const [draftCategory, setDraftCategory] = useState(initialCategory)
	const [squelchDelete, setSquelchDelete] = useState(skipDeleteConfirm)
	const [copied, setCopied] = useState(false)

	const status = (rec.status ?? 'OPEN').toUpperCase()
	const statusValue =
		status === 'ACCEPTED' || status === 'REJECTED' || status === 'FUTURE' ? status : null

	const deleteDisabled = status === 'ACCEPTED' || status === 'FUTURE'
	const addDisabled = status === 'REJECTED'

	const closeMenu = () => setMenuAnchor(null)

	const openAddDialog = () => {
		closeMenu()
		setDraftTitle(defaultTitle(rec))
		setDraftCategory(initialCategory)
		setAddOpen(true)
	}

	const confirmAdd = () => {
		const title = draftTitle.trim() || defaultTitle(rec)
		setAddOpen(false)
		onPromote(rec, draftCategory, title)
	}

	const requestDelete = () => {
		closeMenu()
		if (deleteDisabled) return
		if (skipDeleteConfirm) {
			onSetStatus(rec, 'DELETED')
			return
		}
		setSquelchDelete(skipDeleteConfirm)
		setDeleteOpen(true)
	}

	const confirmDelete = () => {
		if (squelchDelete) setSkipDeleteConfirm(true)
		setDeleteOpen(false)
		onSetStatus(rec, 'DELETED')
	}

	const copyNote = async () => {
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
				{status === 'FUTURE' && <Chip label="Future" size="small" color="secondary" variant="outlined" />}
				<Box sx={{ flexGrow: 1 }} />
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

			<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
				<ToggleButtonGroup
					exclusive
					size="small"
					value={statusValue}
					onChange={(_e, val) => { if (val) onSetStatus(rec, val) }}
				>
					<ToggleButton value="ACCEPTED" color="success">Accept</ToggleButton>
					<ToggleButton value="REJECTED" color="error">Reject</ToggleButton>
					<ToggleButton value="FUTURE" color="secondary">Defer</ToggleButton>
				</ToggleButtonGroup>

				<Button size="small" onClick={copyNote}>
					{copied ? 'Copied' : 'Copy note'}
				</Button>
			</Box>

			<Menu anchorEl={menuAnchor} open={menuOpen} onClose={closeMenu}>
				<MenuItem disabled={addDisabled || promoting} onClick={openAddDialog}>
					{promoting ? 'Adding…' : 'Add to Codex…'}
				</MenuItem>
				<MenuItem
					disabled={deleteDisabled}
					onClick={requestDelete}
					sx={{ color: 'error.main' }}
				>
					Delete
				</MenuItem>
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

					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
						Finding
					</Typography>
					<Typography variant="body2">{rec.recommendation}</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button variant="contained" onClick={confirmAdd} disabled={promoting}>
						Add to {codexLabel(draftCategory)}
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
				<DialogTitle>Delete this AI finding?</DialogTitle>
				<DialogContent>
					<Typography variant="body2" sx={{ mb: 1.5 }}>
						This will remove the finding from the working review list. The review history remains preserved.
					</Typography>
					<FormControlLabel
						control={
							<Checkbox
								checked={squelchDelete}
								onChange={(e) => setSquelchDelete(e.target.checked)}
							/>
						}
						label="Don’t ask again for future deletes"
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDeleteOpen(false)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={confirmDelete}>Delete</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	)
}
