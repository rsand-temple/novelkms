import { useMemo, useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Chip,
	IconButton,
	MenuItem,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Tooltip,
	Typography,
} from '@mui/material'
import { EventOutlined } from '@mui/icons-material'
import { DeleteOutlined } from '@mui/icons-material';

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

	// Direct enum match from backend/model.
	if (CATEGORY_LABELS[upper]) return upper

	// Friendly label / plural label match.
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

function RecommendationRow({ rec, onSetStatus, onPromote, promotingId }) {
	const initialCategory = useMemo(() => normalizeCategory(rec.codexCategory), [rec.codexCategory])
	const [selectedCategory, setSelectedCategory] = useState(initialCategory)

	const status = (rec.status ?? 'OPEN').toUpperCase()
	const statusValue =
		status === 'ACCEPTED' || status === 'REJECTED' || status === 'FUTURE'
			? status
			: null

	const addLabel = codexLabel(selectedCategory)
	const isPromoting = promotingId === rec.id

	return (
		<Box sx={{ mb: 1.5, pb: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5, flexWrap: 'wrap' }}>
				<Typography variant="caption" color="text.secondary">#{rec.seq}</Typography>
				{rec.category && <Chip label={rec.category} size="small" variant="outlined" />}
				{rec.severity && <Chip label={rec.severity} size="small" color={severityColor(rec.severity)} />}
				{status === 'FUTURE' && (
					<Chip icon={<EventOutlined />} label="Future" size="small" color="secondary" variant="outlined" />
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

				<Tooltip title="Remove this finding from the working list">
					<IconButton
						size="small"
						onClick={() => onSetStatus(rec, 'DELETED')}
						aria-label="Delete finding"
					>
						<DeleteOutlined fontSize="small" />
					</IconButton>
				</Tooltip>
			</Box>

			<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1, flexWrap: 'wrap' }}>
				<TextField
					select
					size="small"
					label="Add to"
					value={selectedCategory}
					onChange={(e) => setSelectedCategory(e.target.value)}
					sx={{ minWidth: 145 }}
				>
					{CATEGORY_OPTIONS.map(opt => (
						<MenuItem key={opt.key} value={opt.key}>{opt.label}</MenuItem>
					))}
				</TextField>

				<Button
					size="small"
					variant="outlined"
					onClick={() => onPromote(rec, selectedCategory)}
					disabled={isPromoting}
				>
					{isPromoting ? 'Adding…' : `Add to ${addLabel}`}
				</Button>
			</Box>
		</Box>
	)
}

/**
 * Renders the active recommendations of a completed review.
 *
 * Hidden from the working list:
 *   DELETED  — author removed it
 *   PROMOTED — author added it to Codex
 */
export default function RecommendationList({ review, onSetStatus, onPromote, promotingId }) {
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
				/>))}
		</Box>
	)
}