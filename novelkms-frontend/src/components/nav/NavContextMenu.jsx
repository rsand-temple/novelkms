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
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined'
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined'

import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'
import AddPartDialog from './dialogs/AddPartDialog'
import AddPartChapterDialog from './dialogs/AddPartChapterDialog'
import AddCodexEntryDialog from './dialogs/AddCodexEntryDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'
import DeleteProjectDialog from './dialogs/DeleteProjectDialog'
import { shouldSkipDeleteConfirm } from '../../utils/deleteConfirmPrefs'
import ExportDialog from './dialogs/ExportDialog'
import PreReviewMemoryDialog from '../ai/PreReviewMemoryDialog'
import { flaggedPreceding } from '../ai/memoryStatus'
import BookSummaryDialog from '../ai/BookSummaryDialog'
import ManageAiContextDialog from '../ai/ManageAiContextDialog'
import ChapterReviewHistoryDialog from '../ai/ChapterReviewHistoryDialog'
import ReviewRequestDialog from '../community/ReviewRequestDialog'
import ManageCodexTypesDialog from '../codex/ManageCodexTypesDialog'
import CodexTypeEditorDialog from '../codex/CodexTypeEditorDialog'
import NestedMenuItem from './NestedMenuItem'

import { useScenes, useReorderScenes, useDeleteScene } from '../../hooks/useScenes'
import { useChapters, useDeleteChapter } from '../../hooks/useChapters'
import { useBooks, useDeleteBook } from '../../hooks/useBooks'
import { useDeleteProject } from '../../hooks/useProjects'
import {
	useDeleteCodex, useProjectCodex, useBookCodex,
	useCreateProjectCodex, useCreateBookCodex,
	useCodexChapters, useDeleteCodexChapter, useReorderCodexChapters,
} from '../../hooks/useCodex'
import {
	useParts, usePartChapters,
	useReorderPartChapters,
	useDeletePart,
} from '../../hooks/useParts'
import { useReorderOutline } from '../../hooks/useOutline'
import { toOutlineRefs } from '../../dnd/dndUtils'
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
		case 'codex-category': return {
			level: 'codex-category',
			label: 'Delete Type',
			itemType: 'Type',
			detail: `“${title?.trim() || 'This type'}” goes to Trash together with its fields and all of its entries. Restoring it from Trash brings them all back.`,
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
		case 'project': return {
			level: 'project',
			label: 'Delete Project',
			itemType: 'Project',
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
	const [exportPdfDialogOpen, setExportPdfDialogOpen] = useState(false)
	const [bookDialogOpen, setBookDialogOpen] = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [partDialogOpen, setPartDialogOpen] = useState(false)
	const [partChapterDialogOpen, setPartChapterDialogOpen] = useState(false)
	const [insertAnchor, setInsertAnchor] = useState(null)   // { id, before } | null — insert relative to this node
	const [sceneDialogOpen, setSceneDialogOpen] = useState(false)
	const [entryDialogOpen, setEntryDialogOpen] = useState(false)
	const [reviewDialogOpen, setReviewDialogOpen] = useState(false)
	const [reviewError, setReviewError] = useState(null)
	const [historyDialogOpen, setHistoryDialogOpen] = useState(false)

	// Publish for Human Review. The target is captured when the item is clicked so
	// it survives menuNode changing; the dialog self-gates on the user having
	// claimed a review handle.
	const [publishDialogOpen, setPublishDialogOpen] = useState(false)
	const [publishTarget, setPublishTarget] = useState(null)   // { chapterId, title } | null
	const [publishSnack, setPublishSnack] = useState(null)     // { severity, message } | null

	// ── Sibling lists for Move Up / Down ──────────────────────────────────────
	// These queries hit the TanStack Query cache already populated by the nav
	// tree, so they produce no extra network requests in the happy path.
	// Each is conditionally enabled based on the right-clicked node type.
	// An OUTLINE node is a part, or a chapter that sits directly under the book.
	// Those two types share one display_order sequence, so they are each other's
	// siblings: "Move Up" on a prologue steps it above Part I.
	const isOutlineNode =
		menuNode?.type === 'part' ||
		(menuNode?.type === 'chapter' && !menuNode.partId)

	const { data: sceneSiblings } = useScenes(
		menuNode?.type === 'scene' ? menuNode.chapterId : null
	)
	const { data: partChapSiblings } = usePartChapters(
		menuNode?.type === 'chapter' && menuNode.partId ? menuNode.partId : null
	)
	const { data: outlineParts } = useParts(isOutlineNode ? menuNode.bookId : null)
	const { data: outlineChapters } = useChapters(isOutlineNode ? menuNode.bookId : null)

	// A Codex Type's siblings are the other Types of the same codex, in the
	// order CodexItem renders them. Shares CodexItem's cache key, so no extra
	// request.
	const { data: typeSiblings } = useCodexChapters(
		menuNode?.type === 'codex-category' ? menuNode.codexId : null
	)

	// Merged on displayOrder, exactly as BookItem renders it — the menu and the
	// tree must agree about what "the next one down" is.
	const outlineSiblings = isOutlineNode
		? [
			...(outlineParts ?? []).map(x => ({ id: x.id, type: 'part', displayOrder: x.displayOrder })),
			...(outlineChapters ?? []).map(x => ({ id: x.id, type: 'chapter', displayOrder: x.displayOrder })),
		].sort((a, b) => a.displayOrder - b.displayOrder)
		: null

	const siblings =
		menuNode?.type === 'scene' ? sceneSiblings
			: menuNode?.type === 'chapter' && menuNode.partId ? partChapSiblings
				: menuNode?.type === 'codex-category' ? typeSiblings
					: isOutlineNode ? outlineSiblings
						: null

	const siblingIndex = siblings?.findIndex(s => String(s.id) === String(menuNode?.id)) ?? -1
	const isFirst = siblingIndex <= 0
	const isLast = !siblings || siblingIndex < 0 || siblingIndex >= siblings.length - 1
	const canReorder = menuNode?.type === 'scene' || menuNode?.type === 'chapter'
		|| menuNode?.type === 'part' || menuNode?.type === 'codex-category'

	// ── Mutations ─────────────────────────────────────────────────────────────
	const { mutate: reorderScenes } = useReorderScenes()
	const { mutate: reorderPartChapters } = useReorderPartChapters()
	const { mutate: reorderOutline } = useReorderOutline()
	const { mutate: reorderCodexChapters } = useReorderCodexChapters()
	const { mutate: deleteScene, isPending: deletingScene } = useDeleteScene()
	const { mutate: deleteChapter, isPending: deletingChapter } = useDeleteChapter()
	const { mutate: deletePart, isPending: deletingPart } = useDeletePart()
	const { mutate: deleteBook, isPending: deletingBook } = useDeleteBook()
	const { mutate: deleteCodex, isPending: deletingCodex } = useDeleteCodex()
	const { mutate: deleteCodexType, isPending: deletingCodexType } = useDeleteCodexChapter()
	const { mutate: deleteProject, isPending: deletingProject } = useDeleteProject()
	const isDeleting = deletingScene || deletingChapter || deletingPart || deletingBook
		|| deletingCodex || deletingCodexType

	// ── Project delete confirmation ──────────────────────────────────────────
	const [deleteProjectDialogOpen, setDeleteProjectDialogOpen] = useState(false)
	const { data: menuProjectBooks } = useBooks(
		menuNode?.type === 'project' ? menuNode.id : null,
	)

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

	// ── Codex Type management (E5) ─────────────────────────────────────────────
	// Manage Types opens from the codex container; Edit Type opens directly on a
	// single type (a codex-category chapter). Targets are captured before
	// closeMenu() (which keeps menuNode alive but which we don't want to depend
	// on for dialog props).
	const [manageTypesTarget, setManageTypesTarget] = useState(null) // { codexId, title } | null
	const [editTypeTarget, setEditTypeTarget] = useState(null)       // { typeId, codexId } | null

	// Chapter memory document (nav "Generate / Clear memory document").
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
						? `"${node.title?.trim() || 'Entry'}" is now shared with the AI.`
						: `"${node.title?.trim() || 'Entry'}" is no longer shared with the AI.`,
				}),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Could not update AI context.' }),
			},
		)
	}

	// Bulk include/exclude every entry under one Codex Type (node type
	// 'codex-category'; menuNode.id is the Type chapter).
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
						? `Shared ${data?.updated ?? 0} ${node.title?.trim() || 'type'} entr${(data?.updated === 1) ? 'y' : 'ies'} with the AI.`
						: `Stopped sharing ${node.title?.trim() || 'type'} entries with the AI.`,
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
		setMemorySnack({ severity: 'info', message: `Producing memory document for "${node.title?.trim() || 'chapter'}"…`, persist: true })
		generateMemory(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setMemorySnack({ severity: 'success', message: `Memory document generated for "${node.title?.trim() || 'chapter'}".`, persist: false }),
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
				onSuccess: () => setMemorySnack({ severity: 'success', message: `Memory document cleared for "${node.title?.trim() || 'chapter'}".` }),
				onError: (e) => setMemorySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Clear failed.' }),
			},
		)
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
		setSummarySnack({ severity: 'info', message: `Producing chapter summary for "${node.title?.trim() || 'chapter'}"…`, persist: true })
		generateChapterSummary(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setSummarySnack({ severity: 'success', message: `Chapter summary generated for "${node.title?.trim() || 'chapter'}".`, persist: false }),
				onError: (e) => setSummarySnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }),
			},
		)
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
				onSuccess: () => setSummarySnack({ severity: 'success', message: `Chapter summary cleared for "${node.title?.trim() || 'chapter'}".` }),
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
		setEditorialSnack({ severity: 'info', message: `Producing editorial for "${node.title?.trim() || 'chapter'}"…`, persist: true })
		generateEditorial(
			{ chapterId: node.id, bookId: node.bookId, credentialId: defaultCredentialId },
			{
				onSuccess: () => setEditorialSnack({ severity: 'success', message: `Editorial generated for "${node.title?.trim() || 'chapter'}".`, persist: false }),
				onError: (e) => setEditorialSnack({ severity: 'error', message: e?.response?.data?.message ?? e?.message ?? 'Generation failed.', persist: false }),
			},
		)
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
				onSuccess: () => setEditorialSnack({ severity: 'success', message: `Editorial cleared for "${node.title?.trim() || 'chapter'}".` }),
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

	// `ordered` is the sibling list AFTER the swap, still as objects — the outline
	// path needs each entry's type to know which table the server should renumber.
	const dispatchReorder = (ordered) => {
		if (!menuNode) return
		const { type, chapterId, partId, bookId, codexId } = menuNode
		if (type === 'scene') {
			reorderScenes({ chapterId, ids: ordered.map(o => o.id) })
		} else if (type === 'chapter' && partId) {
			reorderPartChapters({ partId, ids: ordered.map(o => o.id) })
		} else if (type === 'codex-category') {
			reorderCodexChapters({ codexId, ids: ordered.map(o => o.id) })
		} else if (isOutlineNode) {
			reorderOutline({ bookId, items: toOutlineRefs(ordered) })
		}
	}

	const swapSiblings = (i, j) => {
		const next = [...siblings]
		;[next[i], next[j]] = [next[j], next[i]]
		return next
	}

	const handleMoveUp = () => {
		if (isFirst || !siblings) return
		dispatchReorder(swapSiblings(siblingIndex - 1, siblingIndex))
		closeMenu()
	}

	const handleMoveDown = () => {
		if (isLast || !siblings) return
		dispatchReorder(swapSiblings(siblingIndex, siblingIndex + 1))
		closeMenu()
	}

	// ── Insert before / after ─────────────────────────────────────────────────
	// The anchor is handed to the create endpoint, which opens a slot at that
	// position instead of appending. For an outline node the anchor may be a part
	// OR a chapter — "Insert Chapter Before" on Part I is how a prologue is made.
	const openInsertDialog = (setOpen, before) => {
		setInsertAnchor({ id: menuNode.id, before })
		closeMenu()
		setOpen(true)
	}

	const clearInsertAnchor = () => setInsertAnchor(null)

	// ── Delete ────────────────────────────────────────────────────────────────

	const deleteCtx = menuNode ? getDeleteContext(menuNode.type, menuNode.title, menuNode.codexCategory) : null

	const handleConfirmDelete = () => {
		if (!menuNode || !deleteCtx) return
		const { id, chapterId, bookId, projectId, codexId } = menuNode

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
		} else if (deleteCtx.level === 'codex-category') {
			// The Type is a chapter row; deleting it soft-deletes, carrying its
			// fields and entry scenes into Trash with it. Clear chapter/scene
			// from the selection but keep the codex selected.
			deleteCodexType(
				{ id, codexId },
				{
					onSuccess: () => {
						setSelection(s => ({ ...s, chapterId: null, sceneId: null, codexCategory: null }))
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

	// Project deletion uses its own stern, dedicated dialog (DeleteProjectDialog)
	// rather than the generic DeleteConfirmDialog / handleConfirmDelete flow above,
	// so it is never eligible for the "don't show this again" skip shortcut.
	const handleConfirmDeleteProject = () => {
		if (!menuNode || menuNode.type !== 'project') return
		deleteProject(
			menuNode.id,
			{
				onSuccess: () => {
					setSelection(s => ({
						...s,
						projectId: null, bookId: null, partId: null, chapterId: null, sceneId: null,
						codexId: null, codexCategory: null,
					}))
					setDeleteProjectDialogOpen(false)
				},
			},
		)
	}

	// ── Add sub-item ──────────────────────────────────────────────────────────

	const handleAddScene = () => {
		closeMenu()
		setSceneDialogOpen(true)
	}

	// ── Derived menu flags ────────────────────────────────────────────────────

	const isBookNode = menuNode?.type === 'book'
	const canDelete = deleteCtx != null

	// Export URL — derived from the right-clicked node type and id.
	// null for project nodes (no export scope for the whole project).
	const exportUrl = (() => {
		if (!menuNode) return null
		switch (menuNode.type) {
			case 'book': return exportApi.bookDocxUrl(menuNode.id)
			case 'part': return exportApi.partDocxUrl(menuNode.id)
			case 'chapter': return exportApi.chapterDocxUrl(menuNode.id)
			// ExportService resolves a scene's book through its chapter and throws
			// when there isn't one. A codex entry and a Scratchpad scene both live
			// under a chapter with a NULL book_id, so neither is exportable —
			// offering it produced a 500 rather than a file.
			case 'scene': return menuNode.bookId ? exportApi.sceneDocxUrl(menuNode.id) : null
			default: return null
		}
	})()

	// Same scopes as exportUrl, PDF variant.
	const exportPdfUrl = (() => {
		if (!menuNode) return null
		switch (menuNode.type) {
			case 'book': return exportApi.bookPdfUrl(menuNode.id)
			case 'part': return exportApi.partPdfUrl(menuNode.id)
			case 'chapter': return exportApi.chapterPdfUrl(menuNode.id)
			case 'scene': return menuNode.bookId ? exportApi.scenePdfUrl(menuNode.id) : null
			default: return null
		}
	})()

	// ePub export is book-scoped only (there is no part/chapter/scene ePub
	// endpoint), so this is null for every other node type. It downloads
	// directly — same pattern as the AppBar's Export menu — rather than going
	// through ExportDialog's filename picker.
	const epubUrl = menuNode?.type === 'book' ? exportApi.bookEpubUrl(menuNode.id) : null

	// For the AddSceneDialog chapterId:
	// - right-clicked a chapter    → chapterId = menuNode.id
	// - right-clicked the Scratchpad → chapterId = menuNode.id (it is a chapter row)
	// - right-clicked a scene      → chapterId = menuNode.chapterId
	const addSceneChapterId = (menuNode?.type === 'chapter' || menuNode?.type === 'scratchpad')
		? menuNode.id
		: menuNode?.chapterId

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
				{/* Rename — not offered for a Codex Type (rename it in the Type editor)
				    nor for the Scratchpad, which is a fixture of its book and has a
				    fixed name; the server rejects a rename with not_scratchpad_operation. */}
				{menuNode?.type !== 'codex-category' && menuNode?.type !== 'scratchpad' && (
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

				{/* Insert relative to this node.
				    On an outline node (a part, or a chapter directly under the book) the
				    anchor may be either type — which is what makes "Insert Chapter Before"
				    on Part I produce a prologue. Each item is a direct child of Menu:
				    MUI's MenuList does not accept Fragments. */}
				{isOutlineNode && <Divider />}
				{isOutlineNode && (
					<MenuItem dense onClick={() => openInsertDialog(setChapterDialogOpen, true)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Chapter Before</ListItemText>
					</MenuItem>
				)}
				{isOutlineNode && (
					<MenuItem dense onClick={() => openInsertDialog(setChapterDialogOpen, false)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Chapter After</ListItemText>
					</MenuItem>
				)}
				{isOutlineNode && (
					<MenuItem dense onClick={() => openInsertDialog(setPartDialogOpen, true)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Part Before</ListItemText>
					</MenuItem>
				)}
				{isOutlineNode && (
					<MenuItem dense onClick={() => openInsertDialog(setPartDialogOpen, false)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Part After</ListItemText>
					</MenuItem>
				)}

				{/* A chapter inside a part: its siblings are that part's chapters. */}
				{menuNode?.type === 'chapter' && menuNode.partId && <Divider />}
				{menuNode?.type === 'chapter' && menuNode.partId && (
					<MenuItem dense onClick={() => openInsertDialog(setPartChapterDialogOpen, true)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Chapter Before</ListItemText>
					</MenuItem>
				)}
				{menuNode?.type === 'chapter' && menuNode.partId && (
					<MenuItem dense onClick={() => openInsertDialog(setPartChapterDialogOpen, false)}>
						<ListItemIcon><AddIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Insert Chapter After</ListItemText>
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
				{(menuNode?.type === 'chapter' || menuNode?.type === 'scene' || menuNode?.type === 'scratchpad') && (
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
				    Per-entry toggle (a codex-entry scene), per-Type bulk, and
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
					<MenuItem
						dense
						onClick={() => {
							const target = { typeId: menuNode.id, codexId: menuNode.codexId }
							closeMenu()
							setEditTypeTarget(target)
						}}
					>
						<ListItemIcon><TuneOutlinedIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Edit Type…</ListItemText>
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
				{menuNode?.type === 'codex' && (
					<MenuItem
						dense
						onClick={() => {
							const target = { codexId: menuNode.id, title: menuNode.title || 'Codex' }
							closeMenu()
							setManageTypesTarget(target)
						}}
					>
						<ListItemIcon><TuneOutlinedIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Manage Codex Types…</ListItemText>
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

				{/* Export as PDF — not available for project */}
				{exportPdfUrl && (
					<MenuItem dense onClick={() => { setExportPdfDialogOpen(true); closeMenu() }}>
						<ListItemIcon><FileDownloadIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Export as PDF (.pdf)</ListItemText>
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

				{/* ── AI actions — chapter and scene manuscript nodes only ────────
				    Divider here is the intentional boundary between structural
				    actions (rename, reorder, add, export) and AI actions. Flat
				    MenuItems only (no Fragment children): MUI's Menu indexes
				    direct children, and adjacent fragments can cross-wire a
				    click to the wrong item. */}
				{isManuscriptNode && (reviewNodeType === 'chapter' || reviewNodeType === 'scene') && <Divider />}

				{/* Scenes only get the single run action — nothing else to nest. */}
				{isManuscriptNode && reviewNodeType === 'scene' && (
					<MenuItem
						dense
						disabled={!canRunReview}
						onClick={() => { closeMenu(); setReviewError(null); setReviewDialogOpen(true) }}
					>
						<ListItemIcon><CheckCircleOutlinedIcon fontSize="small" /></ListItemIcon>
						<ListItemText>AI Review</ListItemText>
					</MenuItem>
				)}

				{/* ── AI Review submenu — chapter nodes only ───────────────────── */}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<NestedMenuItem label="AI Review" icon={<CheckCircleOutlinedIcon fontSize="small" />}>
						<MenuItem
							dense
							disabled={!canRunReview}
							onClick={() => { closeMenu(); setReviewError(null); setReviewDialogOpen(true) }}
						>
							<ListItemIcon><CheckCircleOutlinedIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Run AI Review</ListItemText>
						</MenuItem>
						<MenuItem
							dense
							onClick={() => { closeMenu(); setHistoryDialogOpen(true) }}
						>
							<ListItemIcon><ScheduleIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Review history…</ListItemText>
						</MenuItem>
					</NestedMenuItem>
				)}

				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}
				{/* Publish for Human Review — manuscript chapters only (never scenes
				    or codex). The dialog self-gates on having claimed a handle. Kept
				    as its own top-level item — it isn't one of the AI Review /
				    Memory Document / Summary / Editorial submenus. */}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<MenuItem
						dense
						onClick={() => {
							const target = { chapterId: menuNode.id, title: menuNode.title }
							closeMenu()
							setPublishTarget(target)
							setPublishDialogOpen(true)
						}}
					>
						<ListItemIcon><GroupsOutlinedIcon fontSize="small" /></ListItemIcon>
						<ListItemText>Publish for Human Review…</ListItemText>
					</MenuItem>
				)}
				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}

				{/* ── Memory Document submenu — Generate and Clear only; editing is
				    via the Memory nav leaf in the editor panel. ─────────────────── */}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<NestedMenuItem label="Memory Document" icon={<AutoAwesomeIcon fontSize="small" />}>
						<MenuItem
							dense
							disabled={!canRunReview || generatingMemory}
							onClick={handleGenerateMemory}
						>
							<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Generate memory document</ListItemText>
						</MenuItem>
						{menuChapterHasDoc && (
							<MenuItem
								dense
								onClick={() => { closeMenu(); openClearConfirm() }}
							>
								<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
								<ListItemText>Clear memory document</ListItemText>
							</MenuItem>
						)}
					</NestedMenuItem>
				)}

				{/* ── Summary submenu — Generate and Clear only; editing is via the
				    Summary nav leaf in the editor panel. ─────────────────────────── */}
				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<NestedMenuItem label="Summary" icon={<AutoAwesomeIcon fontSize="small" />}>
						<MenuItem
							dense
							disabled={!canRunReview || generatingSummary}
							onClick={handleGenerateChapterSummary}
						>
							<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Generate chapter summary</ListItemText>
						</MenuItem>
						{menuChapterHasSummary && (
							<MenuItem
								dense
								onClick={() => { closeMenu(); openClearSummaryConfirm() }}
							>
								<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
								<ListItemText>Clear chapter summary</ListItemText>
							</MenuItem>
						)}
					</NestedMenuItem>
				)}

				{/* ── Editorial submenu — Generate and Clear only; editing is via
				    the Editorial nav leaf in the editor panel. ───────────────────── */}
				{isManuscriptNode && reviewNodeType === 'chapter' && <Divider />}
				{isManuscriptNode && reviewNodeType === 'chapter' && (
					<NestedMenuItem label="Editorial" icon={<AutoAwesomeIcon fontSize="small" />}>
						<MenuItem
							dense
							disabled={!canRunReview || generatingEditorial}
							onClick={handleGenerateEditorial}
						>
							<ListItemIcon><AutoAwesomeIcon fontSize="small" /></ListItemIcon>
							<ListItemText>Generate editorial</ListItemText>
						</MenuItem>
						{menuChapterHasEditorial && (
							<MenuItem
								dense
								onClick={() => { closeMenu(); openClearEditorialConfirm() }}
							>
								<ListItemIcon><CloseIcon fontSize="small" /></ListItemIcon>
								<ListItemText>Clear editorial</ListItemText>
							</MenuItem>
						)}
					</NestedMenuItem>
				)}

				{/* Delete */}
				{canDelete && <Divider />}
				{canDelete && (
					<MenuItem
						dense
						onClick={() => {
							closeMenu()
							if (deleteCtx.level === 'project') {
								// Whole-project delete always shows its own stern dialog —
								// the "don't show this again" skip shortcut never applies here.
								setDeleteProjectDialogOpen(true)
							} else if (shouldSkipDeleteConfirm()) {
								handleConfirmDelete()
							} else {
								setDeleteDialogOpen(true)
							}
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

			{/* ── Delete Project confirmation (stern, dedicated — see design notes
			    above handleConfirmDeleteProject) ──────────────────────────────── */}
			<DeleteProjectDialog
				open={deleteProjectDialogOpen}
				onClose={() => setDeleteProjectDialogOpen(false)}
				onConfirm={handleConfirmDeleteProject}
				projectTitle={menuNode?.type === 'project' ? menuNode.title : ''}
				bookCount={menuProjectBooks?.length ?? 0}
				isPending={deletingProject}
			/>

			{/* ── Export dialog ───────────────────────────────────────────────── */}
			<ExportDialog
				open={exportDialogOpen}
				onClose={() => setExportDialogOpen(false)}
				url={exportUrl}
				suggestedName={menuNode?.title?.trim() || menuNode?.type || 'export'}
			/>
			<ExportDialog
				open={exportPdfDialogOpen}
				onClose={() => setExportPdfDialogOpen(false)}
				url={exportPdfUrl}
				suggestedName={menuNode?.title?.trim() || menuNode?.type || 'export'}
				extension="pdf"
				dialogTitle="Export as PDF (.pdf)"
				fileDescription="PDF Document"
				accept={{ 'application/pdf': ['.pdf'] }}
			/>

			{/* ── Add dialogs ────────────────────────────────────────────────── */}
			<AddBookDialog
				open={bookDialogOpen}
				onClose={() => setBookDialogOpen(false)}
				projectId={menuNode?.type === 'project' ? menuNode.id : menuNode?.projectId}
			/>
			{/* anchor is null when reached via "Add …" (append) and set when reached
			    via "Insert … Before/After". It is cleared on close so the next plain
			    Add does not inherit the last insert position. */}
			<AddPartDialog
				open={partDialogOpen}
				onClose={() => { setPartDialogOpen(false); clearInsertAnchor() }}
				bookId={menuNode?.type === 'book' ? menuNode.id : menuNode?.bookId}
				anchor={insertAnchor}
			/>
			<AddChapterDialog
				open={chapterDialogOpen}
				onClose={() => { setChapterDialogOpen(false); clearInsertAnchor() }}
				bookId={menuNode?.type === 'book' ? menuNode.id : menuNode?.bookId}
				anchor={insertAnchor}
			/>
			<AddPartChapterDialog
				open={partChapterDialogOpen}
				onClose={() => { setPartChapterDialogOpen(false); clearInsertAnchor() }}
				partId={menuNode?.type === 'part' ? menuNode.id : menuNode?.partId}
				anchor={insertAnchor}
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
				typeName={menuNode?.type === 'codex-category' ? menuNode.title : null}
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

			{/* ── Publish for Human Review ────────────────────────────────── */}
			<ReviewRequestDialog
				open={publishDialogOpen}
				mode="publish"
				chapterId={publishTarget?.chapterId}
				suggestedTitle={publishTarget?.title}
				onClose={() => setPublishDialogOpen(false)}
				onPublished={(req) => setPublishSnack({
					severity: 'success',
					message: `Published “${req.title}” for human review.`,
				})}
			/>

			<Snackbar
				open={!!publishSnack}
				autoHideDuration={4000}
				onClose={() => setPublishSnack(null)}
				anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
			>
				{publishSnack ? (
					<Alert severity={publishSnack.severity} onClose={() => setPublishSnack(null)} sx={{ width: '100%' }}>
						{publishSnack.message}
					</Alert>
				) : undefined}
			</Snackbar>

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
				title="Earlier chapters' memory is missing or out of date"
				intro="Memory documents read best as a complete chain in book order."
				proceedLabel="Generate anyway"
				regenerateLabel="Regenerate earlier first"
			/>

			{/* Clear memory document confirmation */}
			<Dialog open={clearOpen} onClose={() => setClearOpen(false)} maxWidth="xs" fullWidth>
				<DialogTitle>Clear memory document</DialogTitle>
				<DialogContent>
					<Typography variant="body2">
						Delete the memory document for {clearNode?.title?.trim() ? `"${clearNode.title.trim()}"` : 'this chapter'}? You can generate a new one at any time.
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

			{/* ── Codex Type management (E5) ──────────────────────────────── */}
			<ManageCodexTypesDialog
				open={!!manageTypesTarget}
				onClose={() => setManageTypesTarget(null)}
				codexId={manageTypesTarget?.codexId}
				codexTitle={manageTypesTarget?.title}
			/>
			<CodexTypeEditorDialog
				open={!!editTypeTarget}
				onClose={() => setEditTypeTarget(null)}
				typeId={editTypeTarget?.typeId}
				codexId={editTypeTarget?.codexId}
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
						Delete the summary for {clearSummaryNode?.title?.trim() ? `"${clearSummaryNode.title.trim()}"` : 'this chapter'}? You can generate a new one at any time.
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
						Delete the editorial for {clearEditorialNode?.title?.trim() ? `"${clearEditorialNode.title.trim()}"` : 'this chapter'}? You can generate a new one at any time.
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
