import { Box, Badge, CircularProgress, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { useProjects } from '../../hooks/useProjects'
import { useTrash } from '../../hooks/useTrash'
import ProjectItem from './ProjectItem'

export default function NavTree({ selection, setSelection }) {
	const { data: projects, isLoading, isError } = useProjects()
	const { data: trashItems = [] } = useTrash()

	if (isLoading) {
		return (
			<Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
				<CircularProgress size={24} />
			</Box>
		)
	}

	if (isError) {
		return (
			<Box sx={{ p: 2 }}>
				<Typography variant="body2" color="error">
					Failed to load projects
				</Typography>
			</Box>
		)
	}

	if (!projects?.length && trashItems.length === 0) {
		return (
			<Box sx={{ p: 2 }}>
				<Typography variant="body2" color="text.disabled">
					No projects yet — use Add Project to get started
				</Typography>
			</Box>
		)
	}

	return (
		<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
			<Box sx={{ flex: 1, minHeight: 0 }}>
				{projects?.map((project) => (
					<ProjectItem
						key={project.id}
						project={project}
						selection={selection}
						setSelection={setSelection}
					/>
				))}
			</Box>

			{/* Trash node — pinned at bottom */}
			<Box sx={{ flexShrink: 0, borderTop: '1px solid', borderColor: 'divider', mt: 0.5 }}>
				<ListItemButton
					selected={!!selection.trashSelected}
					onClick={() => setSelection({ trashSelected: true })}
					sx={{ py: 0.5, minHeight: 36 }}
				>
					<ListItemIcon sx={{ minWidth: 30 }}>
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
		</Box>
	)
}
