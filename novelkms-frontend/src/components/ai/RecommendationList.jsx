import { useMemo, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	ButtonGroup,
	Checkbox,
	Chip,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	FormControlLabel,
	IconButton,
	Menu,
	MenuItem,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Tooltip,
	Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'

const CATEGORY_LABELS = {
	CHARACTER: 'Characters',
	VOICE: 'Voices',
	PLOT: 'Plot',
	WORLD: 'World',
	TIMELINE: 'Timeline',
	CANON: 'Canon',
	NOTES: 'Notes',
}

const CATEGORY_OPTIONS = [
	{ key: 'CHARACTER', label: 'Characters' },
	{ key: 'VOICE', label: 'Voices' },
	{ key: 'PLOT', label: 'Plot' },
	{ key: 'WORLD', label: 'World' },
	{ key: 'TIMELINE', label: 'Timeline' },
	{ key: 'CANON', label: 'Canon' },
	{ key: 'NOTES', label: 'Notes' },
]

const HIDDEN_STATUSES = new Set(['DELETED', 'PROMOTED'])

function severityColor(severity) {
	switch ((severity ?? '').toUpperCase()) {
		case 'HIGH': return 'error'
		case 'MEDIUM': return 'warning'
		case 'LOW': return 'info'
		default: return 'default'
	}
}

function normalizeCategory(value) {
	const raw = (value ?? '').trim()
	if (!raw) return 'NOTES'

	const upper = raw.toUpperCase()
	if (CATEGORY_LABELS[upper]) return upper

	const compact = upper.replace(/[^A-Z]/g, '')

	switch (compact) {
		case 'CHARACTER':
		case 'CHARACTERS':
			return 'CHARACTER'
		case 'VOICE':
		case 'VOICES':
			return 'VOICE'
		case 'PLOT':
			return 'PLOT'
		case 'WORLD':
			return 'WORLD'
		case 'TIMELINE':
			return 'TIMELINE'
		case 'CANON':
			return 'CANON'
		case 'NOTE':
		case 'NOTES':
		case 'GENERALNOTE':
		case 'GENERALNOTES':
			return 'NOTES'
		default:
			return 'NOTES'
	}
}

function codexLabel(key) {
	return CATEGORY_LABELS[normalizeCategory(key)] ?? 'Notes'
}

function defaultTitle(rec) {
	const title = (rec.codexTitle ?? '').trim()
	if (title) return title

	const text = (rec.recommendation ?? '').trim()
	if (!text) return 'Untitled'
	return text.length <= 80 ? text : text.slice(0, 80).trim()
}

function RecommendationRow({
	rec,
	onSetStatus,
	onPromote,
	promotingId,
	skipDeleteConfirm,
	setSkipDeleteConfirm,
}) {
	const initialCategory = useMemo(() => normalizeCategory(rec.codexCategory), [rec.codexCategory])
	const initialTitle = useMemo(() => defaultTitle(rec), [rec])
	
	const [categoryMenuAnchor, setCategoryMenuAnchor] = useState(null)
	const categoryMenuOpen = Boolean(categoryMenuAnchor)
	const [selectedCategory, setSelectedCategory] = useState(initialCategory)
	const [addOpen, setAddOpen] = useState(false)
	const [deleteOpen, setDeleteOpen] = useState(false)
	const [draftTitle, setDraftTitle] = useState(initialTitle)
	const [draftCategory, setDraftCategory] = useState(initialCategory)
	const [squelchDelete, setSquelchDelete] = useState(skipDeleteConfirm)

	const status = (rec.status ?? 'OPEN').toUpperCase()
	const statusValue =
		status === 'ACCEPTED' || status === 'REJECTED' || status === 'FUTURE'
			? status
			: null

	const isPromoting = promotingId === rec.id
	const deleteDisabled = status === 'ACCEPTED' || status === 'FUTURE'
	const addDisabled = status === 'REJECTED'

	const openAddDialog = () => {
		setDraftTitle(defaultTitle(rec))
		setDraftCategory(selectedCategory)
		setAddOpen(true)
	}

	const confirmAdd = () => {
		const title = draftTitle.trim() || defaultTitle(rec)
		setSelectedCategory(draftCategory)
		setAddOpen(false)
		onPromote(rec, draftCategory, title)
	}

	const requestDelete = () => {
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
	
	const openCategoryMenu = (event) => {
		setCategoryMenuAnchor(event.currentTarget)
	}

	const closeCategoryMenu = () => {
		setCategoryMenuAnchor(null)
	}

	const chooseCategory = (category) => {
		setSelectedCategory(category)
		setCategoryMenuAnchor(null)
	}

	return (
		<Box sx={{ mb: 1.5, pb: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5, flexWrap: 'wrap' }}>
				<Typography variant="caption" color="text.secondary">#{rec.seq}</Typography>
				{rec.category && <Chip label={rec.category} size="small" variant="outlined" />}
				{rec.severity && <Chip label={rec.severity} size="small" color={severityColor(rec.severity)} />}
				{status === 'FUTURE' && (
					<Chip label="Future" size="small" color="secondary" variant="outlined" />
				)}
			</Box>

			<Typography variant="body2" sx={{ mb: rec.location ? 0.25 : 1 }}>
				{rec.recommendation}
			</Typography>

			{rec.location && (
				<Typography
					variant="caption"
					color="text.secondary"
					sx={{ display: 'block', mb: 1, fontStyle: 'italic' }}
				>
					{rec.location}
				</Typography>
			)}

			<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
				<ToggleButtonGroup
					exclusive
					size="small"
					value={statusValue}
					onChange={(_e, val) => {
						if (val) onSetStatus(rec, val)
					}}
				>
					<ToggleButton value="ACCEPTED" color="success">Accept</ToggleButton>
					<ToggleButton value="REJECTED" color="error">Reject</ToggleButton>
					<ToggleButton value="FUTURE" color="secondary">Future</ToggleButton>
				</ToggleButtonGroup>

				<Tooltip title={deleteDisabled ? 'Accepted and future findings cannot be deleted' : 'Delete finding'}>
					<span>
						<IconButton
							size="small"
							color="default"
							disabled={deleteDisabled}
							onClick={requestDelete}
							aria-label="Delete finding"
						>
							<DeleteIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>
			</Box>

			<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1, flexWrap: 'wrap' }}>
				<ButtonGroup
					size="small"
					variant="outlined"
					disabled={isPromoting || addDisabled}
				>
					<Button onClick={openAddDialog}>
						{isPromoting ? 'Adding…' : `Add to ${codexLabel(selectedCategory)}`}
					</Button>

					<Button
						size="small"
						aria-label="Choose codex category"
						aria-haspopup="menu"
						aria-expanded={categoryMenuOpen ? 'true' : undefined}
						onClick={openCategoryMenu}
					>
						<ArrowDropDownIcon fontSize="small" />
					</Button>
				</ButtonGroup>

				<Menu
					anchorEl={categoryMenuAnchor}
					open={categoryMenuOpen}
					onClose={closeCategoryMenu}
				>
					{CATEGORY_OPTIONS.map(opt => (
						<MenuItem
							key={opt.key}
							selected={opt.key === selectedCategory}
							onClick={() => chooseCategory(opt.key)}
						>
							{opt.label}
						</MenuItem>
					))}
				</Menu>
			</Box>
			
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
					<Typography variant="body2">
						{rec.recommendation}
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAddOpen(false)}>Cancel</Button>
					<Button variant="contained" onClick={confirmAdd} disabled={isPromoting}>
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
					<Button color="error" variant="contained" onClick={confirmDelete}>
						Delete
					</Button>
				</DialogActions>
			</Dialog>
		</Box>
	)
}

export default function RecommendationList({ review, onSetStatus, onPromote, promotingId }) {
	const [skipDeleteConfirm, setSkipDeleteConfirm] = useState(() =>
		window.localStorage.getItem('ai.skipDeleteConfirm') === 'true',
	)

	const persistSkipDeleteConfirm = (value) => {
		setSkipDeleteConfirm(value)
		window.localStorage.setItem('ai.skipDeleteConfirm', value ? 'true' : 'false')
	}

	if (!review) return null

	if (review.status === 'FAILED') {
		return <Alert severity="error">{review.errorMessage ?? 'The review failed.'}</Alert>
	}

	if (review.status !== 'COMPLETED') {
		return <Typography variant="body2" color="text.secondary">Review in progress…</Typography>
	}

	const allRecs = review.recommendations ?? []
	const recs = allRecs.filter(rec => !HIDDEN_STATUSES.has((rec.status ?? '').toUpperCase()))

	if (allRecs.length === 0) {
		return <Alert severity="success">No notes — the model had no substantive recommendations on this chapter.</Alert>
	}

	if (recs.length === 0) {
		return <Alert severity="success">All recommendations have been handled.</Alert>
	}

	return (
		<Box>
			{recs.map(rec => (
				<RecommendationRow
					key={`${rec.id}:${normalizeCategory(rec.codexCategory)}`}
					rec={rec}
					onSetStatus={onSetStatus}
					onPromote={onPromote}
					promotingId={promotingId}
					skipDeleteConfirm={skipDeleteConfirm}
					setSkipDeleteConfirm={persistSkipDeleteConfirm}
				/>
			))}
		</Box>
	)
}