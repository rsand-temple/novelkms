import {
	Alert,
	Box,
	Button,
	Chip,
	ToggleButton,
	ToggleButtonGroup,
	Typography,
} from '@mui/material'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'

const CATEGORY_LABELS = {
	CHARACTER: 'Characters',
	VOICE:     'Voices',
	PLOT:      'Plot',
	WORLD:     'World',
	TIMELINE:  'Timeline',
	CANON:     'Canon',
	NOTES:     'Notes',
}

function severityColor(severity) {
	switch ((severity ?? '').toUpperCase()) {
		case 'HIGH':   return 'error'
		case 'MEDIUM': return 'warning'
		case 'LOW':    return 'info'
		default:       return 'default'
	}
}

function codexLabel(key) {
	return CATEGORY_LABELS[(key ?? '').toUpperCase()] ?? 'Notes'
}

/**
 * Renders the recommendations of a completed review with per-item Accept/Reject
 * and one-click promote-to-codex. Shared so any surface (panel, future views)
 * presents recommendations identically.
 *
 * Props:
 *   review       {object}  — review with .status, .errorMessage, .recommendations
 *   onSetStatus  {(rec, value: 'ACCEPTED'|'REJECTED'|null) => void}
 *   onPromote    {(rec) => void}
 *   promotingId  {string|null} — rec id currently being promoted (disables its button)
 */
export default function RecommendationList({ review, onSetStatus, onPromote, promotingId }) {
	if (!review) return null

	if (review.status === 'FAILED') {
		return <Alert severity="error">{review.errorMessage ?? 'The review failed.'}</Alert>
	}
	if (review.status !== 'COMPLETED') {
		return <Typography variant="body2" color="text.secondary">Review in progress…</Typography>
	}
	const recs = review.recommendations ?? []
	if (recs.length === 0) {
		return <Alert severity="success">No notes — the model had no substantive recommendations on this chapter.</Alert>
	}

	return (
		<Box>
			{recs.map(rec => {
				const value = rec.status === 'ACCEPTED' ? 'ACCEPTED' : rec.status === 'REJECTED' ? 'REJECTED' : null
				const label = codexLabel(rec.codexCategory)
				const promoted = !!rec.promotedSceneId
				return (
					<Box key={rec.id} sx={{ mb: 1.5, pb: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
						<Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5, flexWrap: 'wrap' }}>
							<Typography variant="caption" color="text.secondary">#{rec.seq}</Typography>
							{rec.category && <Chip label={rec.category} size="small" variant="outlined" />}
							{rec.severity && <Chip label={rec.severity} size="small" color={severityColor(rec.severity)} />}
						</Box>

						<Typography variant="body2" sx={{ mb: rec.location ? 0.25 : 1 }}>
							{rec.recommendation}
						</Typography>
						{rec.location && (
							<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1, fontStyle: 'italic' }}>
								{rec.location}
							</Typography>
						)}

						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
							<ToggleButtonGroup
								exclusive size="small" value={value}
								onChange={(_e, val) => onSetStatus(rec, val)}
							>
								<ToggleButton value="ACCEPTED" color="success">Accept</ToggleButton>
								<ToggleButton value="REJECTED" color="error">Reject</ToggleButton>
							</ToggleButtonGroup>

							<Box sx={{ flexGrow: 1 }} />

							{promoted ? (
								<Chip
									icon={<CheckCircleOutlinedIcon />}
									label={`Added to ${label}`}
									size="small" color="success" variant="outlined"
								/>
							) : (
								<Button
									size="small"
									onClick={() => onPromote(rec)}
									disabled={promotingId === rec.id}
								>
									{promotingId === rec.id ? 'Adding…' : `Add to ${label}`}
								</Button>
							)}
						</Box>
					</Box>
				)
			})}
		</Box>
	)
}
