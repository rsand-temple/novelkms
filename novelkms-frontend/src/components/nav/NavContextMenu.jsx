import { useState, useCallback, useEffect, useMemo } from 'react'
import {
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Divider,
	Menu,
	MenuItem,
	ListItemIcon,
	ListItemText,
	Typography,
} from '@mui/material'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import DeleteIcon from '@mui/icons-material/Delete'
import AddIcon from '@mui/icons-material/Add'
import DriveFileRenameOutlineIcon from '@mui/icons-material/DriveFileRenameOutline'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'

import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import AddPartDialog from './dialogs/AddPartDialog'
import AddPartChapterDialog from './dialogs/AddPartChapterDialog'
import AddCodexEntryDialog from './dialogs/AddCodexEntryDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'
import { shouldSkipDeleteConfirm } from '../../utils/deleteConfirmPrefs'
import ExportDialog from './dialogs/ExportDialog'

import { useScenes, useReorderScenes, useDeleteScene } from '../../hooks/useScenes'
import { useChapters, useReorderChapters, useDeleteChapter } from '../../hooks/useChapters'
import { useDeleteBook } from '../../hooks/useBooks'
import { useDeleteCodex, useProjectCodex, useBookCodex, useCreateProjectCodex, useCreateBookCodex } from '../../hooks/useCodex'
import {
	useParts, usePartChapters,
	useReorderParts, useReorderPartChapters,
	useDeletePart,
} from '../../hooks/useParts'
import { useRunChapterReview, useRunSceneReview } from '../../hooks/useAiReviews'
import { useAiCredentials } from '../../hooks/useAiCredentials'
import { useReview } from '../../review/ReviewContext'

// ── Context ───────────────────────────────────────────────────────────────────

import { NavContextMenuContext } from './NavContextMenuContext'

import { exportApi } from '../../api/export'

// useNavContextMenu is exported from NavContextMenuContext.js — import it from
// there in any component that needs to read rename state or open the menu.

// ── Helpers ───────────────────────────────────────────────────────────────────

const CODEX_ENTRY_LABELS = {
	CHARACTER: 'Character',
	VOICE: 'Voice Sheet',
	PLOT: 'Plot Element',
	WORLD: 'World Entry',
	TIMELINE: 'Timeline Entry',
	CANON: 'Canon Entry',
	NOTES: 'Note',
}

function getDeleteContext(type, title, codexCategory) {
	switch (type) {
		case 'scene': {
			const itemType = codexCategory
				? (CODEX_ENTRY_LABELS[codexCategory] ?? 'Entry')
				: 'Scene'

			return {
				level: 'scene',
				label: `Delete ${itemType}`,
				itemType,
			}
		}
		case 'chapter': return {
			level: 'chapter',
			label: 'Delete Chapter',
			itemType: 'Chapter',
		}
		case 'part': return {
			level: 'part',
			label: 'Delete Part',
			itemType: 'Part',
			detail: 'The part will be removed and its chapters will be moved directly under the book. This cannot be undone.',
		}
		case 'book': return {
			level: 'book',
			label: 'Delete Book',
			itemType: 'Book',
		}
		case 'codex': return {
			level: 'codex',
			label: 'Delete Codex',
			itemType: 'Codex',
		}
		default: return null
	}
}

// ── Provider ──────────────────────────────────────────────────────────────────

/**
 * NavContextMenuProvider
 *
 * Wraps the nav tree and provides:
 *  - openContextMenu(event, type, nodeData) — called by item onContextMenu handlers
 *  - renamingId — the ID currently in inline-rename mode (string | null)
 *  - startRename(id) — begin rename (also called by F2 listener)
 *  - endRename()    — cancel rename without saving
 *
 * nodeData shape:
 *   { id, title, projectId?, bookId?, partId?, chapterId? }
 *
 * navRef — a ref attached to the scrollable nav Box; F2 is only triggered when
 * focus is within that element so it doesn't fire while the editor is active.
 */
export function NavContextMenuProvider({ children, selection, setSelection, navRef }) {

	// ── Context menu position & target ────────────────────────────────────────
	const [menuPos, setMenuPos] = useState(null)   // { mouseX, mouseY } | null
	const [menuNode, setMenuNode] = useState(null)   // { type, id, title, ...ids } | null

	// ── Rename state ──────────────────────────────────────────────────────────
	const [renamingId, setRenamingId] = useState(null)

	// ── Add-dialog visibility ─────────────────────────────────────────────────
	const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
	const [exportDialogOpen, setExportDialogOpen] = useState(false)
	const [bookDialogOpen, setBookDialogOpen] = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [partDialogOpen, setPartDialogOpen] = useState(false)
	const [partChapterDialogOpen, setPartChapterDialogOpen] = useState(false)
	const [sceneDialogOpen, setSceneDialogOpen] = useState(false)
	const [entryDialogOpen, setEntryDialogOpen] = useState(false)
	const [reviewDialogOpen, setReviewDialogOpen] = useState(false)
	const [reviewError, setReviewError] = useState(null)

	// ── Sibling lists for Move Up / Down ──────────────────────────────────────
	// These queries hit the TanStack Query cache already populated by the nav
	// tree, so they produce no extra network requests in the happy path.
	// Each is conditionally enabled based on the right-clicked node type.
	const { data: sceneSiblings } = useScenes(
		menuNode?.type === 'scene' ? menuNode.chapterId : null
	)
	const { data: directChapSiblings } = useChapters(
		menuNode?.type === 'chapter' && !menuNode.partId ? menuNode.bookId : null
	)
	const { data: partChapSiblings } = usePartChapters(
		menuNode?.type === 'chapter' && menuNode.partId ? menuNode.partId : null
	)
	const { data: partSiblings } = useParts(
		menuNode?.type === 'part' ? menuNode.bookId : null
	)

	const siblings =
		menuNode?.type === 'scene' ? sceneSiblings
			: menuNode?.type === 'chapter' && !menuNode.partId ? directChapSiblings
				: menuNode?.type === 'chapter' && menuNode.partId ? partChapSiblings
					: menuNode?.type === 'part' ? partSiblings
						: null

	const siblingIndex = siblings?.findIndex(s => String(s.id) === String(menuNode?.id)) ?? -1
	const isFirst = siblingIndex <= 0
	const isLast = !siblings || siblingIndex < 0 || siblingIndex >= siblings.length - 1
	const canReorder = menuNode?.type === 'scene' || menuNode?.type === 'chapter' || menuNode?.type === 'part'

	// ── Mutations ─────────────────────────────────────────────────────────────
	const { mutate: reorderScenes } = useReorderScenes()
	const { mutate: reorderChapters } = useReorderChapters()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderParts } = useReorderParts()
	const { mutate: deleteScene, isPending: deletingScene } = useDeleteScene()
	const { mutate: deleteChapter, isPending: deletingChapter } = useDeleteChapter()
	const { mutate: deletePart, isPending: deletingPart } = useDeletePart()
	const { mutate: deleteBook, isPending: deletingBook } = useDeleteBook()
	const { mutate: deleteCodex, isPending: deletingCodex } = useDeleteCodex()
	const isDeleting = deletingScene || deletingChapter || deletingPart || deletingBook || deletingCodex

	// ── Codex existence (for conditional "Add Codex" in project/book context menus)
	const { data: ctxProjectCodex } = useProjectCodex(menuNode?.type === 'project' ? menuNode.id : null)
	const { data: ctxBookCodex } = useBookCodex(menuNode?.type === 'book' ? menuNode.id : null)
	const { mutate: createProjectCodex } = useCreateProjectCodex()
	const { mutate: createBookCodex } = useCreateBookCodex()

	// ── AI review (context menu "AI Review" item) ──────────────────────────────
	const reviewCtx = useReview()
	const { data: aiCredentials = [] } = useAiCredentials()
	const { mutate: runChapterReview, isPending: runningChapterReview } = useRunChapterReview()
	const { mutate: runSceneReview, isPending: runningSceneReview } = useRunSceneReview()
	const runningReview = runningChapterReview || runningSceneReview
	const defaultCredentialId = useMemo(
		() => aiCredentials.find(c => c.defaultCredential)?.id ?? aiCredentials[0]?.id ?? null,
		[aiCredentials],
	)
	const canRunReview = aiCredentials.length > 0
	const isManuscriptNode = !!menuNode?.bookId
	const reviewNodeType = menuNode?.type  // 'chapter' or 'scene'
	const reviewLabel =
		reviewNodeType === 'scene'
			? `scene "${menuNode?.title?.trim() || 'Untitled'}"`
			: `chapter "${menuNode?.title?.trim() || 'Untitled'}"`

	const handleRunReview = () => {
		setReviewError(null)
		const onSuccess = () => {
			setReviewDialogOpen(false)
			reviewCtx.openReview()
		}
		const onError = (e) => {
			const data = e?.response?.data
			setReviewError(data?.message ?? e?.message ?? 'Review failed.')
		}
		if (reviewNodeType === 'scene') {
			runSceneReview(
				{ sceneId: menuNode.id, credentialId: defaultCredentialId, model: null },
				{ onSuccess, onError },
			)
		} else {
			runChapterReview(
				{ chapterId: menuNode.id, credentialId: defaultCredentialId, model: null },
				{ onSuccess, onError },
			)
		}
	}

	// ── Public API ────────────────────────────────────────────────────────────

	const openContextMenu = useCallback((event, nodeType, nodeData) => {
		event.preventDefault()
		event.stopPropagation()
		setMenuPos({ mouseX: event.clientX, mouseY: event.clientY })
		setMenuNode({ type: nodeType, ...nodeData })
	}, [])

	const closeMenu = useCallback(() => {
		setMenuPos(null)
		// Keep menuNode alive until dialogs close so their props don't flicker.
		// Dialogs use menuNode.bookId / chapterId / etc. for their own IDs.
	}, [])

	const startRename = useCallback((id) => {
		setRenamingId(String(id))
		setMenuPos(null)
	}, [])

	const endRename = useCallback(() => setRenamingId(null), [])

	// ── F2 key: trigger rename on currently selected nav node ─────────────────
	useEffect(() => {
		const handleKeyDown = (e) => {
			if (e.key !== 'F2') return
			// Only fire when focus is inside the nav tree, not in the editor.
			if (!navRef?.current?.contains(document.activeElement)) return
			const selectedId =
				selection.sceneId ?? selection.chapterId ?? selection.partId ??
				selection.bookId ?? selection.projectId ?? null
			if (selectedId) {
				e.preventDefault()
				startRename(selectedId)
			}
		}
		document.addEventListener('keydown', handleKeyDown)
		return () => document.removeEventListener('keydown', handleKeyDown)
	}, [selection, startRename, navRef])

	// ── Move up / down ────────────────────────────────────────────────────────

	const dispatchReorder = (ids) => {
		if (!menuNode) return
		const { type, chapterId, partId, bookId } = menuNode
		if (type === 'scene') reorderScenes({ chapterId, ids })
		else if (type === 'chapter' && !partId) reorderChapters({ bookId, ids })
		else if (type === 'chapter' && partId) reorderPartChapters({ partId, ids })
		else if (type === 'part') reorderParts({ bookId, ids })
	}

	const handleMoveUp = () => {
		if (isFirst || !siblings) return
		const ids = siblings.map(s => s.id)
			;[ids[siblingIndex - 1], ids[siblingIndex]] = [ids[siblingIndex], ids[siblingIndex - 1]]
		dispatchReorder(ids)
		closeMenu()
	}

	const handleMoveDown = () => {
		if (isLast || !siblings) return
		const ids = siblings.map(s => s.id)
			;[ids[siblingIndex], ids[siblingIndex + 1]] = [ids[siblingIndex + 1], ids[siblingIndex]]
		dispatchReorder(ids)
		closeMenu()
	}

	// ── Delete ────────────────────────────────────────────────────────────────

	const deleteCtx = menuNode ? getDeleteContext(menuNode.type, menuNode.title, menuNode.codexCategory) : null

	const handleConfirmDelete = () => {
		if (!menuNode || !deleteCtx) return
		const { id, chapterId, bookId, projectId } = menuNode

		if (deleteCtx.level === 'scene') {
			deleteScene(
				{ id, chapterId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, sceneId: null }))
						setDeleteDialogOpen(false)
					}
				},
			)
		} else if (deleteCtx.level === 'chapter') {
			deleteChapter(
				{ id, bookId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					}
				},
			)
		} else if (deleteCtx.level === 'part') {
			deletePart(
				{ id, bookId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, partId: null, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					}
				},
			)
		} else if (deleteCtx.level === 'book') {
			deleteBook(
				{ id, projectId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, bookId: null, partId: null, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					}
				},
			)
		} else if (deleteCtx.level === 'codex') {
			deleteCodex(
				{ id },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, codexId: null, codexCategory: null, chapterId: null, sceneId: null }))
						setDeleteDialogOpen(false)
					}
				},
			)
		}
	}

	// ── Add sub-item ──────────────────────────────────────────────────────────

	const handleAddScene = () => {
		closeMenu()
		setSceneDialogOpen(true)
	}

	// ── Derived menu flags ────────────────────────────────────────────────────

	const isBookNode = menuNode?.type === 'book'
	const canDelete = deleteCtx != null  // project delete not supported

	// Export URL — derived from the right-clicked node type and id.
	// null for project nodes (no export scope for the whole project).
	const exportUrl = (() => {
		if (!menuNode) return null
		switch (menuNode.type) {
			case 'book': return exportApi.bookDocxUrl(menuNode.id)
			case 'part': return exportApi.partDocxUrl(menuNode.id)
			case 'chapter': return exportApi.chapterDocxUrl(menuNode.id)
			case 'scene': return exportApi.sceneDocxUrl(menuNode.id)
			default: return null
		}
	})()

	// ePub export is book-scoped only (there is no part/chapter/scene ePub
	// endpoint), so this is null for every other node type. It downloads
	// directly — same pattern as the AppBar's Export menu — rather than going
	// through ExportDialog's filename picker.
	const epubUrl = menuNode?.type === 'book' ? exportApi.bookEpubUrl(menuNode.id) : null

	// For the AddSceneDialog chapterId:
	// - right-clicked a chapter → chapterId = menuNode.id
	// - right-clicked a scene   → chapterId = menuNode.chapterId
	const addSceneChapterId = menuNode?.type === 'chapter' ? menuNode.id : menuNode?.chapterId

	// ── Render ────────────────────────────────────────────────────────────────

	return (
		<NavContextMenuContext.Provider value={{ openContextMenu, renamingId, startRename, endRename }}>
			{children}

			{/* ── Context menu ──────────────────────────────────────────────── */}
			<Menu
				open={Boolean(menuPos)}
				onClose={closeMenu}
				anchorReference="anchorPosition"
				anchorPosition={
					menuPos ? { top: menuPos.mouseY, left: menuPos.mouseX } : undefined
				}
				disableRestoreFocus
			>
				{/* Rename — not available for codex categories (fixed) */}
				{menuNode?.type !== 'codex-category' && (
					<MenuItem dense onClick={() => menuNode && startRename(menuNode.id)}>
						<ListItemIcon>
							<DriveFileRenameOutlineIcon fontSize="small" />
						</ListItemIcon>
						<ListItemText>Rename</ListItemText>
					</MenuItem>
				)}

				{/* Move Up / Move Down — only for orderable node types */}
				{canReorder && <Divider />}
				{canReorder && (
					<MenuItem dense onClick={handleMoveUp} disabled={isFirst || siblingIndex < 0}>
						<ListItemIcon><ArrowUpwardIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Move Up</ListItemText>
					</MenuItem>
				)}
				{canReorder && (
					<MenuItem dense onClick={handleMoveDown} disabled={isLast || siblingIndex < 0}>
						<ListItemIcon><ArrowDownwardIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Move Down</ListItemText>
					</MenuItem>
				)}

				{/* Add sub-item — context-sensitive */}
				<Divider />

				{menuNode?.type === 'project' && (
					<MenuItem dense onClick={() => { closeMenu(); setBookDialogOpen(true) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Book</ListItemText>
					</MenuItem>
				)}
				{menuNode?.type === 'project' && !ctxProjectCodex && (
					<MenuItem dense onClick={() => { closeMenu(); createProjectCodex({ projectId: menuNode.id, data: {} }) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Codex</ListItemText>
					</MenuItem>
				)}
				{isBookNode && (
					<MenuItem dense onClick={() => { closeMenu(); setPartDialogOpen(true) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Part</ListItemText>
					</MenuItem>
				)}
				{isBookNode && (
					<MenuItem dense onClick={() => { closeMenu(); setChapterDialogOpen(true) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Chapter</ListItemText>
					</MenuItem>
				)}
				{isBookNode && !ctxBookCodex && (
					<MenuItem dense onClick={() => { closeMenu(); createBookCodex({ bookId: menuNode.id, data: {} }) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Codex</ListItemText>
					</MenuItem>
				)}
				{menuNode?.type === 'part' && (
					<MenuItem dense onClick={() => { closeMenu(); setPartChapterDialogOpen(true) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Chapter</ListItemText>
					</MenuItem>
				)}
				{(menuNode?.type === 'chapter' || menuNode?.type === 'scene') && (
					<MenuItem dense onClick={handleAddScene}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Add Scene</ListItemText>
					</MenuItem>
				)}
				{menuNode?.type === 'codex-category' && (
					<MenuItem dense onClick={() => { closeMenu(); setEntryDialogOpen(true) }}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>
							{({
								CHARACTER: 'Add Character',
								VOICE: 'Add Voice Sheet',
								PLOT: 'Add Plot Element',
								WORLD: 'Add World Entry',
								TIMELINE: 'Add Timeline Entry',
								CANON: 'Add Canon Entry',
								NOTES: 'Add Note',
							})[menuNode.codexCategory] ?? 'Add Entry'}
						</ListItemText>
					</MenuItem>
				)}

				{/* Export as Word — not available for project */}
				{exportUrl && <Divider />}
				{exportUrl && (
					<MenuItem dense onClick={() => { setExportDialogOpen(true); closeMenu() }}>
						<ListItemIcon><FileDownloadIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Export as Word (.docx)</ListItemText>
					</MenuItem>
				)}

				{/* Export as ePub — book-only, direct download (no filename picker) */}
				{epubUrl && (
					<MenuItem dense onClick={() => { exportApi.download(epubUrl); closeMenu() }}>
						<ListItemIcon><FileDownloadIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Export as ePub (.epub)</ListItemText>
					</MenuItem>
				)}

				{/* AI Review — chapter and scene manuscript nodes only */}
				{isManuscriptNode && (reviewNodeType === 'chapter' || reviewNodeType === 'scene') && (
					<>
						<Divider />
						<MenuItem
							dense
							disabled={!canRunReview}
							onClick={() => { closeMenu(); setReviewError(null); setReviewDialogOpen(true) }}
						>
							<ListItemIcon><CheckCircleOutlinedIcon fontSize="small" /></ListItemIcon>
							<ListItemText>AI Review</ListItemText>
						</MenuItem>
					</>
				)}

				{/* Delete — not available for project */}
				{canDelete && <Divider />}
				{canDelete && (
					<MenuItem
						dense
						onClick={() => {
							closeMenu()
							if (shouldSkipDeleteConfirm()) handleConfirmDelete()
							else setDeleteDialogOpen(true)
						}}>
						<ListItemIcon><DeleteIcon fontSize="small" /></ListItemIcon>
						<ListItemText>{deleteCtx.label}</ListItemText>
					</MenuItem>
				)}
			</Menu>

			{/* ── Delete confirmation ────────────────────────────────────────── */}
			<DeleteConfirmDialog
				open={deleteDialogOpen}
				onClose={() => setDeleteDialogOpen(false)}
				onConfirm={handleConfirmDelete}
				itemType={deleteCtx?.itemType ?? 'item'}
				detail={deleteCtx?.detail ?? null}
				isPending={isDeleting}
			/>

			{/* ── Export dialog ───────────────────────────────────────────────── */}
			<ExportDialog
				open={exportDialogOpen}
				onClose={() => setExportDialogOpen(false)}
				url={exportUrl}
				suggestedName={menuNode?.title?.trim() || menuNode?.type || 'export'}
			/>

			{/* ── Add dialogs ────────────────────────────────────────────────── */}
			<AddBookDialog
				open={bookDialogOpen}
				onClose={() => setBookDialogOpen(false)}
				projectId={menuNode?.type === 'project' ? menuNode.id : menuNode?.projectId}
			/>
			<AddPartDialog
				open={partDialogOpen}
				onClose={() => setPartDialogOpen(false)}
				bookId={menuNode?.type === 'book' ? menuNode.id : menuNode?.bookId}
			/>
			<AddChapterDialog
				open={chapterDialogOpen}
				onClose={() => setChapterDialogOpen(false)}
				bookId={menuNode?.type === 'book' ? menuNode.id : menuNode?.bookId}
			/>
			<AddPartChapterDialog
				open={partChapterDialogOpen}
				onClose={() => setPartChapterDialogOpen(false)}
				partId={menuNode?.type === 'part' ? menuNode.id : menuNode?.partId}
			/>
			<AddSceneDialog
				open={sceneDialogOpen}
				onClose={() => setSceneDialogOpen(false)}
				chapterId={addSceneChapterId}
			/>
			<AddCodexEntryDialog
				open={entryDialogOpen}
				onClose={() => setEntryDialogOpen(false)}
				chapterId={menuNode?.type === 'codex-category' ? menuNode.id : null}
				codexCategory={menuNode?.codexCategory ?? null}
			/>

			{/* ── AI Review confirmation ─────────────────────────────────────── */}
			<Dialog
				open={reviewDialogOpen}
				onClose={() => { if (!runningReview) setReviewDialogOpen(false) }}
				maxWidth="xs"
				fullWidth
			>
				<DialogTitle>Run AI Review</DialogTitle>
				<DialogContent>
					<Typography variant="body2" sx={{ mb: 1 }}>
						Run an AI review on {reviewLabel}?
					</Typography>
					{!canRunReview && (
						<Typography variant="body2" color="error">
							No AI key is configured. Add one in Settings → AI first.
						</Typography>
					)}
					{reviewError && (
						<Typography variant="body2" color="error" sx={{ mt: 1 }}>
							{reviewError}
						</Typography>
					)}
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setReviewDialogOpen(false)} disabled={runningReview}>
						Cancel
					</Button>
					<Button
						variant="contained"
						onClick={handleRunReview}
						disabled={!canRunReview || runningReview}
						startIcon={runningReview ? <CircularProgress size={16} color="inherit" /> : null}
					>
						{runningReview ? 'Reviewing…' : 'Review'}
					</Button>
				</DialogActions>
			</Dialog>
		</NavContextMenuContext.Provider>
	)
}