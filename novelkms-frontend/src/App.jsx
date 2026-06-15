import { useState, useCallback } from 'react'
import {
	Box, AppBar, Toolbar, Typography, Button, Menu, MenuItem,
} from '@mui/material'
import DescriptionIcon from '@mui/icons-material/Description'
import FileDownloadIcon from '@mui/icons-material/FileDownload'
import FileUploadIcon from '@mui/icons-material/FileUpload'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'
import ImportDialog from './components/nav/dialogs/ImportDialog'
import ExportDialog from './components/nav/dialogs/ExportDialog'
import { exportApi } from './api/export'
import { SearchProvider } from './search/SearchProvider'

const NAV_WIDTH = 280
const PROPS_WIDTH = 280

const EMPTY_SELECTION = {
	projectId: null,
	bookId: null,
	partId: null,
	chapterId: null,
	sceneId: null,
	templateType: null,   // 'cover' | 'part' | null
	templateScope: null,  // 'global' | 'book' | null
}

export default function App() {
	const [selection, setSel] = useState(EMPTY_SELECTION)
	const [tplAnchor, setTplAnchor] = useState(null)
	const [importAnchor, setImportAnchor] = useState(null)
	const [exportAnchor, setExportAnchor] = useState(null)
	const [exportDialog, setExportDialog] = useState({ open: false, url: null, suggestedName: '' })
	const [importDialogOpen, setImportDialogOpen] = useState(false)

	// Any manuscript selection (nav tree, toolbar, properties) clears template
	// mode automatically, so we never thread template state through the tree.
	const setSelection = useCallback((update) => {
		setSel(prev => {
			const base = typeof update === 'function' ? update(prev) : update
			return { ...base, templateType: null, templateScope: null }
		})
	}, [])

	// The only path that enters template mode.
	const selectTemplate = useCallback(({ type, scope, bookId }) => {
		setSel(prev => ({
			...prev,
			bookId: scope === 'book' ? (bookId ?? prev.bookId) : null,
			partId: null,
			chapterId: null,
			sceneId: null,
			templateType: type,
			templateScope: scope,
		}))
	}, [])

	// Called when the user clicks a book thumbnail in the project shelf.
	const handleSelectBook = useCallback((bookId, partId, chapterId) => {
		setSelection({
			projectId: selection.projectId,
			bookId,
			partId: partId ?? null,
			chapterId: chapterId ?? null,
			sceneId: null,
		})
	}, [setSelection, selection.projectId])

	// Called after a successful import — navigate directly to the new book.
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

	return (
		<SearchProvider selection={selection}>
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

			<AppBar position="static" elevation={1}>
				<Toolbar>
					<Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1 }}>
						NovelKMS
					</Typography>

					<Box sx={{ flexGrow: 1 }} />

					{/* Export menu */}
					<Button
						color="inherit"
						size="small"
						startIcon={<FileDownloadIcon fontSize="small" />}
						endIcon={<ArrowDropDownIcon />}
						onClick={(e) => setExportAnchor(e.currentTarget)}
						sx={{ mr: 1 }}
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

					{/* Import menu */}
					<Button
						color="inherit"
						size="small"
						startIcon={<FileUploadIcon fontSize="small" />}
						endIcon={<ArrowDropDownIcon />}
						onClick={(e) => setImportAnchor(e.currentTarget)}
						sx={{ mr: 1 }}
					>
						Import
					</Button>
					<Menu anchorEl={importAnchor} open={!!importAnchor} onClose={() => setImportAnchor(null)}>
						<MenuItem onClick={() => openImportDialog()}>From Word (.docx)</MenuItem>
						<MenuItem disabled>From Markdown (coming soon)</MenuItem>
					</Menu>

					{/* Templates menu */}
					<Button
						color="inherit"
						size="small"
						startIcon={<DescriptionIcon fontSize="small" />}
						endIcon={<ArrowDropDownIcon />}
						onClick={(e) => setTplAnchor(e.currentTarget)}
					>
						Templates
					</Button>
					<Menu anchorEl={tplAnchor} open={!!tplAnchor} onClose={() => setTplAnchor(null)}>
						<MenuItem disabled sx={{ fontSize: '0.75rem', opacity: 0.7 }}>Global defaults</MenuItem>
						<MenuItem onClick={() => openGlobalTemplate('cover')}>Cover Page</MenuItem>
						<MenuItem onClick={() => openGlobalTemplate('part')}>Part Page</MenuItem>
					</Menu>
				</Toolbar>
			</AppBar>

			<Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

				<Box sx={{
					width: NAV_WIDTH,
					flexShrink: 0,
					borderRight: '1px solid',
					borderColor: 'divider',
					overflowY: 'auto',
					display: 'flex',
					flexDirection: 'column',
				}}>
					<NavPanel selection={selection} setSelection={setSelection} />
				</Box>

				<Box sx={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
					<EditorPanel
						partId={selection.partId}
						chapterId={selection.chapterId}
						sceneId={selection.sceneId}
						projectId={selection.projectId}
						bookId={selection.bookId}
						templateType={selection.templateType}
						templateScope={selection.templateScope}
						onSelectBook={handleSelectBook}
					/>
				</Box>

				<Box sx={{
					width: PROPS_WIDTH,
					flexShrink: 0,
					borderLeft: '1px solid',
					borderColor: 'divider',
					overflowY: 'auto',
				}}>
					<PropertiesPanel
						selection={selection}
						setSelection={setSelection}
						selectTemplate={selectTemplate}
					/>
				</Box>

			</Box>

			{/* Import dialog — rendered at root level so it is never clipped */}
			<ImportDialog
				open={importDialogOpen}
				onClose={() => setImportDialogOpen(false)}
				projectId={selection.projectId}
				onSuccess={handleImportSuccess}
			/>

			{/* Export dialog — rendered at root level so it is never clipped */}
			<ExportDialog
				open={exportDialog.open}
				onClose={() => setExportDialog(d => ({ ...d, open: false }))}
				url={exportDialog.url}
				suggestedName={exportDialog.suggestedName}
			/>

		</Box>
		</SearchProvider>
	)
}