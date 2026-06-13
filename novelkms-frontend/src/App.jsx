import { useState, useCallback } from 'react'
import {
	Box, AppBar, Toolbar, Typography, Button, Menu, MenuItem,
} from '@mui/material'
import DescriptionIcon from '@mui/icons-material/Description'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'

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
	// Retains the current projectId; clears everything else.
	const handleSelectBook = useCallback((bookId) => {
		setSelection({
			projectId: selection.projectId,
			bookId,
			partId:    null,
			chapterId: null,
			sceneId:   null,
		})
	}, [setSelection, selection.projectId])

	const openGlobalTemplate = (type) => {
		setTplAnchor(null)
		selectTemplate({ type, scope: 'global' })
	}

	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

			<AppBar position="static" elevation={1}>
				<Toolbar>
					<Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1 }}>
						NovelKMS
					</Typography>

					<Box sx={{ flexGrow: 1 }} />

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
		</Box>
	)
}
