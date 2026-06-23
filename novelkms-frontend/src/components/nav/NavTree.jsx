import { Box, CircularProgress, Typography } from '@mui/material'
import { useProjects } from '../../hooks/useProjects'
import ProjectItem from './ProjectItem'

export default function NavTree({ selection, setSelection }) {
	const { data: projects, isLoading, isError } = useProjects()

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

	if (!projects?.length) {
		return (
			<Box sx={{ p: 2 }}>
				<Typography variant="body2" color="text.disabled">
					No projects yet — use Add Project to get started
				</Typography>
			</Box>
		)
	}

	return (
		<Box sx={{ pb: 0.5 }}>
			{projects.map((project) => (
				<ProjectItem
					key={project.id}
					project={project}
					selection={selection}
					setSelection={setSelection}
				/>
			))}
		</Box>
	)
}
