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
	DialogContentText,
	DialogTitle,
	IconButton,
	Tooltip,
	Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import FolderIcon from '@mui/icons-material/Folder'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import ArticleIcon from '@mui/icons-material/Article'
import TheatersIcon from '@mui/icons-material/Theaters'
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark'
import DataObjectIcon from '@mui/icons-material/DataObject'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { useTrash, useRestoreTrashItem, usePurgeTrashItem, useEmptyTrash } from '../../hooks/useTrash'

const TYPE_META = {
	PROJECT:        { icon: FolderIcon,              label: 'Project' },
	BOOK:           { icon: MenuBookIcon,             label: 'Book' },
	CHAPTER:        { icon: ArticleIcon,              label: 'Chapter' },
	SCENE:          { icon: TheatersIcon,              label: 'Scene' },
	CODEX_CATEGORY: { icon: CollectionsBookmarkIcon,  label: 'Codex Category' },
	CODEX_ENTRY:    { icon: DataObjectIcon,            label: 'Codex Entry' },
	AI_REVIEW:      { icon: ArticleIcon,              label: 'AI Review' },
}

function formatTime(iso) {
	if (!iso) return ''
	try { return new Date(iso).toLocaleString() } catch { return iso }
}

function errMsg(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'An error occurred.'
}

/**
 * The per-user trash can. Shows every soft-deleted root with type, title,
 * originating project, child count, and deletion time. Supports restore
 * (with 409 collision-block feedback) and permanent purge.
 */
export default function TrashPanel() {
	const { data: items = [], isLoading } = useTrash()
	const { mutate: restore, isPending: restoring } = useRestoreTrashItem()
	const { mutate: purge, isPending: purging } = usePurgeTrashItem()
	const { mutate: emptyAll, isPending: emptying } = useEmptyTrash()

	const [feedback, setFeedback] = useState(null)           // { severity, message }
	const [confirmPurge, setConfirmPurge] = useState(null)    // batchId or 'all'

	const handleRestore = (batchId) => {
	  setFeedback(null)

	  if (!batchId) {
	    setFeedback({
	      severity: 'error',
	      message: 'Cannot restore this item because the trash batch id is missing.',
	    })
	    return
	  }

	  restore(batchId, {
	    onSuccess: () => setFeedback({ severity: 'success', message: 'Item restored.' }),
	    onError: (e) => setFeedback({ severity: 'error', message: errMsg(e) }),
	  })
	}
	
	const handlePurge = () => {
		if (!confirmPurge) return
		setFeedback(null)
		if (confirmPurge === 'all') {
			emptyAll(undefined, {
				onSuccess: () => {
					setConfirmPurge(null)
					setFeedback({ severity: 'success', message: 'Trash emptied.' })
				},
				onError: (e) => {
					setConfirmPurge(null)
					setFeedback({ severity: 'error', message: errMsg(e) })
				},
			})
		} else {
			purge(confirmPurge, {
				onSuccess: () => {
					setConfirmPurge(null)
					setFeedback({ severity: 'success', message: 'Item permanently deleted.' })
				},
				onError: (e) => {
					setConfirmPurge(null)
					setFeedback({ severity: 'error', message: errMsg(e) })
				},
			})
		}
	}

	const busy = restoring || purging || emptying

	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
			{/* Header */}
			<Box sx={{
				display: 'flex', alignItems: 'center', gap: 1.5,
				px: 2.5, py: 1.5,
				borderBottom: '1px solid', borderColor: 'divider',
			}}>
				<DeleteIcon color="action" />
				<Typography variant="h6" sx={{ fontWeight: 700, flex: 1 }}>
					Trash
				</Typography>
				{items.length > 0 && (
					<Chip label={`${items.length} item${items.length !== 1 ? 's' : ''}`} size="small" />
				)}
				{items.length > 0 && (
					<Button
						size="small"
						color="error"
						variant="outlined"
						disabled={busy}
						onClick={() => setConfirmPurge('all')}
					>
						Empty Trash
					</Button>
				)}
			</Box>

			{/* Feedback */}
			{feedback && (
				<Alert
					severity={feedback.severity}
					onClose={() => setFeedback(null)}
					sx={{ mx: 2, mt: 1, borderRadius: 1 }}
				>
					{feedback.message}
				</Alert>
			)}

			{/* List */}
			<Box sx={{ flex: 1, overflowY: 'auto', px: 2, py: 1 }}>
				{isLoading ? (
					<Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
						<CircularProgress size={28} />
					</Box>
				) : items.length === 0 ? (
					<Typography variant="body2" color="text.disabled" sx={{ py: 4, textAlign: 'center' }}>
						Trash is empty.
					</Typography>
				) : (
					items.map((item) => {
						const meta = TYPE_META[item.rootType] ?? TYPE_META.SCENE
						const Icon = meta.icon
						return (
							<Box key={item.batchId} sx={{
								display: 'flex', alignItems: 'flex-start', gap: 1.5,
								py: 1.25, px: 1,
								borderBottom: '1px solid', borderColor: 'divider',
								'&:last-child': { borderBottom: 'none' },
							}}>
								<Icon sx={{ fontSize: 20, color: 'text.secondary', mt: 0.25 }} />
								<Box sx={{ flex: 1, minWidth: 0 }}>
									<Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
										{item.rootTitle || '(untitled)'}
									</Typography>
									<Typography variant="caption" color="text.secondary" component="div">
										{meta.label}
										{item.childCount > 0 && ` · ${item.childCount} child item${item.childCount !== 1 ? 's' : ''}`}
									</Typography>
									<Typography variant="caption" color="text.disabled" component="div">
										{item.projectTitle && `${item.projectTitle} · `}
										{formatTime(item.deletedAt)}
									</Typography>
								</Box>
								<Box sx={{ display: 'flex', gap: 0.5, flexShrink: 0, mt: 0.25 }}>
									<Button
									  size="small"
									  variant="outlined"
									  disabled={busy || !item.batchId}
									  onClick={() => handleRestore(item.batchId)}
									>
									  Restore
									</Button>
									<Tooltip title="Delete forever">
										<span>
											<IconButton
												size="small"
												color="error"
												disabled={busy}
												onClick={() => setConfirmPurge(item.batchId)}
											>
												<DeleteIcon fontSize="small" />
											</IconButton>
										</span>
									</Tooltip>
								</Box>
							</Box>
						)
					})
				)}
			</Box>

			{/* Confirm purge dialog */}
			<Dialog open={!!confirmPurge} onClose={() => setConfirmPurge(null)}>
				<DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
					<WarningAmberIcon color="error" />
					{confirmPurge === 'all' ? 'Empty Trash?' : 'Delete Forever?'}
				</DialogTitle>
				<DialogContent>
					<DialogContentText>
						{confirmPurge === 'all'
							? 'This will permanently delete all items in the trash. This action cannot be undone.'
							: 'This item and all its descendants will be permanently deleted. This action cannot be undone.'}
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setConfirmPurge(null)} disabled={purging || emptying}>
						Cancel
					</Button>
					<Button
						onClick={handlePurge}
						color="error"
						variant="contained"
						disabled={purging || emptying}
						startIcon={(purging || emptying) ? <CircularProgress size={16} color="inherit" /> : null}
					>
						{confirmPurge === 'all' ? 'Empty Trash' : 'Delete Forever'}
					</Button>
				</DialogActions>
			</Dialog>
		</Box>
	)
}
