import { useState, useCallback, useEffect, useMemo } from 'react'
import {
	Alert,
	Button,
	Snackbar,
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
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import CloseIcon from '@mui/icons-material/Close'
import ScheduleIcon from '@mui/icons-material/Schedule'

import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import AddPartDialog from './dialogs/AddPartDialog'
import AddPartChapterDialog from './dialogs/AddPartChapterDialog'
import AddCodexEntryDialog from './dialogs/AddCodexEntryDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'
import { shouldSkipDeleteConfirm } from '../../utils/deleteConfirmPrefs'
import ExportDialog from './dialogs/ExportDialog'
import PreReviewMemoryDialog from '../ai/PreReviewMemoryDialog'
import { flaggedPreceding } from '../ai/memoryStatus'
import BookSummaryDialog from '../ai/BookSummaryDialog'
import ManageAiContextDialog from '../ai/ManageAiContextDialog'
import ChapterReviewHistoryDialog from '../ai/ChapterReviewHistoryDialog'

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
import { useGenerateChapterMemory, useChapterMemoryStatus, useDeleteChapterMemory } from '../../hooks/useChapterMemory'
import { useGenerateChapterSummary, useBookChapterSummaries, useDeleteChapterSummary } from '../../hooks/useSummary'
import { useGenerateChapterEditorial, useDeleteChapterEditorial, useChapterEditorial } from '../../hooks/useEditorial'
import { useSetScenePinned, useSetCategoryPinned } from '../../hooks/useAiContext'
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
	const [historyDialogOpen, setHistoryDialogOpen] = useState(false)

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

	// ── AI reference context (share Codex entries with the AI) ─────────────────
	const { mutate: setScenePinned } = useSetScenePinned()
	const { mutate: setCategoryPinned } = useSetCategoryPinned()
	const [manageContextCodexId, setManageContextCodexId] = useState(null)
	const [manageContextTitle, setManageContextTitle] = useState('')

	// Chapter memory document (nav "Generate / Edit memory document").
	const { mutate: generateMemory, isPending: generatingMemory } = useGenerateChapterMemory()
	const [memorySnack, setMemorySnack] = useState(null) // { severity, message } | null
	const { data: chapterMemStatus = [] } = useChapterMemoryStatus(
		menuNode?.type === 'chapter' ? menuNode?.bookId : null,
	)
	const { mutate: clearMemory } = useDeleteChapterMemory()
	const [gateOpen, setGateOpen] = useState(false)
	const [gateNode, setGateNode] = useState(null)
	const [gateFlagged, setGateFlagged] = useState([])
	const [clearOpen, setClearOpen] = useState(false)
	const [clearNode, setClearNode] = useState(null)
	const menuChapterState = chapterMemStatus.find(s => s.chapterId === menuNode?.id)?.state
	const menuChapterHasDoc = !!menuChapterState && menuChapterState !== 'MISSING'
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

	// ── AI reference context handlers ──────────────────────────────────────────

	// Toggle one Codex entry. menuNode (type 'scene', codexCategory set) carries
	// its current aiContextPinned so the menu shows the right verb.
	const handleToggleEntryPin = () => {
		const node = menuNode
		closeMenu()
		if (!node) return
		const next = !node.aiContextPinned
		setScenePinned(
			{ sceneId: node.id, chapterId: node.chapterId, codexId: node.codexId, pinned: next },
			{
				onSuccess: () => setMemorySnack({
					severity: 'success',
					message: next
						? `“${node.title?.trim() || 'Entry'}” is now shared with the AI.`
						: `“${node.title?.trim() || 'Entry'}” is no longer shared with the AI.`,
				}),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Could not update AI context.' }),
			},
		)
	}

	// Bulk include/exclude every entry under one Codex category (type
	// 'codex-category'; menuNode.id is the category chapter).
	const handleCategoryPin = (pinned) => {
		const node = menuNode
		closeMenu()
		if (!node) return
		setCategoryPinned(
			{ chapterId: node.id, codexId: node.codexId, pinned },
			{
				onSuccess: (data) => setMemorySnack({
					severity: 'success',
					message: pinned
						? `Shared ${data?.updated ?? 0} ${node.title?.trim() || 'category'} entr${(data?.updated === 1) ? 'y' : 'ies'} with the AI.`
						: `Stopped sharing ${node.title?.trim() || 'category'} entries with the AI.`,
				}),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Could not update AI context.' }),
			},
		)
	}

	const openManageContext = () => {
		const node = menuNode
		closeMenu()
		if (!node) return
		setManageContextTitle(node.title || 'Codex')
		setManageContextCodexId(node.id)
	}

	const doGenerateMemory = (node) => {
		setMemorySnack({ severity: 'info', message: `Producing memory document for “${node.title?.trim() || 'chapter'}”…`, persist: true })
		generateMemory(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setMemorySnack({ severity: 'success', message: `Memory document generated for “${node.title?.trim() || 'chapter'}”.`, persist: false }),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }),
			},
		)
	}

	// Inline generation is gated like a chapter review: if an earlier chapter's
	// memory document is missing or behind, warn before generating this one.
	const handleGenerateMemory = () => {
		const node = menuNode
		if (!node) return
		closeMenu()
		const flagged = flaggedPreceding(chapterMemStatus, node.id)
		if (flagged.length > 0) {
			setGateNode(node)
			setGateFlagged(flagged)
			setGateOpen(true)
			return
		}
		doGenerateMemory(node)
	}

	const openClearConfirm = () => {
		setClearNode(menuNode)
		setClearOpen(true)
	}

	const handleConfirmClear = () => {
		const node = clearNode
		setClearOpen(false)
		if (!node) return
		clearMemory(
			{ chapterId: node.id, bookId: node.bookId },
			{
				onSuccess: () => setMemorySnack({ severity: 'success', message: `Memory document cleared for “${node.title?.trim() || 'chapter'}”.` }),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Clear failed.' }),
			},
		)
	}

	// "Edit memory document…" now selects the chapter's Memory leaf in the nav
	// tree (opened for full rich-text editing in EditorPanel) instead of opening
	// a standalone dialog — the dialog was removed once that nav node existed.
	const openMemoryDocument = () => {
		const node = menuNode
		closeMenu()
		if (!node) return
		setSelection((prev) => ({
			...prev,
			bookId: node.bookId,
			partId: node.partId ?? null,
			chapterId: node.id,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: 'memory',
		}))
	}

	// ── Chapter & book summaries (nav) ─────────────────────────────────────────
	const { mutate: generateChapterSummary, isPending: generatingSummary } = useGenerateChapterSummary()
	const { mutate: clearChapterSummary } = useDeleteChapterSummary()
	const { data: chapterSummaryRows = [] } = useBookChapterSummaries(
		menuNode?.type === 'chapter' ? menuNode?.bookId : null,
	)
	const [bookSummaryOpen, setBookSummaryOpen] = useState(false)       // book-level aggregate + book summary
	const [bookSummaryNode, setBookSummaryNode] = useState(null)
	const [summarySnack, setSummarySnack] = useState(null)             // { severity, message } | null
	const [clearSummaryOpen, setClearSummaryOpen] = useState(false)
	const [clearSummaryNode, setClearSummaryNode] = useState(null)
	const menuChapterSummaryState = chapterSummaryRows.find(s => s.chapterId === menuNode?.id)?.state
	const menuChapterHasSummary = !!menuChapterSummaryState && menuChapterSummaryState !== 'MISSING'

	// Chapter summaries are independent paragraphs — no preceding-chapter gating.
	const handleGenerateChapterSummary = () => {
		const node = menuNode
		if (!node) return
		closeMenu()
		setSummarySnack({ severity: 'info', message: `Producing chapter summary for “${node.title?.trim() || 'chapter'}”…`, persist: true })
		generateChapterSummary(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setSummarySnack({ severity: 'success', message: `Chapter summary generated for “${node.title?.trim() || 'chapter'}”.`, persist: false }),
				onError: (e) => setSummarySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }),
			},
		)
	}

	// "Edit chapter summary…" now selects the chapter's Summary leaf in the nav
	// tree, the same way openMemoryDocument does.
	const openChapterSummaryDocument = () => {
		const node = menuNode
		closeMenu()
		if (!node) return
		setSelection((prev) => ({
			...prev,
			bookId: node.bookId,
			partId: node.partId ?? null,
			chapterId: node.id,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: 'chapterSummary',
		}))
	}

	const openBookSummaryDialog = () => {
		setBookSummaryNode(menuNode)
		setBookSummaryOpen(true)
	}

	// "Edit in document" from inside BookSummaryDialog: select the book's
	// Summary leaf and close the dashboard dialog.
	const openBookSummaryDocument = () => {
		const node = bookSummaryNode
		setBookSummaryOpen(false)
		if (!node) return
		setSelection((prev) => ({
			...prev,
			bookId: node.id,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: 'bookSummary',
		}))
	}

	const openClearSummaryConfirm = () => {
		setClearSummaryNode(menuNode)
		setClearSummaryOpen(true)
	}

	const handleConfirmClearSummary = () => {
		const node = clearSummaryNode
		setClearSummaryOpen(false)
		if (!node) return
		clearChapterSummary(
			{ chapterId: node.id, bookId: node.bookId },
			{
				onSuccess: () => setSummarySnack({ severity: 'success', message: `Chapter summary cleared for “${node.title?.trim() || 'chapter'}”.` }),
				onError: (e) => setSummarySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Clear failed.' }),
			},
		)
	}

	// ── Chapter editorials (nav) ───────────────────────────────────────────────
	// An editorial is an author-facing editorial reading of the chapter; it has
	// no book-wide aggregate, so existence for the Clear item is read from the
	// single-chapter doc query rather than a coverage list.
	const { mutate: generateEditorial, isPending: generatingEditorial } = useGenerateChapterEditorial()
	const { mutate: clearEditorial } = useDeleteChapterEditorial()
	const { data: menuChapterEditorial } = useChapterEditorial(
		menuNode?.type === 'chapter' ? menuNode?.id : null,
	)
	const menuChapterHasEditorial = !!menuChapterEditorial
	const [editorialSnack, setEditorialSnack] = useState(null)          // { severity, message } | null
	const [clearEditorialOpen, setClearEditorialOpen] = useState(false)
	const [clearEditorialNode, setClearEditorialNode] = useState(null)

	// Editorials are independent of other chapters — no preceding-chapter gating.
	const handleGenerateEditorial = () => {
		const node = menuNode
		if (!node) return
		closeMenu()
		setEditorialSnack({ severity: 'info', message: `Producing editorial for “${node.title?.trim() || 'chapter'}”…`, persist: true })
		generateEditorial(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setEditorialSnack({ severity: 'success', message: `Editorial generated for “${node.title?.trim() || 'chapter'}”.`, persist: false }),
				onError: (e) => setEditorialSnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }),
			},
		)
	}

	// "Edit editorial…" selects the chapter's Editorial leaf in the nav tree
	// (opened for full rich-text editing in EditorPanel), the same way
	// openMemoryDocument / openChapterSummaryDocument do.
	const openEditorialDocument = () => {
		const node = menuNode
		closeMenu()
		if (!node) return
		setSelection((prev) => ({
			...prev,
			bookId: node.bookId,
			partId: node.partId ?? null,
			chapterId: node.id,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: 'editorial',
		}))
	}

	const openClearEditorialConfirm = () => {
		setClearEditorialNode(menuNode)
		setClearEditorialOpen(true)
	}

	const handleConfirmClearEditorial = () => {
		const node = clearEditorialNode
		setClearEditorialOpen(false)
		if (!node) return
		clearEditorial(
			{ chapterId: node.id, bookId: node.bookId },
			{
				onSuccess: () => setEditorialSnack({ severity: 'success', message: `Editorial cleared for “${node.title?.trim() || 'chapter'}”.` }),
				onError: (e) => setEditorialSnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Clear failed.' }),
			},
		)
	}

	// ── Public API ──

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

				{/* ── Share Codex with the AI ──────────────────────────────────
				    Per-entry toggle (a codex-entry scene), per-category bulk, and
				    the Manage dialog (codex container). Flat MenuItems only — MUI
				    indexes direct children, so no Fragment wrappers. */}
				{menuNode?.type === 'scene' && menuNode?.codexCategory && <Divider />}
				{menuNode?.type === 'scene' && menuNode?.codexCategory && (
					<MenuItem dense onClick={handleToggleEntryPin}>
						<ListItemIcon>
							{menuNode.aiContextPinned
								? <CloseIcon fontSize="small" />
								: <AutoAwesomeIcon fontSize="small" />}
						</ListItemIcon>
						<ListItemText>
							{menuNode.aiContextPinned ? 'Exclude from AI context' : 'Include in AI context'}
						</ListItemText>
					</MenuItem>
				)}

				{menuNode?.type === 'codex-category' && <Divider />}
				{menuNode?.type === 'codex-category' && (
					<MenuItem dense onClick={() => handleCategoryPin(true)}>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Include all in AI context</ListItemText>
					</MenuItem>
				)}
				{menuNode?.type === 'codex-category' && (
					<MenuItem dense onClick={() => handleCategoryPin(false)}>
						<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Exclude all from AI context</ListItemText>
					</MenuItem>
				)}

				{menuNode?.type === 'codex' && <Divider />}
				{menuNode?.type === 'codex' && (
					<MenuItem dense onClick={openManageContext}>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Manage AI Context…</ListItemText>
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

				{/* Book summary — book nodes only. Opens the aggregated chapter
				    summaries (read-only) plus the whole-book summary panel. */}
				{isBookNode && <Divider />}
				{isBookNode && (
					<MenuItem dense onClick={() => { closeMenu(); openBookSummaryDialog() }}>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>View chapter summaries…</ListItemText>
					</MenuItem>
				)}

				{/* AI Review — chapter and scene manuscript nodes only. Flat MenuItems
				    (no Fragment children): MUI's Menu indexes direct children, and adjacent
				    fragments can cross-wire a click to the wrong item. */}
				{isManuscriptNode && (reviewNodeType === 'chapter' || reviewNodeType === 'scene') && <Divider />}
				{isManuscriptNode && (reviewNodeType === 'chapter' || reviewNodeType === 'scene') && (
					<MenuItem
						dense
						disabled={!canRunReview}
						onClick={() => { closeMenu(); setReviewError(null); setReviewDialogOpen(true) }}
					>
						<ListItemIcon><CheckCircleOutlinedIcon fontSize="small" /></ListItemIcon>
						<ListItemText>AI Review</ListItemText>
					</MenuItem>
				)}

				{/* Review history — chapter nodes only (scene reviews are part of the
				    parent chapter's history; right-click the chapter to see them). */}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						onClick={() => { closeMenu(); setHistoryDialogOpen(true) }}
					>
						<ListItemIcon><ScheduleIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Review history…</ListItemText>
					</MenuItem>
				)}

				{/* Chapter memory document — chapter manuscript nodes only */}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						disabled={!canRunReview || generatingMemory}
						onClick={handleGenerateMemory}
					>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Generate memory document</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						onClick={openMemoryDocument}
					>
						<ListItemIcon><DriveFileRenameOutlineIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Edit memory document…</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && menuChapterHasDoc && (
					<MenuItem
						dense
						onClick={() => { closeMenu(); openClearConfirm() }}
					>
						<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Clear memory document</ListItemText>
					</MenuItem>
				)}

				{/* Chapter summary — chapter manuscript nodes only */}
				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						disabled={!canRunReview || generatingSummary}
						onClick={handleGenerateChapterSummary}
					>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Generate chapter summary</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						onClick={openChapterSummaryDocument}
					>
						<ListItemIcon><DriveFileRenameOutlineIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Edit chapter summary…</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && menuChapterHasSummary && (
					<MenuItem
						dense
						onClick={() => { closeMenu(); openClearSummaryConfirm() }}
					>
						<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Clear chapter summary</ListItemText>
					</MenuItem>
				)}

				{/* Chapter editorial — chapter manuscript nodes only */}
				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						disabled={!canRunReview || generatingEditorial}
						onClick={handleGenerateEditorial}
					>
						<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Generate editorial</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						onClick={openEditorialDocument}
					>
						<ListItemIcon><DriveFileRenameOutlineIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Edit editorial…</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && menuChapterHasEditorial && (
					<MenuItem
						dense
						onClick={() => { closeMenu(); openClearEditorialConfirm() }}
					>
						<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Clear editorial</ListItemText>
					</MenuItem>
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

			{/* ── AI Review history ──────────────────────────────────────── */}
			<ChapterReviewHistoryDialog
				open={historyDialogOpen}
				onClose={() => setHistoryDialogOpen(false)}
				chapterId={reviewNodeType === 'chapter' ? menuNode?.id : null}
			/>

			<Snackbar
				open={!!memorySnack}
				autoHideDuration={memorySnack?.persist ? null : 4000}
				onClose={() => setMemorySnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{memorySnack ? (
					<Alert severity={memorySnack.severity} onClose={() => setMemorySnack(null)} sx={{ width: '100%' }}>
						{memorySnack.message}
					</Alert>
				) : undefined}
			</Snackbar>

			{/* Pre-generation memory gate (earlier chapters missing/behind) */}
			<PreReviewMemoryDialog
				open={gateOpen}
				onCancel={() => setGateOpen(false)}
				onProceed={() => { setGateOpen(false); if (gateNode) doGenerateMemory(gateNode) }}
				flagged={gateFlagged}
				bookId={gateNode?.bookId}
				credentialId={defaultCredentialId}
				title="Earlier chapters’ memory is missing or out of date"
				intro="Memory documents read best as a complete chain in book order."
				proceedLabel="Generate anyway"
				regenerateLabel="Regenerate earlier first"
			/>

			{/* Clear memory document confirmation */}
			<Dialog open={clearOpen} onClose={() => setClearOpen(false)} maxWidth="xs" fullWidth>
				<DialogTitle>Clear memory document</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Delete the memory document for {clearNode?.title?.trim() ? `“${clearNode.title.trim()}”` : 'this chapter'}? You can generate a new one at any time.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setClearOpen(false)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={handleConfirmClear}>Clear</Button>
				</DialogActions>
			</Dialog>

			{/* ── Book summary (aggregated chapter summaries + book synopsis) ──── */}
			<BookSummaryDialog
				open={bookSummaryOpen}
				onClose={() => setBookSummaryOpen(false)}
				bookId={bookSummaryNode?.id}
				title={bookSummaryNode?.title}
				onEditInDocument={openBookSummaryDocument}
			/>

			<ManageAiContextDialog
				open={!!manageContextCodexId}
				onClose={() => setManageContextCodexId(null)}
				codexId={manageContextCodexId}
				title={manageContextTitle}
			/>

			<Snackbar
				open={!!summarySnack}
				autoHideDuration={summarySnack?.persist ? null : 4000}
				onClose={() => setSummarySnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{summarySnack ? (
					<Alert severity={summarySnack.severity} onClose={() => setSummarySnack(null)} sx={{ width: '100%' }}>
						{summarySnack.message}
					</Alert>
				) : undefined}
			</Snackbar>

			{/* Clear chapter summary confirmation */}
			<Dialog open={clearSummaryOpen} onClose={() => setClearSummaryOpen(false)} maxWidth="xs" fullWidth>
				<DialogTitle>Clear chapter summary</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Delete the summary for {clearSummaryNode?.title?.trim() ? `“${clearSummaryNode.title.trim()}”` : 'this chapter'}? You can generate a new one at any time.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setClearSummaryOpen(false)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={handleConfirmClearSummary}>Clear</Button>
				</DialogActions>
			</Dialog>

			{/* Editorial generate/clear feedback */}
			<Snackbar
				open={!!editorialSnack}
				autoHideDuration={editorialSnack?.persist ? null : 4000}
				onClose={() => setEditorialSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{editorialSnack ? (
					<Alert severity={editorialSnack.severity} onClose={() => setEditorialSnack(null)} sx={{ width: '100%' }}>
						{editorialSnack.message}
					</Alert>
				) : undefined}
			</Snackbar>

			{/* Clear editorial confirmation */}
			<Dialog open={clearEditorialOpen} onClose={() => setClearEditorialOpen(false)} maxWidth="xs" fullWidth>
				<DialogTitle>Clear editorial</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Delete the editorial for {clearEditorialNode?.title?.trim() ? `“${clearEditorialNode.title.trim()}”` : 'this chapter'}? You can generate a new one at any time.
					</Typography>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setClearEditorialOpen(false)}>Cancel</Button>
					<Button color="error" variant="contained" onClick={handleConfirmClearEditorial}>Clear</Button>
				</DialogActions>
			</Dialog>
		</NavContextMenuContext.Provider>
	)
}