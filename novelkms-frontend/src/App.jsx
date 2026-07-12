import { useState, useCallback, useEffect, useRef } from 'react'
import {
	Box, AppBar, Toolbar, Typography, Button, Menu, MenuItem,
	IconButton, Tooltip, Divider, Link,
	Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions,
} from '@mui/material'
import { useLocation } from 'react-router-dom'
import DescriptionIcon from '@mui/icons-material/Description'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import FileUploadIcon from '@mui/icons-material/FileUpload'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import AutoStoriesOutlinedIcon from '@mui/icons-material/AutoStoriesOutlined'
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import ArtifactsPanel from './components/artifacts/ArtifactsPanel'
import TrashPanel from './components/trash/TrashPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'
import ImportDialog from './components/nav/dialogs/ImportDialog'
import ExportDialog from './components/nav/dialogs/ExportDialog'
import SettingsDialog from './components/settings/SettingsDialog'
import EditorSettingsDialog from './components/settings/EditorSettingsDialog'
import UserMenu from './components/nav/UserMenu'
import ContactSupportDialog from './components/nav/dialogs/ContactSupportDialog'
import ArchiveImportDialog from './components/archive/ArchiveImportDialog'
import { archiveApi } from './api/archive'
import { LogoMark } from './components/branding/Logo'
import { exportApi } from './api/export'
import { SearchProvider } from './search/SearchProvider'
import { ReviewProvider } from './review/ReviewProvider'
import { useHelp } from './help'
import { usePreferences } from './hooks/usePreferences'
import { hydrateSkipDeleteConfirm } from './utils/deleteConfirmPrefs'
import AdminSupportConsole from './components/admin/AdminSupportConsole'
import ToolsMenu from './components/tools/ToolsMenu'

/* eslint-disable no-undef */
const APP_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev'
const BUILD_NUMBER = typeof __BUILD_NUMBER__ !== 'undefined' ? __BUILD_NUMBER__ : '?'
/* eslint-enable no-undef */

const DEFAULT_NAV_WIDTH = 300
const DEFAULT_PROPS_WIDTH = 320
const MIN_NAV_WIDTH = 220
const MAX_NAV_WIDTH = 720
const MIN_PROPS_WIDTH = 260
const MAX_PROPS_WIDTH = 560
const COLLAPSED_PANEL_WIDTH = 44
const PANEL_HEADER_HEIGHT = 46

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

function readStoredNumber(key, fallback) {
	const raw = window.localStorage.getItem(key)
	const parsed = Number(raw)
	return Number.isFinite(parsed) ? parsed : fallback
}

function readStoredBoolean(key, fallback = false) {
	const raw = window.localStorage.getItem(key)
	return raw == null ? fallback : raw === 'true'
}

const EMPTY_SELECTION = {
	projectId: null,
	bookId: null,
	partId: null,
	chapterId: null,
	sceneId: null,
	codexId: null,        // selected codex (categories/entries live under it)
	codexCategory: null,  // category key of the selected codex chapter, if any
	trashSelected: false, // true when the Trash node is selected
	templateType: null,   // 'cover' | 'part' | null
	templateScope: null,  // 'global' | 'book' | null
	aiDocType: null,       // 'memory' | 'chapterSummary' | 'bookSummary' | 'editorial' | null
	aiDocProvider: null,   // selected provider variant for the active AI doc; null = preferred (default provider)
	artifactFolderId: null, // 'root' | <folderId> | null — active Artifacts Explorer folder
}

function WorkspacePanelHeader({ icon, title, subtitle, actions }) {
	return (
		<Box sx={{
			height: PANEL_HEADER_HEIGHT,
			flexShrink: 0,
			display: 'flex',
			alignItems: 'center',
			gap: 1.25,
			px: 1.75,
			borderBottom: '1px solid',
			borderColor: 'divider',
			bgcolor: 'background.paper',
		}}>
			<Box sx={{
				display: 'grid',
				placeItems: 'center',
				color: 'primary.main',
				'& svg': { fontSize: 19 },
			}}>
				{icon}
			</Box>
			<Box sx={{ minWidth: 0, flex: 1 }}>
				<Typography
					variant="subtitle2"
					sx={{ fontWeight: 700, lineHeight: 1.15, letterSpacing: 0.15 }}
				>
					{title}
				</Typography>
				{subtitle && (
					<Typography
						variant="caption"
						color="text.secondary"
						sx={{
							display: 'block',
							lineHeight: 1.15,
							mt: 0.25,
							whiteSpace: 'nowrap',
							overflow: 'hidden',
							textOverflow: 'ellipsis',
						}}
					>
						{subtitle}
					</Typography>
				)}
			</Box>
			{actions && (
				<Box sx={{ display: 'flex', alignItems: 'center', ml: 0.5 }}>
					{actions}
				</Box>
			)}
		</Box>
	)
}

// Must be a direct child of the outer flex row — flex stretches it to full height.
// Wrapping in a <span> collapses the inner Box to 0 height (block formatting context).
function ResizeHandle({ onMouseDown, disabled }) {
	if (disabled) return null

	return (
		<Box
			onMouseDown={onMouseDown}
			role="separator"
			aria-orientation="vertical"
			sx={{
				width: 8,
				flexShrink: 0,
				alignSelf: 'stretch',
				cursor: 'col-resize',
				position: 'relative',
				zIndex: 3,
				ml: -0.5,
				mr: -0.5,
				'&::after': {
					content: '""',
					position: 'absolute',
					top: 8,
					bottom: 8,
					left: '50%',
					width: 2,
					transform: 'translateX(-50%)',
					borderRadius: 2,
					bgcolor: 'divider',
					transition: 'background-color 120ms ease',
				},
				'&:hover::after, &:active::after': {
					bgcolor: 'primary.main',
				},
			}}
		/>
	)
}

const topBarButtonSx = {
	mr: 0.5,
	px: 1.25,
	borderRadius: 1,
	color: 'rgba(255,255,255,0.86)',
	'&:hover': {
		color: '#fff',
		bgcolor: 'rgba(255,255,255,0.09)',
	},
}

export default function App() {
	const [selection, setSel] = useState(EMPTY_SELECTION)
	const { openHelp } = useHelp()
	const [tplAnchor, setTplAnchor] = useState(null)
	const [importAnchor, setImportAnchor] = useState(null)
	const [exportAnchor, setExportAnchor] = useState(null)
	const [helpAnchor, setHelpAnchor] = useState(null)
	const [exportDialog, setExportDialog] = useState({ open: false, url: null, suggestedName: '', format: 'docx' })
	const [projectExportConfirmOpen, setProjectExportConfirmOpen] = useState(false)
	const [importDialogOpen, setImportDialogOpen] = useState(false)
	const [kmsImportDialogOpen, setKmsImportDialogOpen] = useState(false)
	const [settings, setSettings] = useState({ open: false, tab: 'document', subscriptionRequired: false })
	const [ctxSettings, setCtxSettings] = useState({ open: false, scope: null })
	const [contactSupportOpen, setContactSupportOpen] = useState(false)

	const [navWidth, setNavWidth] = useState(() =>
		clamp(readStoredNumber('novelkms.navWidth', DEFAULT_NAV_WIDTH), MIN_NAV_WIDTH, MAX_NAV_WIDTH)
	)
	const [propsWidth, setPropsWidth] = useState(() =>
		clamp(readStoredNumber('novelkms.propsWidth', DEFAULT_PROPS_WIDTH), MIN_PROPS_WIDTH, MAX_PROPS_WIDTH)
	)
	const [navCollapsed, setNavCollapsed] = useState(() =>
		readStoredBoolean('novelkms.navCollapsed')
	)
	const [propsCollapsed, setPropsCollapsed] = useState(() =>
		readStoredBoolean('novelkms.propsCollapsed')
	)

	const resizeRef = useRef(null)
	const location = useLocation()
	const path = location.pathname
	console.error('Path: ' + path)

	const { data: preferences } = usePreferences()
	useEffect(() => {
		if (preferences) hydrateSkipDeleteConfirm(preferences.skipDeleteConfirm)
	}, [preferences])

	useEffect(() => {
		window.localStorage.setItem('novelkms.navWidth', String(navWidth))
	}, [navWidth])

	useEffect(() => {
		window.localStorage.setItem('novelkms.propsWidth', String(propsWidth))
	}, [propsWidth])

	useEffect(() => {
		window.localStorage.setItem('novelkms.navCollapsed', String(navCollapsed))
	}, [navCollapsed])

	useEffect(() => {
		window.localStorage.setItem('novelkms.propsCollapsed', String(propsCollapsed))
	}, [propsCollapsed])

	useEffect(() => {
		const handleSubscriptionRequired = () => {
			setSettings({
				open: true,
				tab: 'billing',
				subscriptionRequired: true,
			})
		}

		window.addEventListener('novelkms:subscription-required', handleSubscriptionRequired)
		return () => {
			window.removeEventListener('novelkms:subscription-required', handleSubscriptionRequired)
		}
	}, [])

	useEffect(() => {
		const handleMouseMove = (event) => {
			const state = resizeRef.current
			if (!state) return

			if (state.panel === 'nav') {
				setNavWidth(clamp(state.startWidth + (event.clientX - state.startX), MIN_NAV_WIDTH, MAX_NAV_WIDTH))
			} else {
				setPropsWidth(clamp(state.startWidth - (event.clientX - state.startX), MIN_PROPS_WIDTH, MAX_PROPS_WIDTH))
			}
		}

		const handleMouseUp = () => {
			if (!resizeRef.current) return
			resizeRef.current = null
			document.body.style.cursor = ''
			document.body.style.userSelect = ''
		}

		window.addEventListener('mousemove', handleMouseMove)
		window.addEventListener('mouseup', handleMouseUp)
		return () => {
			window.removeEventListener('mousemove', handleMouseMove)
			window.removeEventListener('mouseup', handleMouseUp)
		}
	}, [])

	const handleResizeMouseDown = useCallback((event, panel) => {
		event.preventDefault()
		resizeRef.current = {
			panel,
			startX: event.clientX,
			startWidth: panel === 'nav' ? navWidth : propsWidth,
		}
		document.body.style.cursor = 'col-resize'
		document.body.style.userSelect = 'none'
	}, [navWidth, propsWidth])

	const setSelection = useCallback((update) => {
		setSel(prev => {
			const cleanPrev = {
				...prev,
				trashSelected: false,
				templateType: null,
				templateScope: null,
				aiDocType: null,
				aiDocProvider: null,
				artifactFolderId: null,
			}

			const base = typeof update === 'function' ? update(cleanPrev) : update

			return {
				...EMPTY_SELECTION,
				...base,
				codexId: base.codexId ?? null,
				codexCategory: base.codexCategory ?? null,
				trashSelected: base.trashSelected === true,
				templateType: base.templateType ?? null,
				templateScope: base.templateScope ?? null,
				aiDocType: base.aiDocType ?? null,
				aiDocProvider: base.aiDocProvider ?? null,
				artifactFolderId: base.artifactFolderId ?? null,
			}
		})
	}, [])

	const selectTemplate = useCallback(({ type, scope, bookId }) => {
		setSel(prev => ({
			...prev,
			bookId: scope === 'book' ? (bookId ?? prev.bookId) : null,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			trashSelected: false,
			templateType: type,
			templateScope: scope,
		}))
	}, [])

	const handleSelectBook = useCallback((bookId, partId, chapterId) => {
		setSelection({
			projectId: selection.projectId,
			bookId,
			partId: partId ?? null,
			chapterId: chapterId ?? null,
			sceneId: null,
		})
	}, [setSelection, selection.projectId])

	const handleImportSuccess = useCallback((bookId) => {
		setSelection(prev => ({
			...prev,
			bookId,
			partId: null,
			chapterId: null,
			sceneId: null,
		}))
	}, [setSelection])

	const openGlobalTemplate = (type) => {
		setTplAnchor(null)
		selectTemplate({ type, scope: 'global' })
	}

	const doExport = (url, suggestedName, format = 'docx') => {
		setExportAnchor(null)
		setExportDialog({ open: true, url, suggestedName, format })
	}

	const doDirectExport = (url) => {
		setExportAnchor(null)
		exportApi.download(url)
	}

	const openProjectExportConfirm = () => {
		setExportAnchor(null)
		setProjectExportConfirmOpen(true)
	}

	const confirmProjectExport = () => {
		setProjectExportConfirmOpen(false)
		if (!selection.projectId) return
		archiveApi.downloadProject(selection.projectId)
	}

	const openImportDialog = () => {
		setImportAnchor(null)
		setImportDialogOpen(true)
	}

	const openKmsImportDialog = () => {
		setImportAnchor(null)
		setKmsImportDialogOpen(true)
	}

	const handleKmsImportSuccess = useCallback((result) => {
		const projectId = result?.projectIds?.[0] ?? null
		if (projectId) {
			setSelection({ projectId })
		}
	}, [setSelection])

	const openSettings = (tab = 'document') => {
		setSettings({ open: true, tab })
	}

	const openContextSettings = () => {
		const scope = selection.bookId ? 'book' : (selection.projectId ? 'project' : null)
		if (!scope) return
		setCtxSettings({ open: true, scope })
	}

	const editGlobalFromContext = () => {
		setCtxSettings(s => ({ ...s, open: false }))
		openSettings()
	}

	const openDocumentation = () => {
		setHelpAnchor(null)
		openHelp()
	}

	const openFaqPage = () => {
		setHelpAnchor(null)
		window.location.href = '/faq'
	}

	const openContactSupport = () => {
		setHelpAnchor(null)
		setContactSupportOpen(true)
	}

	if (path === '/admin') {
		return <AdminSupportConsole />
	}

	return (
		<SearchProvider selection={selection}>
			<ReviewProvider>
				<Box sx={{
					display: 'flex',
					flexDirection: 'column',
					height: '100vh',
					overflow: 'hidden',
					bgcolor: 'background.default',
				}}>
					<AppBar position="static" elevation={0}>
						<Toolbar sx={{ minHeight: '54px !important', px: 2 }}>
							<Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
								<LogoMark size={34} />
								<Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5 }}>
									<Typography
										variant="h6"
										sx={{ fontWeight: 750, letterSpacing: 0.5, lineHeight: 1 }}
									>
										NovelKMS
									</Typography>
									<Typography
										variant="caption"
										sx={{
											display: { xs: 'none', md: 'block' },
											color: 'rgba(255,255,255,0.62)',
											letterSpacing: 0.25,
										}}
									>
										Novel Knowledge Management System
									</Typography>
								</Box>
							</Box>

							<Box sx={{ flexGrow: 1 }} />

							<Button
								color="inherit"
								size="small"
								startIcon={<FileDownloadIcon fontSize="small" />}
								endIcon={<ArrowDropDownIcon />}
								onClick={(e) => setExportAnchor(e.currentTarget)}
								sx={topBarButtonSx}
							>
								Export
							</Button>
							<Menu anchorEl={exportAnchor} open={!!exportAnchor} onClose={() => setExportAnchor(null)}>
								{selection.projectId && (
									<MenuItem onClick={openProjectExportConfirm}>
										Project archive (.json)
									</MenuItem>
								)}
								{selection.projectId && <Divider />}
								{selection.bookId ? [
									<MenuItem key="book" onClick={() => doExport(exportApi.bookDocxUrl(selection.bookId), 'Book')}>
										Book (.docx)
									</MenuItem>,
									<MenuItem key="book-pdf" onClick={() => doExport(exportApi.bookPdfUrl(selection.bookId), 'Book', 'pdf')}>
										Book (.pdf)
									</MenuItem>,
									<MenuItem key="book-epub" onClick={() => doDirectExport(exportApi.bookEpubUrl(selection.bookId))}>
										Book (.epub)
									</MenuItem>,
									selection.partId && (
										<MenuItem key="part" onClick={() => doExport(exportApi.partDocxUrl(selection.partId), 'Part')}>
											This Part (.docx)
										</MenuItem>
									),
									selection.partId && (
										<MenuItem key="part-pdf" onClick={() => doExport(exportApi.partPdfUrl(selection.partId), 'Part', 'pdf')}>
											This Part (.pdf)
										</MenuItem>
									),
									selection.chapterId && (
										<MenuItem key="chapter" onClick={() => doExport(exportApi.chapterDocxUrl(selection.chapterId), 'Chapter')}>
											This Chapter (.docx)
										</MenuItem>
									),
									selection.chapterId && (
										<MenuItem key="chapter-pdf" onClick={() => doExport(exportApi.chapterPdfUrl(selection.chapterId), 'Chapter', 'pdf')}>
											This Chapter (.pdf)
										</MenuItem>
									),
									selection.sceneId && (
										<MenuItem key="scene" onClick={() => doExport(exportApi.sceneDocxUrl(selection.sceneId), 'Scene')}>
											This Scene (.docx)
										</MenuItem>
									),
									selection.sceneId && (
										<MenuItem key="scene-pdf" onClick={() => doExport(exportApi.scenePdfUrl(selection.sceneId), 'Scene', 'pdf')}>
											This Scene (.pdf)
										</MenuItem>
									),
								] : (
									<MenuItem disabled>Select a book to export</MenuItem>
								)}
							</Menu>

							<Button
								color="inherit"
								size="small"
								startIcon={<FileUploadIcon fontSize="small" />}
								endIcon={<ArrowDropDownIcon />}
								onClick={(e) => setImportAnchor(e.currentTarget)}
								sx={topBarButtonSx}
							>
								Import
							</Button>
							<Menu anchorEl={importAnchor} open={!!importAnchor} onClose={() => setImportAnchor(null)}>
								<MenuItem onClick={openImportDialog}>From Word (.docx)</MenuItem>
								<MenuItem disabled>From Markdown (coming soon)</MenuItem>
								<MenuItem onClick={openKmsImportDialog}>From NovelKMS archive (.json)</MenuItem>
							</Menu>

							<Button
								color="inherit"
								size="small"
								startIcon={<DescriptionIcon fontSize="small" />}
								endIcon={<ArrowDropDownIcon />}
								onClick={(e) => setTplAnchor(e.currentTarget)}
								sx={topBarButtonSx}
							>
								Templates
							</Button>
							<Menu anchorEl={tplAnchor} open={!!tplAnchor} onClose={() => setTplAnchor(null)}>
								<MenuItem disabled sx={{ fontSize: '0.75rem', opacity: 0.7 }}>Global defaults</MenuItem>
								<MenuItem onClick={() => openGlobalTemplate('cover')}>Cover Page</MenuItem>
								<MenuItem onClick={() => openGlobalTemplate('part')}>Part Page</MenuItem>
							</Menu>

							<ToolsMenu buttonSx={topBarButtonSx} />

							<Button
								color="inherit"
								size="small"
								startIcon={<MenuBookIcon fontSize="small" />}
								endIcon={<ArrowDropDownIcon />}
								onClick={(e) => setHelpAnchor(e.currentTarget)}
								sx={topBarButtonSx}
							>
								Help
							</Button>
							<Menu anchorEl={helpAnchor} open={!!helpAnchor} onClose={() => setHelpAnchor(null)}>
								<MenuItem onClick={openDocumentation}>
									Documentation
								</MenuItem>
								<MenuItem onClick={openFaqPage}>
									FAQ
								</MenuItem>
								<MenuItem onClick={openContactSupport}>
									Contact Us
								</MenuItem>
							</Menu>

							<UserMenu onOpenSettings={openSettings} />
						</Toolbar>
					</AppBar>

					<Box sx={{
						display: 'flex',
						flex: 1,
						minHeight: 0,
						overflow: 'hidden',
						p: 1,
						gap: 1,
					}}>
						<Box sx={{
							width: navCollapsed ? COLLAPSED_PANEL_WIDTH : navWidth,
							flexShrink: 0,
							minHeight: 0,
							overflow: 'hidden',
							display: 'flex',
							flexDirection: 'column',
							bgcolor: 'background.paper',
							border: '1px solid',
							borderColor: 'divider',
							borderRadius: 1.5,
							boxShadow: 1,
						}}>
							{navCollapsed ? (
								<Box sx={{
									height: '100%',
									display: 'flex',
									flexDirection: 'column',
									alignItems: 'center',
									py: 1,
									gap: 1,
								}}>
									<Tooltip title="Show manuscript panel" placement="right">
										<IconButton size="small" onClick={() => setNavCollapsed(false)}>
											<ChevronRightIcon fontSize="small" />
										</IconButton>
									</Tooltip>
									<AutoStoriesOutlinedIcon sx={{ fontSize: 19, color: 'primary.main', mt: 0.5 }} />
								</Box>
							) : (
								<>
									<WorkspacePanelHeader
										icon={<AutoStoriesOutlinedIcon />}
										title="Manuscript"
										subtitle="Projects, books, chapters, and scenes"
										actions={
											<>
												<Tooltip title="All projects">
													<span>
														<IconButton
															size="small"
															aria-label="Show all projects"
															onClick={() => setSelection(EMPTY_SELECTION)}
															disabled={!selection.projectId}
														>
															<FolderOpenOutlinedIcon fontSize="small" />
														</IconButton>
													</span>
												</Tooltip>
												<Tooltip title="Collapse manuscript panel">
													<span>
														<IconButton size="small" onClick={() => setNavCollapsed(true)}>
															<ChevronLeftIcon fontSize="small" />
														</IconButton>
													</span>
												</Tooltip>
											</>
										}
									/>
									<Box sx={{
										flex: 1,
										minHeight: 0,
										overflow: 'hidden',
										bgcolor: 'background.paper',

										'& .MuiListItemButton-root': {
											position: 'relative',
											minHeight: 34,
											py: 0.35,
											pr: 1,
											ml: 0,
											mr: 0,
											borderRadius: 0,
											borderLeft: '3px solid transparent',
											transition: 'background-color 120ms ease, border-color 120ms ease, color 120ms ease',
										},
										'& .MuiListItemButton-root:hover': {
											bgcolor: 'action.hover',
										},
										'& .MuiListItemButton-root.Mui-selected': {
											bgcolor: 'action.selected',
											borderLeftColor: 'primary.main',
										},
										'& .MuiListItemButton-root.Mui-selected:hover': {
											bgcolor: 'action.selected',
										},
										'& .MuiListItemIcon-root': {
											color: 'text.secondary',
											transition: 'color 120ms ease, opacity 120ms ease',
										},
										'& .MuiListItemText-primary': {
											overflow: 'hidden',
											textOverflow: 'ellipsis',
											whiteSpace: 'nowrap',
										},
										'& .MuiListItemText-secondary': {
											mt: 0.15,
											fontSize: '0.66rem',
											lineHeight: 1.1,
											color: 'warning.dark',
										},

										'& .MuiListItemButton-root:has([data-testid="FolderIcon"])': {
											minHeight: 40,
											mt: 0.5,
											bgcolor: 'rgba(42, 57, 66, 0.035)',
											borderTop: '1px solid',
											borderBottom: '1px solid',
											borderTopColor: 'divider',
											borderBottomColor: 'divider',
										},
										'& .MuiListItemButton-root:has([data-testid="FolderIcon"]) .MuiListItemText-primary': {
											fontWeight: 750,
											letterSpacing: 0.1,
										},
										'& [data-testid="FolderIcon"]': {
											color: 'primary.main',
										},

										'& .MuiListItemButton-root:has([data-testid="MenuBookIcon"])': {
											minHeight: 38,
											bgcolor: 'rgba(42, 57, 66, 0.018)',
										},
										'& .MuiListItemButton-root:has([data-testid="MenuBookIcon"]) .MuiListItemText-primary': {
											fontWeight: 650,
										},
										'& [data-testid="MenuBookIcon"]': {
											color: 'primary.light',
										},

										'& .MuiListItemButton-root:has([data-testid="BookmarksIcon"])': {
											minHeight: 36,
											mt: 0.35,
										},
										'& .MuiListItemButton-root:has([data-testid="BookmarksIcon"]) .MuiListItemText-primary': {
											fontWeight: 650,
											fontStyle: 'normal',
											fontSize: '0.78rem',
											textTransform: 'uppercase',
											letterSpacing: '0.055em',
											color: 'text.secondary',
										},
										'& [data-testid="BookmarksIcon"]': {
											color: 'secondary.main',
										},

										'& .MuiListItemButton-root:has([data-testid="ArticleIcon"]) .MuiListItemText-primary': {
											fontWeight: 550,
										},
										'& [data-testid="ArticleIcon"]': {
											fontSize: 18,
										},

										'& .MuiListItemButton-root:has([data-testid="TheatersIcon"])': {
											minHeight: 31,
											color: 'text.secondary',
										},
										'& .MuiListItemButton-root:has([data-testid="TheatersIcon"]) .MuiListItemText-primary': {
											fontSize: '0.78rem',
											fontWeight: 400,
										},
										'& .MuiListItemButton-root:has([data-testid="TheatersIcon"]).Mui-selected': {
											color: 'text.primary',
										},
										'& [data-testid="TheatersIcon"]': {
											fontSize: 16,
											opacity: 0.72,
										},

										'& [data-testid="ChevronRightIcon"], & [data-testid="ExpandMoreIcon"]': {
											fontSize: 18,
											opacity: 0.62,
											transition: 'opacity 120ms ease',
										},
										'& .MuiListItemButton-root:hover [data-testid="ChevronRightIcon"], & .MuiListItemButton-root:hover [data-testid="ExpandMoreIcon"]': {
											opacity: 1,
										},
									}}>
										<NavPanel selection={selection} setSelection={setSelection} />
									</Box>
								</>
							)}
						</Box>

						<ResizeHandle
							disabled={navCollapsed}
							onMouseDown={(event) => handleResizeMouseDown(event, 'nav')}
						/>

						<Box sx={{
							flex: 1,
							minWidth: 0,
							minHeight: 0,
							overflow: 'hidden',
							display: 'flex',
							flexDirection: 'column',
							bgcolor: 'background.paper',
							border: '1px solid',
							borderColor: 'divider',
							borderRadius: 1.5,
							boxShadow: 1,
						}}>
							{selection.trashSelected ? (
								<TrashPanel />
							) : selection.artifactFolderId ? (
								<ArtifactsPanel
									projectId={selection.projectId}
									folderId={selection.artifactFolderId === 'root' ? null : selection.artifactFolderId}
									setSelection={setSelection}
								/>
							) : (
								<EditorPanel
									partId={selection.partId}
									chapterId={selection.chapterId}
									sceneId={selection.sceneId}
									projectId={selection.projectId}
									bookId={selection.bookId}
									codexId={selection.codexId}
									templateType={selection.templateType}
									templateScope={selection.templateScope}
									aiDocType={selection.aiDocType}
									aiDocProvider={selection.aiDocProvider}
									setSelection={setSelection}
									onSelectBook={handleSelectBook}
									onOpenContextSettings={openContextSettings}
									contextSettingsLabel={selection.bookId ? 'Book Settings' : selection.projectId ? 'Project Settings' : null}
								/>
							)}
						</Box>

						<ResizeHandle
							disabled={propsCollapsed}
							onMouseDown={(event) => handleResizeMouseDown(event, 'props')}
						/>

						<Box sx={{
							width: propsCollapsed ? COLLAPSED_PANEL_WIDTH : propsWidth,
							flexShrink: 0,
							minHeight: 0,
							overflow: 'hidden',
							display: 'flex',
							flexDirection: 'column',
							bgcolor: 'background.paper',
							border: '1px solid',
							borderColor: 'divider',
							borderRadius: 1.5,
							boxShadow: 1,
						}}>
							{propsCollapsed ? (
								<Box sx={{
									height: '100%',
									display: 'flex',
									flexDirection: 'column',
									alignItems: 'center',
									py: 1,
									gap: 1,
								}}>
									<Tooltip title="Show inspector" placement="left">
										<IconButton size="small" onClick={() => setPropsCollapsed(false)}>
											<ChevronLeftIcon fontSize="small" />
										</IconButton>
									</Tooltip>
									<TuneOutlinedIcon sx={{ fontSize: 19, color: 'primary.main', mt: 0.5 }} />
								</Box>
							) : (
								<>
									<WorkspacePanelHeader
										icon={<TuneOutlinedIcon />}
										title="Inspector"
										subtitle="Details and document settings"
										actions={
											<Tooltip title="Collapse inspector">
												<IconButton size="small" onClick={() => setPropsCollapsed(true)}>
													<ChevronRightIcon fontSize="small" />
												</IconButton>
											</Tooltip>
										}
									/>
									<Box sx={{
										flex: 1,
										minHeight: 0,
										overflow: 'hidden',
										bgcolor: 'background.default',
										'& > .MuiBox-root': {
											bgcolor: 'transparent',
										},
										'& > .MuiBox-root > .MuiStack-root': {
											m: 1,
											p: 1.75,
											border: '1px solid',
											borderColor: 'divider',
											borderRadius: 1.25,
											bgcolor: 'background.paper',
											boxShadow: 1,
										},
										'& .MuiTypography-overline': {
											display: 'block',
											fontSize: '0.68rem',
											fontWeight: 800,
											letterSpacing: '0.11em',
											color: 'primary.main',
										},
									}}>
										<PropertiesPanel
											selection={selection}
											setSelection={setSelection}
											selectTemplate={selectTemplate}
										/>
									</Box>
								</>
							)}
						</Box>
					</Box>

					<Box sx={{
						height: 22,
						flexShrink: 0,
						display: 'flex',
						alignItems: 'center',
						justifyContent: 'space-between',
						px: 2,
					}}>
						<Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
							<Typography
								variant="caption"
								sx={{
									fontSize: '0.65rem',
									color: 'text.disabled',
									letterSpacing: 0.3,
									userSelect: 'none',
									whiteSpace: 'nowrap',
								}}
							>
								© {new Date().getFullYear()} NovelKMS, LLC. All rights reserved.
							</Typography>
							<Link
								href="/terms"
								underline="hover"
								variant="caption"
								sx={{
									fontSize: '0.65rem',
									color: 'text.disabled',
									letterSpacing: 0.3,
									whiteSpace: 'nowrap',
								}}
							>
								Terms of Service
							</Link>
							<Link
								href="/privacy"
								underline="hover"
								variant="caption"
								sx={{
									fontSize: '0.65rem',
									color: 'text.disabled',
									letterSpacing: 0.3,
									whiteSpace: 'nowrap',
								}}
							>
								Privacy Policy
							</Link>
						</Box>
						<Typography
							variant="caption"
							sx={{
								fontSize: '0.65rem',
								color: 'text.disabled',
								letterSpacing: 0.3,
								userSelect: 'none',
							}}
						>
							{`Version ${APP_VERSION} Build ${BUILD_NUMBER}`}
						</Typography>
					</Box>

					<ImportDialog
						open={importDialogOpen}
						onClose={() => setImportDialogOpen(false)}
						projectId={selection.projectId}
						onSuccess={handleImportSuccess}
					/>

					<ExportDialog
						open={exportDialog.open}
						onClose={() => setExportDialog(d => ({ ...d, open: false }))}
						url={exportDialog.url}
						suggestedName={exportDialog.suggestedName}
						extension={exportDialog.format === 'pdf' ? 'pdf' : 'docx'}
						dialogTitle={exportDialog.format === 'pdf' ? 'Export as PDF (.pdf)' : 'Export as Word (.docx)'}
						fileDescription={exportDialog.format === 'pdf' ? 'PDF Document' : 'Word Document'}
						accept={exportDialog.format === 'pdf'
							? { 'application/pdf': ['.pdf'] }
							: { 'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'] }}
					/>

					<Dialog
						open={projectExportConfirmOpen}
						onClose={() => setProjectExportConfirmOpen(false)}
						maxWidth="sm"
						fullWidth
					>
						<DialogTitle>Export project data?</DialogTitle>
						<DialogContent dividers>
							<DialogContentText component="div">
								<Typography paragraph variant="body2">
									This will download a JSON export containing the selected project&apos;s
									NovelKMS data, including manuscript structure, books, parts, chapters,
									scenes, Codex entries, editor/page settings, templates, AI review
									artifacts, memory documents, summaries, and related project metadata.
								</Typography>
								<Typography paragraph variant="body2">
									&nbsp;
								</Typography>
								<Typography paragraph variant="body2">
									This export is intended for portability and re-import into NovelKMS.
									It will not include your login account, OAuth provider secrets, AI API
									keys, or backup archives.
								</Typography>
							</DialogContentText>
						</DialogContent>
						<DialogActions>
							<Button onClick={() => setProjectExportConfirmOpen(false)}>
								Cancel
							</Button>
							<Button variant="contained" onClick={confirmProjectExport}>
								Export JSON
							</Button>
						</DialogActions>
					</Dialog>

					<ArchiveImportDialog
						open={kmsImportDialogOpen}
						onClose={() => setKmsImportDialogOpen(false)}
						onImported={handleKmsImportSuccess}
					/>

					<SettingsDialog
						open={settings.open}
						initialTab={settings.tab}
						projectId={selection.projectId}
						subscriptionRequired={settings.subscriptionRequired}
						onClose={() => setSettings(s => ({ ...s, open: false, subscriptionRequired: false }))}
					/>

					<ContactSupportDialog
						open={contactSupportOpen}
						onClose={() => setContactSupportOpen(false)}
					/>

					<EditorSettingsDialog
						open={ctxSettings.open}
						scope={ctxSettings.scope}
						projectId={selection.projectId}
						bookId={selection.bookId}
						scopeLabel={ctxSettings.scope === 'book'
							? 'Settings for the selected book'
							: 'Settings for the selected project'}
						onEditGlobal={editGlobalFromContext}
						onClose={() => setCtxSettings(s => ({ ...s, open: false }))}
					/>
				</Box>
			</ReviewProvider>
		</SearchProvider>
	)
}