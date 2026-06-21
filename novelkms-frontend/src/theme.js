import { alpha, createTheme } from '@mui/material/styles'

/**
 * NovelKMS application theme.
 *
 * The intent is a restrained writing/research workspace rather than a
 * stock Material dashboard: warm paper-like surfaces, navy application
 * chrome, compact controls, subtle borders, and minimal elevation.
 *
 * Palette is keyed off the commissioned brand assets (public/brand/) -
 * navyDark matches the icon's flattened background almost exactly so the
 * mark drops into the AppBar with no visible seam; paper matches the
 * assets' cream background the same way.
 */
const colors = {
	navy: '#1E3050',
	navyDark: '#0E1B31',
	navyLight: '#56647F',
	gold: '#B8915C',
	goldDark: '#8A6738',
	desk: '#ECE4D6',
	paper: '#FBF4EC',
	panel: '#F7F0E5',
	line: '#DED2BC',
	text: '#23293A',
	muted: '#6E6A5F',
}

const theme = createTheme({
	palette: {
		mode: 'light',
		primary: {
			main: colors.navy,
			dark: colors.navyDark,
			light: colors.navyLight,
			contrastText: '#FFFFFF',
		},
		secondary: {
			main: colors.gold,
			dark: colors.goldDark,
			contrastText: '#FFFFFF',
		},
		background: {
			default: colors.desk,
			paper: colors.paper,
		},
		text: {
			primary: colors.text,
			secondary: colors.muted,
		},
		divider: colors.line,
		action: {
			hover: alpha(colors.navy, 0.065),
			selected: alpha(colors.gold, 0.14),
			focus: alpha(colors.gold, 0.20),
		},
	},

	shape: {
		borderRadius: 5,
	},

	spacing: 8,

	typography: {
		fontFamily: '"Inter", "Segoe UI", "Roboto", "Helvetica", "Arial", sans-serif',
		fontSize: 14,
		h1: {
			fontFamily: 'Georgia, "Times New Roman", serif',
			fontWeight: 600,
			letterSpacing: '-0.015em',
		},
		h2: {
			fontFamily: 'Georgia, "Times New Roman", serif',
			fontWeight: 600,
			letterSpacing: '-0.01em',
		},
		h3: {
			fontFamily: 'Georgia, "Times New Roman", serif',
			fontWeight: 600,
		},
		h4: {
			fontFamily: 'Georgia, "Times New Roman", serif',
			fontWeight: 600,
		},
		h5: {
			fontWeight: 650,
		},
		h6: {
			fontWeight: 650,
		},
		button: {
			fontWeight: 600,
			textTransform: 'none',
			letterSpacing: '0.01em',
		},
		caption: {
			color: colors.muted,
		},
	},

	shadows: [
		'none',
		'0 1px 2px rgba(14, 27, 49, 0.12)',
		'0 2px 6px rgba(14, 27, 49, 0.13)',
		'0 4px 10px rgba(14, 27, 49, 0.14)',
		'0 6px 14px rgba(14, 27, 49, 0.15)',
		...Array(20).fill('0 8px 20px rgba(14, 27, 49, 0.16)'),
	],

	components: {
		MuiCssBaseline: {
			styleOverrides: {
				body: {
					backgroundColor: colors.desk,
				},
				'*': {
					scrollbarWidth: 'thin',
					scrollbarColor: `${alpha(colors.navy, 0.30)} transparent`,
				},
				'*::-webkit-scrollbar': {
					width: 10,
					height: 10,
				},
				'*::-webkit-scrollbar-thumb': {
					backgroundColor: alpha(colors.navy, 0.24),
					border: '3px solid transparent',
					backgroundClip: 'padding-box',
					borderRadius: 10,
				},
				'*::-webkit-scrollbar-thumb:hover': {
					backgroundColor: alpha(colors.navy, 0.38),
					backgroundClip: 'padding-box',
				},
				'.MuiToolbar-root + .MuiToolbar-root': {
					borderTopColor: alpha(colors.navy, 0.10),
				},
				'.MuiToolbar-root .MuiDivider-vertical': {
					marginTop: 5,
					marginBottom: 5,
					borderColor: alpha(colors.navy, 0.14),
				},
			},
		},

		MuiAppBar: {
			defaultProps: {
				elevation: 0,
			},
			styleOverrides: {
				root: {
					backgroundColor: colors.navyDark,
					backgroundImage: 'none',
					borderBottom: `1px solid ${alpha('#FFFFFF', 0.11)}`,
				},
			},
		},

		MuiToolbar: {
			styleOverrides: {
				root: {
					minHeight: 40,
					backgroundColor: colors.panel,
					'@media (min-width: 600px)': {
						minHeight: 40,
					},
					'.MuiAppBar-root &': {
						minHeight: 52,
						backgroundColor: 'transparent',
						'@media (min-width: 600px)': {
							minHeight: 52,
						},
					},
				},
			},
		},

		MuiDrawer: {
			styleOverrides: {
				paper: {
					boxSizing: 'border-box',
					backgroundColor: colors.panel,
					backgroundImage: 'none',
				},
			},
		},

		MuiPaper: {
			styleOverrides: {
				root: {
					backgroundImage: 'none',
				},
				outlined: {
					borderColor: colors.line,
				},
			},
		},

		MuiButton: {
			defaultProps: {
				disableElevation: true,
			},
			styleOverrides: {
				root: {
					borderRadius: 4,
					paddingLeft: 12,
					paddingRight: 12,
				},
				contained: {
					boxShadow: 'none',
					'&:hover': {
						boxShadow: 'none',
					},
				},
			},
		},

		MuiIconButton: {
			styleOverrides: {
				root: {
					borderRadius: 4,
					border: '1px solid transparent',
					transition: 'background-color 120ms ease, border-color 120ms ease, color 120ms ease',
					'&:hover': {
						backgroundColor: alpha(colors.navy, 0.075),
						borderColor: alpha(colors.navy, 0.10),
					},
					'&.Mui-disabled': {
						borderColor: 'transparent',
					},
				},
				sizeSmall: {
					padding: 5,
				},
			},
		},

		MuiTextField: {
			defaultProps: {
				size: 'small',
				variant: 'outlined',
			},
		},

		MuiSelect: {
			styleOverrides: {
				standard: {
					height: 32,
					minHeight: 32,
					paddingLeft: 8,
					paddingRight: 24,
					display: 'flex',
					alignItems: 'center',
					border: `1px solid ${colors.line}`,
					borderRadius: 4,
					backgroundColor: alpha('#FFFFFF', 0.62),
					'& .MuiSelect-select': {
						display: 'flex',
						alignItems: 'center',
						height: '100%',
						minHeight: '0 !important',
						paddingTop: '0 !important',
						paddingBottom: '0 !important',
						boxSizing: 'border-box',
					},
					'&:hover': {
						backgroundColor: '#FFFFFF',
						borderColor: colors.navyLight,
					},
					'&:focus': {
						backgroundColor: '#FFFFFF',
						borderRadius: 4,
					},
				},
				iconStandard: {
					right: 4,
					top: '50%',
					transform: 'translateY(-50%)',
				},
			},
		},

		MuiInput: {
			styleOverrides: {
				root: {
					'&.MuiInputBase-root': {
						lineHeight: 1.2,
					},
				},
			},
		},

		MuiOutlinedInput: {
			styleOverrides: {
				root: {
					backgroundColor: alpha('#FFFFFF', 0.55),
					'&:hover .MuiOutlinedInput-notchedOutline': {
						borderColor: colors.navyLight,
					},
					'&.Mui-focused .MuiOutlinedInput-notchedOutline': {
						borderWidth: 1,
					},
				},
				notchedOutline: {
					borderColor: colors.line,
				},
			},
		},

		MuiInputLabel: {
			styleOverrides: {
				root: {
					fontWeight: 500,
				},
			},
		},

		MuiMenu: {
			defaultProps: {
				elevation: 3,
			},
			styleOverrides: {
				paper: {
					marginTop: 4,
					border: `1px solid ${colors.line}`,
				},
				list: {
					paddingTop: 5,
					paddingBottom: 5,
				},
			},
		},

		MuiMenuItem: {
			styleOverrides: {
				root: {
					minHeight: 34,
					fontSize: '0.875rem',
					'&.Mui-selected': {
						backgroundColor: alpha(colors.gold, 0.14),
					},
					'&.Mui-selected:hover': {
						backgroundColor: alpha(colors.gold, 0.20),
					},
				},
			},
		},

		MuiListItemButton: {
			styleOverrides: {
				root: {
					borderRadius: 3,
					marginLeft: 4,
					marginRight: 4,
					'&.Mui-selected': {
						backgroundColor: alpha(colors.gold, 0.15),
						'&:hover': {
							backgroundColor: alpha(colors.gold, 0.21),
						},
					},
				},
			},
		},

		MuiListItemIcon: {
			styleOverrides: {
				root: {
					transition: 'color 120ms ease, opacity 120ms ease',
				},
			},
		},

		MuiDivider: {
			styleOverrides: {
				root: {
					borderColor: colors.line,
				},
			},
		},

		MuiDialog: {
			styleOverrides: {
				paper: {
					border: `1px solid ${colors.line}`,
				},
			},
		},

		MuiDialogTitle: {
			styleOverrides: {
				root: {
					fontFamily: 'Georgia, "Times New Roman", serif',
					fontWeight: 600,
					paddingBottom: 12,
				},
			},
		},

		MuiChip: {
			styleOverrides: {
				root: {
					borderRadius: 4,
					fontWeight: 500,
				},
			},
		},

		MuiTooltip: {
			defaultProps: {
				arrow: true,
				enterDelay: 500,
			},
			styleOverrides: {
				tooltip: {
					backgroundColor: colors.navyDark,
					fontSize: '0.75rem',
				},
				arrow: {
					color: colors.navyDark,
				},
			},
		},

		MuiTableHead: {
			styleOverrides: {
				root: {
					backgroundColor: colors.panel,
				},
			},
		},

		MuiTabs: {
			styleOverrides: {
				indicator: {
					backgroundColor: colors.gold,
					height: 2,
				},
			},
		},
	},
})

export default theme
