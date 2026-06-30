import { useMemo, useRef, useState } from 'react'
import {
	Box, Breadcrumbs, Button, CircularProgress, Dialog, DialogActions, DialogContent,
	DialogContentText, DialogTitle, Divider, IconButton, LinearProgress, Link, List,
	ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, Snackbar, Alert,
	Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
	Tooltip, Typography,
} from '@mui/material'
import {
	DndContext, DragOverlay, PointerSensor, useSensor, useSensors, closestCenter,
	useDraggable, useDroppable,
} from '@dnd-kit/core'
import FolderSpecialIcon from '@mui/icons-material/FolderSpecial'
import FolderIcon from '@mui/icons-material/Folder'
import DescriptionIcon from '@mui/icons-material/Description'
import ImageIcon from '@mui/icons-material/Image'
import AddIcon from '@mui/icons-material/Add'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import DriveFileRenameOutlineIcon from '@mui/icons-material/DriveFileRenameOutline'
import DeleteIcon from '@mui/icons-material/Delete'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import {
	useArtifactTree, useArtifactUsage, useCreateArtifactFolder, useUploadArtifactFile,
	useRenameArtifactNode, useMoveArtifactNode, useTrashArtifactNode, artifactErrorMessage,
} from '../../hooks/useArtifacts'
import { artifactsApi } from '../../api/artifacts'
import { formatBytes } from '../../utils/formatBytes'

const PANEL_HEADER_HEIGHT = 48

function isImage(ct) {
	return typeof ct === 'string' && ct.startsWith('image/')
}

function formatWhen(iso) {
	if (!iso) return '—'
	const d = new Date(iso)
	if (Number.isNaN(d.getTime())) return '—'
	return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
		+ ' ' + d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

/**
 * The Artifacts Explorer — a Windows-style details view rendered in the center
 * pane when selection.artifactFolderId is set. It owns its own dnd-kit
 * DndContext (drag a row onto a folder, or onto "Up", to move it) entirely
 * separate from the manuscript nav DndContext. Folders are shown in the nav
 * tree; this pane shows the open folder's full contents (folders and files).
 *
 * folderId is null at the project root, otherwise a folder node id. Navigation
 * is driven through setSelection so the nav tree and breadcrumb stay in sync.
 */
export default function ArtifactsPanel({ projectId, folderId, setSelection }) {
	const { data: tree, isLoading } = useArtifactTree(projectId)
	const { data: usage } = useArtifactUsage(projectId)

	const createFolder = useCreateArtifactFolder()
	const uploadFile = useUploadArtifactFile()
	const renameNode = useRenameArtifactNode()
	const moveNode = useMoveArtifactNode()
	const trashNode = useTrashArtifactNode()

	const fileInputRef = useRef(null)
	const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

	const [activeDrag, setActiveDrag] = useState(null)        // node being dragged (overlay)
	const [rowMenu, setRowMenu] = useState(null)              // { mouseX, mouseY, node }
	const [newFolderOpen, setNewFolderOpen] = useState(false)
	const [renameTarget, setRenameTarget] = useState(null)    // node
	const [moveTarget, setMoveTarget] = useState(null)        // node
	const [uploading, setUploading] = useState(0)             // count remaining
	const [snack, setSnack] = useState(null)                  // { severity, message }

	const nodes = useMemo(() => tree ?? [], [tree])

	// The folder whose contents we are showing (null object => virtual root).
	const currentFolder = folderId ? nodes.find(n => n.id === folderId && n.type === 'FOLDER') ?? null : null
	const effectiveParentId = currentFolder ? currentFolder.id : null

	// Breadcrumb chain: root → … → current.
	const trail = useMemo(() => {
		const chain = []
		let cursor = currentFolder
		while (cursor) {
			chain.unshift(cursor)
			cursor = cursor.parentId ? nodes.find(n => n.id === cursor.parentId) : null
		}
		return chain
	}, [currentFolder, nodes])

	const children = useMemo(() => {
		const here = nodes.filter(n => (n.parentId ?? null) === effectiveParentId)
		const folders = here.filter(n => n.type === 'FOLDER')
		const files = here.filter(n => n.type === 'FILE')
		const byName = (a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name)
		return [...folders.sort(byName), ...files.sort(byName)]
	}, [nodes, effectiveParentId])

	// ── Navigation ───────────────────────────────────────────────────────────
	const navigateTo = (id) =>
		setSelection(prev => ({ ...prev, artifactFolderId: id ?? 'root' }))

	const goUp = () => navigateTo(currentFolder?.parentId ?? 'root')

	// ── Actions ──────────────────────────────────────────────────────────────
	const handleCreateFolder = (name) => {
		createFolder.mutate(
			{ projectId, parentId: effectiveParentId, name },
			{
				onSuccess: () => setNewFolderOpen(false),
				onError: (e) => setSnack({ severity: 'error', message: artifactErrorMessage(e) }),
			},
		)
	}

	const handleRename = (name) => {
		const node = renameTarget
		renameNode.mutate(
			{ nodeId: node.id, name, projectId },
			{
				onSuccess: () => setRenameTarget(null),
				onError: (e) => setSnack({ severity: 'error', message: artifactErrorMessage(e) }),
			},
		)
	}

	const handleMove = (newParentId) => {
		const node = moveTarget
		moveNode.mutate(
			{ nodeId: node.id, parentId: newParentId, projectId },
			{
				onSuccess: () => setMoveTarget(null),
				onError: (e) => setSnack({ severity: 'error', message: artifactErrorMessage(e) }),
			},
		)
	}

	const handleDelete = (node) => {
		trashNode.mutate(
			{ nodeId: node.id, projectId },
			{ onError: (e) => setSnack({ severity: 'error', message: artifactErrorMessage(e) }) },
		)
	}

	const handleDownload = (node) => {
		const a = document.createElement('a')
		a.href = artifactsApi.downloadUrl(node.id)
		a.download = node.name
		document.body.appendChild(a)
		a.click()
		a.remove()
	}

	const handleUploadClick = () => fileInputRef.current?.click()

	const handleFilesChosen = async (e) => {
		const files = Array.from(e.target.files ?? [])
		e.target.value = ''  // allow re-choosing the same file later
		if (!files.length) return
		setUploading(files.length)
		for (const file of files) {
			try {
				await uploadFile.mutateAsync({ projectId, parentId: effectiveParentId, file })
			} catch (err) {
				setSnack({ severity: 'error', message: artifactErrorMessage(err) })
			} finally {
				setUploading(c => Math.max(0, c - 1))
			}
		}
	}

	// ── Drag-to-move (isolated DndContext) ─────────────────────────────────────
	const handleDragStart = ({ active }) => setActiveDrag(active.data.current?.node ?? null)

	const handleDragEnd = ({ active, over }) => {
		setActiveDrag(null)
		if (!over) return
		const node = active.data.current?.node
		if (!node) return

		let targetParentId
		if (over.id === 'drop:up') {
			targetParentId = currentFolder?.parentId ?? null
		} else if (typeof over.id === 'string' && over.id.startsWith('drop:')) {
			targetParentId = over.id.slice('drop:'.length)
			if (targetParentId === node.id) return  // onto itself
		} else {
			return
		}
		if ((node.parentId ?? null) === (targetParentId ?? null)) return  // no-op

		moveNode.mutate(
			{ nodeId: node.id, parentId: targetParentId, projectId },
			{ onError: (e) => setSnack({ severity: 'error', message: artifactErrorMessage(e) }) },
		)
	}

	// ── Render ─────────────────────────────────────────────────────────────────
	const usedBytes = usage?.usedBytes ?? 0
	const quotaBytes = usage?.quotaBytes ?? 0
	const usedPct = quotaBytes > 0 ? Math.min(100, (usedBytes / quotaBytes) * 100) : 0

	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
			{/* Header */}
			<Box sx={{
				height: PANEL_HEADER_HEIGHT, flexShrink: 0, display: 'flex', alignItems: 'center',
				gap: 1.25, px: 1.75, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper',
			}}>
				<FolderSpecialIcon sx={{ fontSize: 19, color: 'primary.main' }} />
				<Box sx={{ minWidth: 0, flex: 1 }}>
					<Typography variant="subtitle2" sx={{ fontWeight: 700, lineHeight: 1.15 }}>Artifacts</Typography>
					<Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.15 }}>
						Project files — download only
					</Typography>
				</Box>
			</Box>

			{/* Toolbar */}
			<Stack direction="row" spacing={1} sx={{ alignItems: 'center', px: 1.5, py: 1, flexShrink: 0 }}>
				<Tooltip title="Up one level">
					<span>
						<IconButton size="small" onClick={goUp} disabled={!currentFolder}>
							<ArrowUpwardIcon fontSize="small" />
						</IconButton>
					</span>
				</Tooltip>
				<Button size="small" startIcon={<AddIcon />} onClick={() => setNewFolderOpen(true)}>
					New folder
				</Button>
				<Button size="small" startIcon={<UploadFileIcon />} onClick={handleUploadClick}>
					Upload
				</Button>
				<input
					ref={fileInputRef}
					type="file"
					multiple
					hidden
					onChange={handleFilesChosen}
				/>
				<Box sx={{ flex: 1 }} />
				{uploading > 0 && (
					<Typography variant="caption" color="text.secondary">
						Uploading {uploading}…
					</Typography>
				)}
			</Stack>

			{/* Breadcrumb */}
			<Box sx={{ px: 1.75, pb: 1, flexShrink: 0 }}>
				<Breadcrumbs separator="›" maxItems={6}>
					<Link component="button" underline="hover" color={currentFolder ? 'inherit' : 'text.primary'}
						onClick={() => navigateTo('root')} sx={{ fontSize: '0.82rem' }}>
						Artifacts
					</Link>
					{trail.map((f, i) => {
						const last = i === trail.length - 1
						return last
							? <Typography key={f.id} color="text.primary" sx={{ fontSize: '0.82rem' }}>{f.name}</Typography>
							: <Link key={f.id} component="button" underline="hover" color="inherit"
								onClick={() => navigateTo(f.id)} sx={{ fontSize: '0.82rem' }}>{f.name}</Link>
					})}
				</Breadcrumbs>
			</Box>

			{(uploading > 0 || createFolder.isPending) && <LinearProgress />}
			<Divider />

			{/* Details table */}
			<Box sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
				{isLoading ? (
					<Box sx={{ p: 3, display: 'flex', justifyContent: 'center' }}><CircularProgress size={24} /></Box>
				) : (
					<DndContext sensors={sensors} collisionDetection={closestCenter}
						onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={() => setActiveDrag(null)}>
						<TableContainer>
							<Table size="small" stickyHeader>
								<TableHead>
									<TableRow>
										<TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
										<TableCell sx={{ fontWeight: 700, width: 120 }}>Type</TableCell>
										<TableCell sx={{ fontWeight: 700, width: 90 }} align="right">Size</TableCell>
										<TableCell sx={{ fontWeight: 700, width: 180 }}>Modified</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{currentFolder && (
										<UpRow onOpen={goUp} />
									)}
									{children.length === 0 && (
										<TableRow>
											<TableCell colSpan={4}>
												<Typography variant="body2" color="text.disabled" sx={{ py: 2 }}>
													This folder is empty. Use New folder or Upload to add items.
												</Typography>
											</TableCell>
										</TableRow>
									)}
									{children.map((node) => (
										<ArtifactRow
											key={node.id}
											node={node}
											onOpenFolder={() => navigateTo(node.id)}
											onContextMenu={(e) => {
												e.preventDefault()
												setRowMenu({ mouseX: e.clientX, mouseY: e.clientY, node })
											}}
											onDownload={() => handleDownload(node)}
										/>
									))}
								</TableBody>
							</Table>
						</TableContainer>

						<DragOverlay dropAnimation={null}>
							{activeDrag && (
								<Box sx={{
									display: 'inline-flex', alignItems: 'center', gap: 0.75, px: 1.25, py: 0.5,
									bgcolor: 'background.paper', border: '1px solid', borderColor: 'primary.main',
									borderRadius: 1, boxShadow: 4, fontSize: 13,
								}}>
									{activeDrag.type === 'FOLDER'
										? <FolderIcon fontSize="small" sx={{ color: 'text.secondary' }} />
										: <DescriptionIcon fontSize="small" sx={{ color: 'text.secondary' }} />}
									{activeDrag.name}
								</Box>
							)}
						</DragOverlay>
					</DndContext>
				)}
			</Box>

			{/* Usage footer */}
			<Divider />
			<Box sx={{ px: 1.75, py: 1, flexShrink: 0 }}>
				<Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.5 }}>
					<Typography variant="caption" color="text.secondary">
						Storage: {formatBytes(usedBytes)} of {formatBytes(quotaBytes)} used
					</Typography>
					{usage?.maxFileSizeBytes != null && (
						<Typography variant="caption" color="text.secondary">
							Max file size {formatBytes(usage.maxFileSizeBytes)}
						</Typography>
					)}
				</Stack>
				<LinearProgress variant="determinate" value={usedPct}
					color={usedPct > 90 ? 'error' : usedPct > 75 ? 'warning' : 'primary'}
					sx={{ height: 6, borderRadius: 3 }} />
			</Box>

			{/* Row context menu */}
			<Menu
				open={!!rowMenu}
				onClose={() => setRowMenu(null)}
				anchorReference="anchorPosition"
				anchorPosition={rowMenu ? { top: rowMenu.mouseY, left: rowMenu.mouseX } : undefined}
			>
				{rowMenu?.node?.type === 'FILE' && (
					<MenuItem onClick={() => { handleDownload(rowMenu.node); setRowMenu(null) }}>
						<ListItemIcon><FileDownloadIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Download</ListItemText>
					</MenuItem>
				)}
				<MenuItem onClick={() => { setRenameTarget(rowMenu.node); setRowMenu(null) }}>
					<ListItemIcon><DriveFileRenameOutlineIcon fontSize="small" /></ListItemIcon>
					<ListItemText>Rename…</ListItemText>
				</MenuItem>
				<MenuItem onClick={() => { setMoveTarget(rowMenu.node); setRowMenu(null) }}>
					<ListItemIcon><FolderIcon fontSize="small" /></ListItemIcon>
					<ListItemText>Move to…</ListItemText>
				</MenuItem>
				<Divider />
				<MenuItem onClick={() => { handleDelete(rowMenu.node); setRowMenu(null) }}>
					<ListItemIcon><DeleteIcon fontSize="small" /></ListItemIcon>
					<ListItemText>Move to Trash</ListItemText>
				</MenuItem>
			</Menu>

			{/* Dialogs */}
			{newFolderOpen && (
				<NameDialog
					title="New folder"
					label="Folder name"
					initial=""
					confirmText="Create"
					busy={createFolder.isPending}
					onCancel={() => setNewFolderOpen(false)}
					onConfirm={handleCreateFolder}
				/>
			)}
			{renameTarget && (
				<NameDialog
					title="Rename"
					label="Name"
					initial={renameTarget.name}
					confirmText="Rename"
					busy={renameNode.isPending}
					onCancel={() => setRenameTarget(null)}
					onConfirm={handleRename}
				/>
			)}
			{moveTarget && (
				<MoveDialog
					node={moveTarget}
					nodes={nodes}
					busy={moveNode.isPending}
					onCancel={() => setMoveTarget(null)}
					onConfirm={handleMove}
				/>
			)}

			<Snackbar
				open={!!snack}
				autoHideDuration={6000}
				onClose={() => setSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{snack ? (
					<Alert severity={snack.severity} onClose={() => setSnack(null)} variant="filled">
						{snack.message}
					</Alert>
				) : undefined}
			</Snackbar>
		</Box>
	)
}

// ── A draggable / droppable details row ──────────────────────────────────────
function ArtifactRow({ node, onOpenFolder, onContextMenu, onDownload }) {
	const isFolder = node.type === 'FOLDER'
	const { attributes, listeners, setNodeRef: dragRef, isDragging } = useDraggable({
		id: node.id,
		data: { node },
	})
	const { setNodeRef: dropRef, isOver } = useDroppable({
		id: `drop:${node.id}`,
		disabled: !isFolder,
	})

	const setRefs = (el) => { dragRef(el); if (isFolder) dropRef(el) }

	return (
		<TableRow
			ref={setRefs}
			hover
			onContextMenu={onContextMenu}
			onDoubleClick={() => { if (isFolder) onOpenFolder(); else onDownload() }}
			{...attributes}
			{...listeners}
			sx={{
				cursor: 'pointer',
				opacity: isDragging ? 0.4 : 1,
				bgcolor: isOver && isFolder ? 'action.hover' : undefined,
				outline: isOver && isFolder ? '2px solid' : 'none',
				outlineColor: 'primary.main',
			}}
		>
			<TableCell>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
					{isFolder
						? <FolderIcon fontSize="small" sx={{ color: 'text.secondary' }} />
						: (isImage(node.contentType)
							? <ImageIcon fontSize="small" sx={{ color: 'text.secondary' }} />
							: <DescriptionIcon fontSize="small" sx={{ color: 'text.secondary' }} />)}
					<Typography variant="body2" noWrap>{node.name}</Typography>
				</Stack>
			</TableCell>
			<TableCell><Typography variant="caption" color="text.secondary">{isFolder ? 'Folder' : (node.contentType || 'File')}</Typography></TableCell>
			<TableCell align="right"><Typography variant="caption" color="text.secondary">{isFolder ? '—' : formatBytes(node.sizeBytes)}</Typography></TableCell>
			<TableCell><Typography variant="caption" color="text.secondary">{formatWhen(node.updatedAt)}</Typography></TableCell>
		</TableRow>
	)
}

// ── The ".." up row (droppable target for moving out one level) ───────────────
function UpRow({ onOpen }) {
	const { setNodeRef, isOver } = useDroppable({ id: 'drop:up' })
	return (
		<TableRow ref={setNodeRef} hover onDoubleClick={onOpen}
			sx={{ cursor: 'pointer', bgcolor: isOver ? 'action.hover' : undefined }}>
			<TableCell>
				<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
					<ArrowUpwardIcon fontSize="small" sx={{ color: 'text.secondary' }} />
					<Typography variant="body2" color="text.secondary">..</Typography>
				</Stack>
			</TableCell>
			<TableCell /><TableCell /><TableCell />
		</TableRow>
	)
}

// ── Name dialog (new folder / rename) ─────────────────────────────────────────
function NameDialog({ title, label, initial, confirmText, busy, onCancel, onConfirm }) {
	const [value, setValue] = useState(initial)
	const trimmed = value.trim()
	const submit = () => { if (trimmed) onConfirm(trimmed) }
	return (
		<Dialog open onClose={onCancel} maxWidth="xs" fullWidth>
			<DialogTitle>{title}</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus fullWidth size="small" label={label} value={value}
					onChange={(e) => setValue(e.target.value)}
					onKeyDown={(e) => { if (e.key === 'Enter') submit() }}
					sx={{ mt: 1 }}
				/>
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel}>Cancel</Button>
				<Button variant="contained" onClick={submit} disabled={!trimmed || busy}>{confirmText}</Button>
			</DialogActions>
		</Dialog>
	)
}

// ── Move dialog (pick a destination folder) ───────────────────────────────────
function MoveDialog({ node, nodes, busy, onCancel, onConfirm }) {
	// Exclude the node itself and its descendants (cannot move a folder inside
	// itself — the backend rejects it too, this just keeps the picker clean).
	const blocked = useMemo(() => {
		const set = new Set([node.id])
		let changed = true
		while (changed) {
			changed = false
			for (const n of nodes) {
				if (n.type === 'FOLDER' && n.parentId && set.has(n.parentId) && !set.has(n.id)) {
					set.add(n.id); changed = true
				}
			}
		}
		return set
	}, [node, nodes])

	const folders = nodes
		.filter(n => n.type === 'FOLDER' && !blocked.has(n.id))
		.sort((a, b) => a.name.localeCompare(b.name))

	const [dest, setDest] = useState(null)  // null = root

	return (
		<Dialog open onClose={onCancel} maxWidth="xs" fullWidth>
			<DialogTitle>Move “{node.name}”</DialogTitle>
			<DialogContent dividers sx={{ p: 0 }}>
				<DialogContentText sx={{ px: 2, pt: 1.5, pb: 0.5, fontSize: '0.8rem' }}>
					Choose a destination folder:
				</DialogContentText>
				<List dense sx={{ maxHeight: 320, overflow: 'auto' }}>
					<ListItemButton selected={dest === null} onClick={() => setDest(null)}>
						<ListItemIcon sx={{ minWidth: 32 }}><FolderSpecialIcon fontSize="small" /></ListItemIcon>
						<ListItemText primary="Artifacts (root)" />
					</ListItemButton>
					{folders.map((f) => (
						<ListItemButton key={f.id} selected={dest === f.id} onClick={() => setDest(f.id)}>
							<ListItemIcon sx={{ minWidth: 32 }}><FolderIcon fontSize="small" /></ListItemIcon>
							<ListItemText primary={f.name} />
						</ListItemButton>
					))}
				</List>
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel}>Cancel</Button>
				<Button variant="contained" disabled={busy} onClick={() => onConfirm(dest)}>Move</Button>
			</DialogActions>
		</Dialog>
	)
}
