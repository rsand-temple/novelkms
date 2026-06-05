import { Box, Divider } from '@mui/material'
import NavToolbar from '../nav/NavToolbar'
import NavTree from '../nav/NavTree'

export default function NavPanel({ selection, setSelection }) {
	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
			<NavToolbar selection={selection} setSelection={setSelection} />
			<Divider />
			<Box sx={{ flex: 1, overflowY: 'auto' }}>
				<NavTree selection={selection} setSelection={setSelection} />
			</Box>
		</Box>
	)
}