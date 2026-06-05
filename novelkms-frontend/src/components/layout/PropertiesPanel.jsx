import { Box, Typography } from '@mui/material'

export default function PropertiesPanel() {
	return (
		<Box sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Properties</Typography>
			<Typography variant="body2" color="text.disabled" sx={{ mt: 1 }}>
				Select a scene or chapter
			</Typography>
		</Box>
	)
}