import { Box, Paper, Typography } from '@mui/material'

const colors = {
	navyDark: '#0E1B31',
	navy: '#1E3050',
	navyLight: '#56647F',
	gold: '#B8915C',
	desk: '#ECE4D6',
	paper: '#FDFAF5',
	panel: '#F7F0E5',
	line: '#DED2BC',
	text: '#23293A',
	muted: '#6E6A5F',
}

const navRows = [
	{ label: 'The Alone Man', depth: 0, kind: 'project' },
	{ label: 'Book One', depth: 1, kind: 'book' },
	{ label: 'Part II — Exile', depth: 2, kind: 'part' },
	{ label: 'Chapter 12 — The Quiet Before', depth: 3, kind: 'chapter', active: true },
	{ label: 'Chapter 13', depth: 3, kind: 'chapter' },
	{ label: 'Chapter 14', depth: 3, kind: 'chapter' },
]

const fields = [
	{ label: 'Title', value: 'The Quiet Before' },
	{ label: 'Subtitle', value: 'Before the line breaks' },
	{ label: 'Word Count', value: '2,481' },
	{ label: 'Reset numbering', value: 'Off' },
]

/** Three short, original placeholder lines - not lifted from any real manuscript. */
const excerptLines = [
	'The river had not frozen yet, but it would by morning.',
	'Mara counted the lanterns on the far bank twice before she believed the count.',
	'Three. There should have been five.',
]

function NavRow({ label, depth, kind, active }) {
	return (
		<Box
			sx={{
				display: 'flex',
				alignItems: 'center',
				gap: 0.75,
				pl: 1 + depth * 1.1,
				py: 0.5,
				borderRadius: 0.5,
				bgcolor: active ? 'rgba(184,145,92,0.18)' : 'transparent',
			}}
		>
			<Box
				sx={{
					width: 5,
					height: 5,
					borderRadius: '50%',
					bgcolor: kind === 'chapter' ? colors.gold : colors.navyLight,
					flexShrink: 0,
					opacity: active ? 1 : 0.55,
				}}
			/>
			<Typography
				noWrap
				sx={{
					fontSize: depth === 0 ? '0.66rem' : '0.62rem',
					fontWeight: depth === 0 ? 700 : active ? 650 : 500,
					fontStyle: kind === 'part' ? 'italic' : 'normal',
					color: active ? colors.navy : colors.navyLight,
				}}
			>
				{label}
			</Typography>
		</Box>
	)
}

export default function EditorMockup() {
	return (
		<Paper
			elevation={4}
			aria-hidden
			sx={{
				width: '100%',
				maxWidth: 560,
				borderRadius: 2,
				overflow: 'hidden',
				border: `1px solid ${colors.line}`,
				userSelect: 'none',
			}}
		>
			{/* fake app chrome */}
			<Box sx={{ bgcolor: colors.navyDark, px: 1.5, py: 0.9, display: 'flex', alignItems: 'center', gap: 1 }}>
				<Box sx={{ display: 'flex', gap: 0.5 }}>
					{['#E6967A', '#E8C875', '#8FBF8A'].map((c) => (
						<Box key={c} sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: c, opacity: 0.85 }} />
					))}
				</Box>
				<Typography sx={{ ml: 1, fontSize: '0.66rem', fontWeight: 700, color: 'rgba(255,255,255,0.82)', letterSpacing: 0.4 }}>
					NovelKMS
				</Typography>
			</Box>

			{/* three panes */}
			<Box sx={{ display: 'flex', minHeight: 280, bgcolor: colors.desk }}>
				<Box sx={{ width: '24%', bgcolor: colors.panel, borderRight: `1px solid ${colors.line}`, py: 1 }}>
					{navRows.map((row) => (
						<NavRow key={row.label} {...row} />
					))}
				</Box>

				<Box sx={{ width: '52%', bgcolor: colors.paper, p: 2 }}>
					<Typography
						sx={{
							fontFamily: 'Georgia, "Times New Roman", serif',
							fontWeight: 700,
							fontSize: '0.95rem',
							color: colors.text,
						}}
					>
						Chapter 12
					</Typography>
					<Typography
						sx={{
							fontFamily: 'Georgia, "Times New Roman", serif',
							fontStyle: 'italic',
							fontSize: '0.7rem',
							color: colors.muted,
							mb: 1.5,
						}}
					>
						The Quiet Before
					</Typography>
					{excerptLines.map((line, i) => (
						<Typography
							key={i}
							sx={{
								fontSize: '0.66rem',
								lineHeight: 1.85,
								color: colors.text,
								textIndent: '1.2em',
							}}
						>
							{line}
						</Typography>
					))}
					<Box sx={{ mt: 2, textAlign: 'center', fontSize: '0.7rem', color: colors.muted, letterSpacing: 2 }}>* * *</Box>
				</Box>

				<Box sx={{ width: '24%', bgcolor: colors.panel, borderLeft: `1px solid ${colors.line}`, p: 1.25 }}>
					<Typography sx={{ fontSize: '0.6rem', fontWeight: 700, color: colors.navyLight, letterSpacing: 0.5, mb: 1 }}>
						PROPERTIES
					</Typography>
					{fields.map((f) => (
						<Box key={f.label} sx={{ mb: 1 }}>
							<Typography sx={{ fontSize: '0.56rem', color: colors.muted }}>{f.label}</Typography>
							<Box
								sx={{
									mt: 0.25,
									px: 0.75,
									py: 0.4,
									borderRadius: 0.5,
									border: `1px solid ${colors.line}`,
									bgcolor: '#FFFFFF',
								}}
							>
								<Typography noWrap sx={{ fontSize: '0.62rem', color: colors.text }}>
									{f.value}
								</Typography>
							</Box>
						</Box>
					))}
				</Box>
			</Box>
		</Paper>
	)
}
