import { useEffect, useMemo, useRef, useState } from 'react'
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
import FolderZipIcon from '@mui/icons-material/FolderZip'
import DescriptionIcon from '@mui/icons-material/Description'
import ImageIcon from '@mui/icons-material/Image'
import AddIcon from '@mui/icons-material/Add'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import DriveFileRenameOutlineIcon from '@mui/icons-material/DriveFileRenameOutline'
import DeleteIcon from '@mui/icons-material/Delete'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import {
	useArtifactTree, useArtifactUsage, useArtifactText, useSaveArtifactText,
	useCreateArtifactFolder, useUploadArtifactFile,
	useRenameArtifactNode, useMoveArtifactNode, useTrashArtifactNode, artifactErrorMessage,
} from '../../hooks/useArtifacts'
import { artifactsApi } from '../../api/artifacts'
import { formatBytes } from '../../utils/formatBytes'

const PANEL_HEADER_HEIGHT = 48
const EMPTY_NODES = []

function isImage(ct) {
	return typeof ct === 'string' && ct.startsWith('image/')
}

function isText(ct) {
	return typeof ct === 'string' && ct.startsWith('text/')
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
export default function ArtifactsPanel({ projectId, folderId, editingNodeId, setSelection }) {
	const { data: tree, isLoading } = useArtifactTree(projectId)
	const { data: usage } = useArtifactUsage(projectId)

	const createFolder = useCreateArtifactFolder()
	const uploadFile = useUploadArtifactFile()
	const renameNode = useRenameArtifactNode()
	const moveNode = useMoveArtifactNode()
	const trashNode = useTrashArtifactNode()

	const fileInputRef = useRef(null)
	const dropZoneRef = useRef(null)
	const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

	const [activeDrag, setActiveDrag] = useState(null)        // node being dragged (overlay)
	const [rowMenu, setRowMenu] = useState(null)              // { mouseX, mouseY, node }
	const [newFolderOpen, setNewFolderOpen] = useState(false)
	const [renameTarget, setRenameTarget] = useState(null)    // node
	const [moveTarget, setMoveTarget] = useState(null)        // node
	const [previewNode, setPreviewNode] = useState(null)      // image node being previewed
	const [uploading, setUploading] = useState(0)             // count remaining
	const [snack, setSnack] = useState(null)                  // { severity, message }
	const [nativeDragOver, setNativeDragOver] = useState(false) // OS file drag hovering

	const nodes = tree ?? EMPTY_NODES

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

	// Triggers a browser download of all project artifacts as a zip archive.
	// The server streams the zip on the fly — no temp file persists after the
	// response completes. The browser handles the download natively; no React
	// state or mutation is needed.
	const handleExportAll = () => {
		window.location.href = artifactsApi.exportUrl(projectId)
	}

	const handleUploadClick = () => fileInputRef.current?.click()

	const handleFilesChosen = async (e) => {
		const files = Array.from(e.target.files ?? [])
		e.target.value = ''  // allow re-choosing the same file later
		if (!files.length) return
		await doUploadFiles(files)
	}

	const doUploadFiles = async (files) => {
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

	// ── Native OS file drag-and-drop ──────────────────────────────────────────
	// Registers on `window` directly — NOT on the panel element, NOT via React
	// props. This is the only approach guaranteed to call preventDefault() before
	// the browser navigates to the dropped file, regardless of React Compiler
	// transformations, MUI Box forwarding, or ref timing.
	//
	// dragover → always preventDefault (prevents browser file-open)
	// drop     → always preventDefault, then check if target is inside this
	//            panel (via ref.contains) and upload if so
	const uploadContextRef = useRef({ projectId, effectiveParentId })

	useEffect(() => {
		uploadContextRef.current = { projectId, effectiveParentId }
	}, [projectId, effectiveParentId])

	useEffect(() => {
		let counter = 0

		const onDragEnter = (e) => {
			e.preventDefault()
			counter++
			if (counter === 1 && e.dataTransfer?.types?.includes('Files')) {
				setNativeDragOver(true)
			}
		}

		const onDragOver = (e) => {
			// This single line is what prevents the browser from opening the file.
			e.preventDefault()
			if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
		}

		const onDragLeave = (e) => {
			e.preventDefault()
			counter--
			if (counter <= 0) { counter = 0; setNativeDragOver(false) }
		}

		const onDrop = (e) => {
			e.preventDefault()
			counter = 0
			setNativeDragOver(false)

			// Only upload if the drop landed inside this panel.
			const el = dropZoneRef.current
			if (!el || !el.contains(e.target)) return

			const files = Array.from(e.dataTransfer?.files ?? [])
			if (files.length) {
				const ctx = uploadContextRef.current
				setUploading(files.length)
					; (async () => {
						for (const file of files) {
							try {
								await uploadFile.mutateAsync({
									projectId: ctx.projectId,
									parentId: ctx.effectiveParentId,
									file,
								})
							} catch (err) {
								setSnack({ severity: 'error', message: artifactErrorMessage(err) })
							} finally {
								setUploading(c => Math.max(0, c - 1))
							}
						}
					})()
			}
		}

		window.addEventListener('dragenter', onDragEnter)
		window.addEventListener('dragover', onDragOver)
		window.addEventListener('dragleave', onDragLeave)
		window.addEventListener('drop', onDrop)

		return () => {
			window.removeEventListener('dragenter', onDragEnter)
			window.removeEventListener('dragover', onDragOver)
			window.removeEventListener('dragleave', onDragLeave)
			window.removeEventListener('drop', onDrop)
		}
		// uploadFile is a stable mutation object from useMutation
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [])

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
		<Box
			ref={dropZoneRef}
			sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, position: 'relative' }}
		>
			{/* Native file-drop overlay */}
			{nativeDragOver && (
				<Box sx={{
					position: 'absolute', inset: 0, zIndex: 20,
					display: 'flex', alignItems: 'center', justifyContent: 'center',
					bgcolor: 'action.hover',
					border: '3px dashed', borderColor: 'primary.main', borderRadius: 2,
					pointerEvents: 'none',
				}}>
					<Typography variant="h6" color="primary" sx={{ fontWeight: 600 }}>
						Drop files here to upload
					</Typography>
				</Box>
			)}
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

			{editingNodeId ? (
				<ArtifactTextEditor
					nodeId={editingNodeId}
					projectId={projectId}
					nodes={nodes}
					onClose={() => setSelection(prev => ({ ...prev, artifactEditingNodeId: null }))}
					onSnack={setSnack}
				/>
			) : (<>
				{/* Toolbar */}
				<Stack direction="row" spacing={1} sx={{ alignItems: "center", px: 1.5, py: 1, flexShrink: 0 }}>
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
					<Tooltip title="Download all artifacts as a zip file">
						<Button size="small" startIcon={<FolderZipIcon />} onClick={handleExportAll}>
							Download all
						</Button>
					</Tooltip>
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
												onPreview={() => setPreviewNode(node)}
												onEdit={(n) => setSelection(prev => ({ ...prev, artifactEditingNodeId: n.id }))}
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
					<Stack direction="row" sx={{ justifyContent: "space-between", mb: 0.5 }}>
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
					{rowMenu?.node?.type === 'FILE' && isImage(rowMenu.node.contentType) && (
						<MenuItem onClick={() => { setPreviewNode(rowMenu.node); setRowMenu(null) }}>
							<ListItemIcon><ImageIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Preview</ListItemText>
						</MenuItem>
					)}
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

				{previewNode && (
					<PreviewDialog
						node={previewNode}
						onDownload={() => handleDownload(previewNode)}
						onClose={() => setPreviewNode(null)}
					/>
				)}

			</>)}

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
function ArtifactRow({ node, onOpenFolder, onContextMenu, onDownload, onPreview, onEdit }) {
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

	const handleDoubleClick = () => {
		if (isFolder) { onOpenFolder(); return }
		if (isImage(node.contentType) && onPreview) { onPreview(node); return }
		if (isText(node.contentType) && onEdit) { onEdit(node); return }
		onDownload()
	}

	return (
		<TableRow
			ref={setRefs}
			hover
			onContextMenu={onContextMenu}
			onDoubleClick={handleDoubleClick}
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
				<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
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
				<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
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
			<DialogTitle>Move "{node.name}"</DialogTitle>
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

// ── Image preview dialog (read-only, download + close) ───────────────────────
function PreviewDialog({ node, onDownload, onClose }) {
	// loaded/error are driven by the <img> element's own events, not effects.
	const [loaded, setLoaded] = useState(false)
	const [errored, setErrored] = useState(false)
	const src = artifactsApi.downloadUrl(node.id)

	return (
		<Dialog open onClose={onClose} maxWidth="md" fullWidth>
			<DialogTitle sx={{ pr: 6 }}>
				<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
					<ImageIcon fontSize="small" sx={{ color: 'text.secondary' }} />
					<Typography variant="subtitle1" sx={{ fontWeight: 600 }} noWrap>{node.name}</Typography>
				</Stack>
			</DialogTitle>
			<DialogContent
				dividers
				sx={{
					display: 'flex', alignItems: 'center', justifyContent: 'center',
					minHeight: 200, bgcolor: 'action.hover', p: 2,
				}}
			>
				{errored ? (
					<Typography color="error" variant="body2">
						Couldn't load this image for preview. Try downloading it instead.
					</Typography>
				) : (
					<>
						{!loaded && <CircularProgress size={28} />}
						<Box
							component="img"
							src={src}
							alt={node.name}
							onLoad={() => setLoaded(true)}
							onError={() => setErrored(true)}
							sx={{
								display: loaded ? 'block' : 'none',
								maxWidth: '100%',
								maxHeight: '70vh',
								objectFit: 'contain',
							}}
						/>
					</>
				)}
			</DialogContent>
			<DialogActions>
				<Button startIcon={<FileDownloadIcon />} onClick={onDownload}>Download</Button>
				<Button variant="contained" onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	)
}
function ArtifactTextEditor({ nodeId, projectId, nodes, onClose, onSnack }) {
	const { data: loadedText, isLoading, isError } = useArtifactText(nodeId)
	const saveMutation = useSaveArtifactText()
	const [text, setText] = useState(null) // null until loaded text arrives
	const [savedText, setSavedText] = useState(null)

	const node = nodes.find(n => n.id === nodeId)
	const fileName = node?.name ?? 'Untitled'

	// Seed the textarea once data arrives (no useEffect setState — conditional mount).
	if (loadedText != null && text === null) {
		setText(loadedText)
		setSavedText(loadedText)
	}

	const changed = text !== null && text !== savedText

	const handleSave = () => {
		saveMutation.mutate(
			{ nodeId, text, projectId },
			{
				onSuccess: () => { setSavedText(text) },
				onError: (e) => onSnack({ severity: 'error', message: artifactErrorMessage(e) }),
			},
		)
	}

	const handleCancel = () => {
		setText(savedText)
	}

	const handleClose = () => {
		if (changed && !window.confirm('You have unsaved changes. Discard them and close?')) {
			return
		}
		onClose()
	}

	if (isLoading) {
		return (
			<Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
				<CircularProgress size={24} />
			</Box>
		)
	}

	if (isError) {
		return (
			<Box sx={{ flex: 1, p: 3 }}>
				<Typography color="error">Failed to load file contents.</Typography>
				<Button onClick={onClose} sx={{ mt: 2 }}>Close</Button>
			</Box>
		)
	}

	return (
		<>
			{/* Editor toolbar */}
			<Stack direction="row" spacing={1} sx={{ alignItems: "center", px: 1.5, py: 1, flexShrink: 0 }}>
				<DescriptionIcon fontSize="small" sx={{ color: 'text.secondary' }} />
				<Typography variant="body2" sx={{ fontWeight: 600, flex: 1 }} noWrap>
					{fileName}
					{changed && <Typography component="span" color="warning.main" sx={{ ml: 0.75, fontWeight: 400, fontSize: '0.75rem' }}>• unsaved</Typography>}
				</Typography>
				<Button size="small" variant="contained" onClick={handleSave}
					disabled={!changed || saveMutation.isPending}>
					Save
				</Button>
				<Button size="small" onClick={handleCancel} disabled={!changed}>
					Cancel
				</Button>
				<Button size="small" onClick={handleClose}>
					Close
				</Button>
			</Stack>
			{saveMutation.isPending && <LinearProgress />}
			<Divider />

			{/* Monospace textarea */}
			<Box
				component="textarea"
				value={text ?? ''}
				onChange={(e) => setText(e.target.value)}
				spellCheck
				sx={{
					flex: 1,
					minHeight: 0,
					m: 0,
					p: 2,
					border: 'none',
					outline: 'none',
					resize: 'none',
					fontFamily: '"Cascadia Code", "Fira Code", "Consolas", "Monaco", monospace',
					fontSize: '0.875rem',
					lineHeight: 1.7,
					whiteSpace: 'pre-wrap',
					overflowWrap: 'break-word',
					bgcolor: 'background.paper',
					color: 'text.primary',
					overflowY: 'auto',
				}}
			/>
		</>
	)
}
