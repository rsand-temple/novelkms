import { useState } from 'react'
import { Box, AppBar, Toolbar, Typography } from '@mui/material'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'

const NAV_WIDTH = 280
const PROPS_WIDTH = 280

export default function App() {
	const [selection, setSelection] = useState({
		projectId: null,
		bookId: null,
		partId: null,
		chapterId: null,
		sceneId: null,
	})

	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

			<AppBar position="static" elevation={1}>
				<Toolbar>
					<Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1 }}>
						NovelKMS
					</Typography>
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
						chapterId={selection.chapterId} 
						sceneId={selection.sceneId} 
						projectId={selection.projectId} 
						bookId={selection.bookId}
					/>
				</Box>

				<Box sx={{
					width: PROPS_WIDTH,
					flexShrink: 0,
					borderLeft: '1px solid',
					borderColor: 'divider',
					overflowY: 'auto',
				}}>
					<PropertiesPanel selection={selection} setSelection={setSelection} />
				</Box>

			</Box>
		</Box>
	)
}