import { Box, AppBar, Toolbar, Typography } from '@mui/material'
import NavPanel from './components/layout/NavPanel'
import EditorPanel from './components/layout/EditorPanel'
import PropertiesPanel from './components/layout/PropertiesPanel'

const NAV_WIDTH = 280
const PROPS_WIDTH = 280

export default function App() {
	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

			{/* Top bar */}
			<AppBar position="static" elevation={1}>
				<Toolbar>
					<Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1 }}>
						NovelKMS
					</Typography>
				</Toolbar>
			</AppBar>

			{/* Three-pane body */}
			<Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

				{/* Left — navigation tree */}
				<Box sx={{
					width: NAV_WIDTH,
					flexShrink: 0,
					borderRight: '1px solid',
					borderColor: 'divider',
					overflowY: 'auto',
				}}>
					<NavPanel />
				</Box>

				{/* Center — editor */}
				<Box sx={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
					<EditorPanel />
				</Box>

				{/* Right — properties */}
				<Box sx={{
					width: PROPS_WIDTH,
					flexShrink: 0,
					borderLeft: '1px solid',
					borderColor: 'divider',
					overflowY: 'auto',
				}}>
					<PropertiesPanel />
				</Box>
			</Box>
		</Box>
	)
}