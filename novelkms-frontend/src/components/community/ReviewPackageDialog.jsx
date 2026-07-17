import { useState } from 'react'
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
import { feedbackTypeLabel } from '../../utils/reviewFeedbackTypes'
import { useReviewPackage, usePackageSnapshot } from '../../hooks/useReviewQueue'

/**
 * Renders a frozen snapshot's HTML in a fully sandboxed iframe.
 *
 * <p>This is the one place in NovelKMS where a user views HTML authored by someone
 * else, so the render trust boundary is real: a hostile author could embed
 * {@code <img onerror=...>} or a {@code javascript:} URL. {@code RichTextPreview}'s
 * own doc notes it is safe only because it renders the current user's own content —
 * that argument does not hold here, so it is deliberately not used.
 *
 * <p>{@code sandbox=""} (no {@code allow-scripts}, no {@code allow-same-origin})
 * puts the document in an opaque origin with scripting disabled: inline event
 * handlers never fire, {@code <script>} never runs, and the frame cannot reach the
 * parent. The author's markup and images still render faithfully, styled by the
 * self-contained CSS below rather than the app's stylesheet (which the frame cannot
 * see). React encodes {@code srcDoc}, so the content cannot break out of the
 * attribute either.
 */
function SnapshotFrame({ html }) {
	const srcDoc = `<!doctype html><html><head><meta charset="utf-8">`
		+ `<style>`
		+ `html,body{margin:0}`
		+ `body{font:16px/1.7 Georgia,'Times New Roman',serif;color:#1a1a1a;padding:20px}`
		+ `p{margin:0 0 1em}`
		+ `h1,h2,h3{font-family:Georgia,serif;line-height:1.3}`
		+ `img{max-width:100%;height:auto}`
		+ `ul,ol{padding-left:1.5em;margin:0 0 1em}`
		+ `blockquote{border-left:3px solid #ccc;margin:0 0 1em;padding-left:12px;color:#555;font-style:italic}`
		+ `hr{border:0;margin:1.5em 0;text-align:center}`
		+ `hr:after{content:"* * *";color:#999;letter-spacing:0.3em}`
		+ `</style></head><body>${html || ''}</body></html>`

	return (
		<Box
			component="iframe"
			title="Manuscript snapshot"
			srcDoc={srcDoc}
			sandbox=""
			sx={{
				width: '100%',
				height: 460,
				border: '1px solid',
				borderColor: 'divider',
				borderRadius: 1,
				bgcolor: '#fff',
			}}
		/>
	)
}

/**
 * A reviewer's view of one package: the author's request and questions first, then
 * the frozen chapter on demand. The manuscript is fetched only when the reviewer
 * chooses to read it — metadata renders instantly while a whole chapter would not.
 *
 * <p>Writing and submitting a review is slice 1D; this is read-only.
 */
export default function ReviewPackageDialog({ open, onClose, requestId }) {
	const [reading, setReading] = useState(false)

	const { data: pkg, isLoading, isError, error } = useReviewPackage(requestId, open && !!requestId)
	const snapshot = usePackageSnapshot(requestId, open && reading && !!requestId)

	// Reset the reader when the dialog closes so reopening starts on the metadata.
	const handleClose = () => {
		setReading(false)
		onClose()
	}

	return (
		<Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
			<DialogTitle>{pkg?.title || 'Review package'}</DialogTitle>
			<DialogContent dividers>
				{isLoading ? (
					<Stack sx={{ alignItems: 'center', py: 4 }}><CircularProgress /></Stack>
				) : isError ? (
					<Alert severity="error">
						{error?.response?.data?.message ?? 'Could not load this package.'}
					</Alert>
				) : pkg ? (
					<Stack spacing={2}>
						<Box>
							<Typography variant="caption" color="text.secondary">
								by @{pkg.authorHandle}{pkg.authorDisplayName ? ` (${pkg.authorDisplayName})` : ''}
							</Typography>
							<Typography variant="body2" color="text.secondary">
								{pkg.sourceTitle}
								{pkg.bookTitle ? ` · ${pkg.bookTitle}` : ''}
								{' · '}{(pkg.wordCount ?? 0).toLocaleString()} words
							</Typography>
						</Box>

						{pkg.description && <Typography variant="body2">{pkg.description}</Typography>}

						{pkg.feedbackTypes?.length > 0 && (
							<Box>
								<Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>Feedback wanted</Typography>
								<Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
									{pkg.feedbackTypes.map(k => (
										<Chip key={k} size="small" variant="outlined" label={feedbackTypeLabel(k)} />
									))}
								</Stack>
							</Box>
						)}

						{pkg.authorQuestions && (
							<Box>
								<Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>The author asks</Typography>
								<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{pkg.authorQuestions}</Typography>
							</Box>
						)}

						{pkg.contentWarnings && (
							<Alert severity="warning" variant="outlined">
								<Typography variant="body2" sx={{ fontWeight: 600 }}>Content warning</Typography>
								<Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{pkg.contentWarnings}</Typography>
							</Alert>
						)}

						<Divider />

						{!reading ? (
							<Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
								<Button variant="contained" onClick={() => setReading(true)}>Read the chapter</Button>
								<Typography variant="caption" color="text.secondary">
									Shared with you for review by @{pkg.authorHandle}. Please don't copy or redistribute it.
								</Typography>
							</Stack>
						) : snapshot.isLoading ? (
							<Stack sx={{ alignItems: 'center', py: 4 }}><CircularProgress /></Stack>
						) : snapshot.isError ? (
							<Alert severity="error">
								{snapshot.error?.response?.data?.message ?? 'Could not load the chapter.'}
							</Alert>
						) : snapshot.data ? (
							<Box>
								<Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
									Shared for review by @{pkg.authorHandle} — please don't copy or redistribute.
								</Typography>
								<SnapshotFrame html={snapshot.data.contentHtml} />
							</Box>
						) : null}
					</Stack>
				) : null}
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
