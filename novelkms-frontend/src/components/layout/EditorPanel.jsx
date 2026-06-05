import { Box, Typography } from '@mui/material'

export default function EditorPanel() {
	return (
		<Box sx={{ p: 4, flex: 1 }}>
			<Typography variant="h5" color="text.secondary">
				Select a scene to begin editing
			</Typography>
		</Box>
	)
}