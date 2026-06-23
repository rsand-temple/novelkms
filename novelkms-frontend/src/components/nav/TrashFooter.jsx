import { Badge, Box, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { useTrash } from '../../hooks/useTrash'

export default function TrashFooter({ selection, setSelection }) {
	const { data: trashItems = [] } = useTrash()

	return (
		<Box
			sx={{
				flexShrink: 0,
				bgcolor: 'background.paper',
				px: 0.5,
				py: 0.5,
			}}
		>
			<ListItemButton
				selected={!!selection.trashSelected}
				onClick={() => setSelection({ trashSelected: true })}
				sx={{
					py: 0.5,
					minHeight: 36,
					borderRadius: 1,
				}}
			>
				<ListItemIcon sx={{ minWidth: 36 }}>
					<Badge
						badgeContent={trashItems.length}
						color="default"
						max={99}
						invisible={trashItems.length === 0}
						sx={{
							'& .MuiBadge-badge': {
								fontSize: '0.6rem',
								height: 16,
								minWidth: 16,
							},
						}}
					>
						<DeleteIcon sx={{ fontSize: 18, opacity: 0.6 }} />
					</Badge>
				</ListItemIcon>
				<ListItemText
					primary="Trash"
					primaryTypographyProps={{
						fontSize: '0.8rem',
						fontWeight: 500,
						color: 'text.secondary',
					}}
				/>
			</ListItemButton>
		</Box>
	)
}
