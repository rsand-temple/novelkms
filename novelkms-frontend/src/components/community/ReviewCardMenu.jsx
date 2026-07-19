import { useState } from 'react'
import {
	Alert,
	Divider,
	IconButton,
	Menu,
	MenuItem,
	Snackbar,
} from '@mui/material'
import ReportDialog from './ReportDialog'
import { useBlockUser, useUnblockUser } from '../../hooks/useReviewSafety'

/**
 * The block/report overflow menu (⋯) shared by every review-network card surface —
 * the queue, the package dialog, Reviews I'm Writing, and Reviews Received (Decision
 * 13 = all four). Each surface drops in one of these with the counterparty's handle
 * and, when there is a concrete piece of content, a `contentTarget` describing it.
 *
 * The trigger is a bare "⋯" glyph rather than an icon import: no MoreVert/MoreHoriz
 * is proven present in node_modules, and an absent icon fails the Rolldown build, so
 * the glyph sidesteps the hazard entirely.
 *
 * Menu children are kept flat (no Fragment wrappers): MUI injects props by walking
 * indexed children, and a Fragment cross-wires the handlers.
 *
 * @param {string} handle           counterparty handle (author or reviewer)
 * @param {string} [displayName]    optional display name, for menu/snackbar copy
 * @param {object} [contentTarget]  { type: 'REQUEST'|'REVIEW', id, label } to report
 */
export default function ReviewCardMenu({ handle, displayName, contentTarget }) {
	const [anchorEl, setAnchorEl] = useState(null)
	const [reportTarget, setReportTarget] = useState(null)
	const [snack, setSnack] = useState(null)

	const block = useBlockUser()
	const unblock = useUnblockUser()

	if (!handle) return null

	const open = Boolean(anchorEl)
	const close = () => setAnchorEl(null)

	const startReport = (target) => {
		close()
		setReportTarget(target)
	}

	const onReportClose = (result) => {
		setReportTarget(null)
		if (result?.reported) setSnack({ severity: 'success', message: 'Report sent to the moderators.' })
	}

	const doBlock = () => {
		close()
		block.mutate(handle, {
			onSuccess: () => setSnack({
				severity: 'success',
				message: `Blocked @${handle}.`,
				undo: true,
			}),
			onError: (e) => setSnack({
				severity: 'error',
				message: e?.response?.data?.message ?? 'Could not block this user.',
			}),
		})
	}

	const doUndoBlock = () => {
		setSnack(null)
		unblock.mutate(handle, {
			onSuccess: () => setSnack({ severity: 'success', message: `Unblocked @${handle}.` }),
			onError: (e) => setSnack({
				severity: 'error',
				message: e?.response?.data?.message ?? 'Could not unblock this user.',
			}),
		})
	}

	return (
		<>
			<IconButton
				size="small"
				aria-label="More actions"
				onClick={(e) => setAnchorEl(e.currentTarget)}
				sx={{ lineHeight: 1, fontSize: 20 }}
			>
				⋯
			</IconButton>

			<Menu anchorEl={anchorEl} open={open} onClose={close}>
				{contentTarget && (
					<MenuItem
						onClick={() => startReport({
							type: contentTarget.type,
							id: contentTarget.id,
							label: contentTarget.label,
						})}
					>
						Report {contentTarget.label ?? 'this'}
					</MenuItem>
				)}
				<MenuItem
					onClick={() => startReport({ type: 'PROFILE', handle, label: `@${handle}'s profile` })}
				>
					Report @{handle}'s profile
				</MenuItem>
				<Divider />
				<MenuItem onClick={doBlock} disabled={block.isPending}>
					Block @{handle}
				</MenuItem>
			</Menu>

			{reportTarget && (
				<ReportDialog open onClose={onReportClose} target={reportTarget} />
			)}

			<Snackbar
				open={!!snack}
				autoHideDuration={5000}
				onClose={() => setSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{snack ? (
					<Alert
						severity={snack.severity}
						onClose={() => setSnack(null)}
						sx={{ width: '100%' }}
						action={snack.undo ? (
							<button
								onClick={doUndoBlock}
								style={{
									background: 'none',
									border: 'none',
									color: 'inherit',
									font: 'inherit',
									fontWeight: 700,
									textDecoration: 'underline',
									cursor: 'pointer',
								}}
							>
								Undo
							</button>
						) : undefined}
					>
						{snack.message}
					</Alert>
				) : undefined}
			</Snackbar>
		</>
	)
}
