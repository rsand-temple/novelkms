import { createTheme } from '@mui/material/styles'

const theme = createTheme({
	palette: {
		mode: 'light',
	},
	typography: {
		fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
		fontSize: 14,
	},
	components: {
		MuiDrawer: {
			styleOverrides: {
				paper: {
					boxSizing: 'border-box',
				},
			},
		},
	},
})

export default theme