import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	Stack,
	Typography,
} from '@mui/material'
import { useReviewSnapshot } from '../../hooks/useReviewRequests'
import RichTextPreview from '../ai/RichTextPreview'

/**
 * Read-only view of the frozen snapshot behind a review request. The snapshot is
 * immutable — this shows exactly what was captured at publish time, never the live
 * chapter, even if the source has since been edited or deleted.
 *
 * Rendering the author's own captured HTML through RichTextPreview is within the
 * same trust boundary as the editor's own template preview.
 */
export default function SnapshotDialog({ open, onClose, requestId, requestTitle }) {
	const { data: snapshot, isLoading, isError, error } = useReviewSnapshot(requestId, open && !!requestId)

	return (
		<Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
			<DialogTitle>{requestTitle || 'Published snapshot'}</DialogTitle>
			<DialogContent dividers>
				{isLoading ? (
					<Stack sx={{ alignItems: 'center', py: 4 }}>
						<CircularProgress />
					</Stack>
				) : isError ? (
					<Alert severity="error">
						{error?.response?.data?.message ?? 'Could not load the snapshot.'}
					</Alert>
				) : snapshot ? (
					<Stack spacing={2}>
						<Box>
							<Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
								{snapshot.sourceTitle}
							</Typography>
							<Stack
								direction="row"
								spacing={1}
								sx={{ flexWrap: 'wrap', mt: 0.5, alignItems: 'center', rowGap: 0.5 }}
							>
								{snapshot.bookTitle && (
									<Chip size="small" label={snapshot.bookTitle} variant="outlined" />
								)}
								<Chip
									size="small"
									variant="outlined"
									label={`${(snapshot.wordCount ?? 0).toLocaleString()} words`}
								/>
								{snapshot.createdAt && (
									<Typography variant="caption" color="text.secondary">
										Captured {new Date(snapshot.createdAt).toLocaleString()}
									</Typography>
								)}
							</Stack>
						</Box>
						<Divider />
						<RichTextPreview html={snapshot.contentHtml} />
					</Stack>
				) : null}
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
