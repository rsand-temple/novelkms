import { Box, Typography } from '@mui/material'

export default function NavPanel() {
	return (
		<Box sx={{ p: 2 }}>
			<Typography variant="overline" color="text.secondary">Projects</Typography>
			<Typography variant="body2" color="text.disabled" sx={{ mt: 1 }}>
				Nav tree coming soon
			</Typography>
		</Box>
	)
}