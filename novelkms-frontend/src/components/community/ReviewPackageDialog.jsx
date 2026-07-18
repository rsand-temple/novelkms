import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	Checkbox,
	Chip,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	FormControlLabel,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import { feedbackTypeLabel } from '../../utils/reviewFeedbackTypes'
import { htmlToPlain, plainToHtml } from '../../utils/reviewBody'
import { useReviewPackage, usePackageSnapshot } from '../../hooks/useReviewQueue'
import {
	useMyReview,
	useSaveDraft,
	useSubmitReview,
	useWithdrawReview,
} from '../../hooks/useHumanReviews'

/**
 * Renders a frozen snapshot's HTML in a fully sandboxed iframe.
 *
 * This is one of the two places in NovelKMS where a user views HTML authored by
 * someone else, so the render trust boundary is real: a hostile author could embed
 * an onerror handler or a javascript: URL. `sandbox=""` (no allow-scripts, no
 * allow-same-origin) puts the document in an opaque origin with scripting disabled —
 * inline handlers never fire, <script> never runs, and the frame cannot reach the
 * parent. The author's markup and images still render faithfully, styled by the
 * self-contained CSS below, and React encodes srcDoc so the content cannot break out
 * of the attribute either.
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

const REVIEW_STATUS_CHIP = {
	DRAFT:     { color: 'default', label: 'Draft' },
	SUBMITTED: { color: 'success', label: 'Submitted' },
	WITHDRAWN: { color: 'default', label: 'Withdrawn' },
}

/**
 * The review editor. Kept as an inner component mounted with a key so it initializes
 * its own state from the loaded review once, rather than syncing through an effect —
 * the house rule is "derive during render or remount with a key," and a review is
 * saved on explicit clicks, so a remount after save is harmless.
 *
 * A submitted review is shown read-only with Revise/Withdraw: there is no in-place
 * edit of a submission (spec §30.2 Q6). Revise reopens it — saving moves the row back
 * to DRAFT on the server — which is the "withdraw and rewrite" path made concrete.
 */
function ReviewEditor({ requestId, review, onNotify }) {
	const initialStatus = review?.status ?? null
	const submitted = initialStatus === 'SUBMITTED'

	const [text, setText] = useState(htmlToPlain(review?.contentHtml ?? ''))
	const [aiAssisted, setAiAssisted] = useState(review?.aiAssisted ?? false)
	const [revising, setRevising] = useState(false)

	const save = useSaveDraft()
	const submit = useSubmitReview()
	const withdraw = useWithdrawReview()
	const busy = save.isPending || submit.isPending || withdraw.isPending

	const readOnly = submitted && !revising
	const hasText = text.trim().length > 0

	const notifyError = (verb) => (e) =>
		onNotify({ severity: 'error', message: e?.response?.data?.message ?? `Could not ${verb} your review.` })

	const doSave = () =>
		save.mutate(
			{ requestId, contentHtml: plainToHtml(text), aiAssisted },
			{
				onSuccess: () => { setRevising(false); onNotify({ severity: 'success', message: 'Draft saved.' }) },
				onError: notifyError('save'),
			},
		)

	const doSubmit = () =>
		submit.mutate(
			{ requestId, contentHtml: plainToHtml(text), aiAssisted },
			{
				onSuccess: () => { setRevising(false); onNotify({ severity: 'success', message: 'Review submitted.' }) },
				onError: notifyError('submit'),
			},
		)

	const doWithdraw = () =>
		withdraw.mutate(
			{ requestId },
			{
				onSuccess: () => onNotify({ severity: 'success', message: 'Review withdrawn.' }),
				onError: notifyError('withdraw'),
			},
		)

	const chip = initialStatus ? REVIEW_STATUS_CHIP[initialStatus] : null

	return (
		<Stack spacing={1.5}>
			<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
				<Typography variant="subtitle2" sx={{ fontWeight: 700, flexGrow: 1 }}>Your review</Typography>
				{chip && <Chip size="small" color={chip.color} label={chip.label} />}
			</Stack>

			{submitted && !revising && (
				<Alert severity="success" variant="outlined">
					You submitted this review. To change it, choose Revise — it returns to a draft you can edit and resubmit.
				</Alert>
			)}

			<TextField
				value={text}
				onChange={(e) => setText(e.target.value)}
				multiline
				minRows={6}
				maxRows={16}
				fullWidth
				placeholder="Share what worked, what confused you, and anything the author asked about."
				disabled={readOnly || busy}
			/>

			<FormControlLabel
				control={
					<Checkbox
						checked={aiAssisted}
						onChange={(e) => setAiAssisted(e.target.checked)}
						disabled={readOnly || busy}
						size="small"
					/>
				}
				label={<Typography variant="body2">I used AI assistance for this review</Typography>}
			/>

			<Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
				{readOnly ? (
					<>
						<Button size="small" onClick={() => setRevising(true)} disabled={busy}>Revise</Button>
						<Button size="small" color="error" onClick={doWithdraw} disabled={busy}>Withdraw</Button>
					</>
				) : (
					<>
						<Button size="small" onClick={doSave} disabled={busy}>Save draft</Button>
						<Button size="small" variant="contained" onClick={doSubmit} disabled={busy || !hasText}>
							Submit review
						</Button>
						{initialStatus && initialStatus !== 'WITHDRAWN' && (
							<Button size="small" color="error" onClick={doWithdraw} disabled={busy}>Withdraw</Button>
						)}
					</>
				)}
			</Stack>

			<Typography variant="caption" color="text.secondary">
				Your review is shared privately with the author.
			</Typography>
		</Stack>
	)
}

/**
 * A reviewer's view of one package: the author's request and questions, the frozen
 * chapter on demand, and — slice 1D — an editor to write, submit, and withdraw a
 * review. The manuscript is fetched only when the reviewer chooses to read it, so
 * metadata renders instantly while a whole chapter loads lazily.
 */
export default function ReviewPackageDialog({ open, onClose, requestId }) {
	const [reading, setReading] = useState(false)
	const [notice, setNotice] = useState(null)

	const { data: pkg, isLoading, isError, error } = useReviewPackage(requestId, open && !!requestId)
	const snapshot = usePackageSnapshot(requestId, open && reading && !!requestId)
	const myReview = useMyReview(requestId, open && !!requestId)

	const handleClose = () => {
		setReading(false)
		setNotice(null)
		onClose()
	}

	// The editor initializes from the loaded review, so it must not mount until that
	// query has settled; its key includes the review id so a first save (null -> id)
	// cleanly remounts it around the newly-created row.
	const reviewReady = myReview.isSuccess || myReview.isError
	const editorKey = `${requestId}:${myReview.data?.id ?? 'none'}`

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

						<Divider />

						{notice && (
							<Alert severity={notice.severity} onClose={() => setNotice(null)}>
								{notice.message}
							</Alert>
						)}

						{reviewReady ? (
							<ReviewEditor
								key={editorKey}
								requestId={requestId}
								review={myReview.data ?? null}
								onNotify={setNotice}
							/>
						) : (
							<Stack sx={{ alignItems: 'center', py: 2 }}><CircularProgress size={22} /></Stack>
						)}
					</Stack>
				) : null}
			</DialogContent>
			<DialogActions>
				<Button onClick={handleClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
