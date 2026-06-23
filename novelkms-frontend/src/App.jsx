import { useState, useCallback, useEffect, useRef } from 'react'
import {
	Box, AppBar, Toolbar, Typography, Button, Menu, MenuItem,
	IconButton, Tooltip,
} from '@mui/material'
import DescriptionIcon from '@mui/icons-material/Description'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import FileUploadIcon from '@mui/icons-material/FileUpload'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import AutoStoriesOutlinedIcon from '@mui/icons-material/AutoStoriesOutlined'
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import TrashPanel from './components/trash/TrashPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'
import ImportDialog from './components/nav/dialogs/ImportDialog'
import ExportDialog from './components/nav/dialogs/ExportDialog'
import AiSettingsDialog from './components/ai/AiSettingsDialog'
import { LogoMark } from './components/branding/Logo'
import { exportApi } from './api/export'
import { SearchProvider } from './search/SearchProvider'

/* eslint-disable no-undef */
const APP_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev'
const BUILD_NUMBER = typeof __BUILD_NUMBER__ !== 'undefined' ? __BUILD_NUMBER__ : '?'
/* eslint-enable no-undef */

const DEFAULT_NAV_WIDTH = 300
const DEFAULT_PROPS_WIDTH = 320
const MIN_NAV_WIDTH = 220
const MAX_NAV_WIDTH = 520
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

function ResizeHandle({ onMouseDown, side, disabled }) {
	if (disabled) return null

	return (
		<Box
			onMouseDown={onMouseDown}
			role="separator"
			aria-orientation="vertical"
			sx={{
				width: 8,
				flexShrink: 0,
				cursor: 'col-resize',
				position: 'relative',
				zIndex: 3,
				'&::after': {
					content: '""',
					position: 'absolute',
					top: 8,
					bottom: 8,
					left: '50%',
					width: 2,
					transform: 'translateX(-50%)',
					borderRadius: 2,
					bgcolor: 'transparent',
					transition: 'background-color 120ms ease',
				},
				'&:hover::after': {
					bgcolor: 'primary.main',
				},
				...(side === 'left' ? { ml: -0.5, mr: -0.5 } : { ml: -0.5, mr: -0.5 }),
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
	const [tplAnchor, setTplAnchor] = useState(null)
	const [importAnchor, setImportAnchor] = useState(null)
	const [exportAnchor, setExportAnchor] = useState(null)
	const [exportDialog, setExportDialog] = useState({ open: false, url: null, suggestedName: '' })
	const [importDialogOpen, setImportDialogOpen] = useState(false)
	const [aiAnchor, setAiAnchor] = useState(null)
	const [aiSettingsOpen, setAiSettingsOpen] = useState(false)

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
			const base = typeof update === 'function' ? update(prev) : update
			return {
				...base,
				codexId:       base.codexId ?? null,
				codexCategory: base.codexCategory ?? null,
				trashSelected: base.trashSelected ?? false,
				templateType: null,
				templateScope: null,
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

	const doExport = (url, suggestedName) => {
		setExportAnchor(null)
		setExportDialog({ open: true, url, suggestedName })
	}

	const openImportDialog = () => {
		setImportAnchor(null)
		setImportDialogOpen(true)
	}

	const openAiSettings = () => {
		setAiAnchor(null)
		setAiSettingsOpen(true)
	}

	return (
		<SearchProvider selection={selection}>
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
							{selection.bookId ? [
								<MenuItem key="book" onClick={() => doExport(exportApi.bookDocxUrl(selection.bookId), 'Book')}>
									Book (.docx)
								</MenuItem>,
								selection.partId && (
									<MenuItem key="part" onClick={() => doExport(exportApi.partDocxUrl(selection.partId), 'Part')}>
										This Part (.docx)
									</MenuItem>
								),
								selection.chapterId && (
									<MenuItem key="chapter" onClick={() => doExport(exportApi.chapterDocxUrl(selection.chapterId), 'Chapter')}>
										This Chapter (.docx)
									</MenuItem>
								),
								selection.sceneId && (
									<MenuItem key="scene" onClick={() => doExport(exportApi.sceneDocxUrl(selection.sceneId), 'Scene')}>
										This Scene (.docx)
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

						<Button
							color="inherit"
							size="small"
							endIcon={<ArrowDropDownIcon />}
							onClick={(e) => setAiAnchor(e.currentTarget)}
							sx={{ ...topBarButtonSx, mr: 0 }}
						>
							AI
						</Button>
						<Menu anchorEl={aiAnchor} open={!!aiAnchor} onClose={() => setAiAnchor(null)}>
							<MenuItem onClick={openAiSettings}>AI Settings…</MenuItem>
						</Menu>
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
												<IconButton
													size="small"
													aria-label="Show all projects"
													onClick={() => setSelection(EMPTY_SELECTION)}
													disabled={!selection.projectId}
												>
													<FolderOpenOutlinedIcon fontSize="small" />
												</IconButton>
											</Tooltip>
											<Tooltip title="Collapse manuscript panel">
												<IconButton size="small" onClick={() => setNavCollapsed(true)}>
													<ChevronLeftIcon fontSize="small" />
												</IconButton>
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

									// Project rows — strongest anchors
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

									// Book rows — visible document anchors
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

									// Parts — sectional dividers
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

									// Chapters — primary working outline entries
									'& .MuiListItemButton-root:has([data-testid="ArticleIcon"]) .MuiListItemText-primary': {
										fontWeight: 550,
									},
									'& [data-testid="ArticleIcon"]': {
										fontSize: 18,
									},

									// Scenes — quieter leaves
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

									// Expand/collapse affordances
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
						side="left"
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
							onSelectBook={handleSelectBook}
						/>
					)}
					</Box>

					<ResizeHandle
						side="right"
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
					<Typography
						variant="caption"
						sx={{
							fontSize: '0.65rem',
							color: 'text.disabled',
							letterSpacing: 0.3,
							userSelect: 'none',
						}}
					>
						© {new Date().getFullYear()} Richard A. Sand. All rights reserved.
					</Typography>
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
				/>

				<AiSettingsDialog
					open={aiSettingsOpen}
					onClose={() => setAiSettingsOpen(false)}
				/>
			</Box>
		</SearchProvider>
	)
}