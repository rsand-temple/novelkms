import { useState } from 'react'
import { Box, Button, Tooltip } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import AddProjectDialog from './dialogs/AddProjectDialog'
import AddBookDialog from './dialogs/AddBookDialog'
import AddChapterDialog from './dialogs/AddChapterDialog'
import AddSceneDialog from './dialogs/AddSceneDialog'

const getAddLabel = (selection) => {
	if (selection.chapterId) return 'Add Scene'
	if (selection.bookId) return 'Add Chapter'
	if (selection.projectId) return 'Add Book'
	return 'Add Project'
}

export default function NavToolbar({ selection }) {
	const [projectDialogOpen, setProjectDialogOpen] = useState(false)
	const [bookDialogOpen, setBookDialogOpen] = useState(false)
	const [chapterDialogOpen, setChapterDialogOpen] = useState(false)
	const [sceneDialogOpen, setSceneDialogOpen] = useState(false)

	const label = getAddLabel(selection)

	const handleAdd = () => {
		if (selection.chapterId) setSceneDialogOpen(true)
		else if (selection.bookId) setChapterDialogOpen(true)
		else if (selection.projectId) setBookDialogOpen(true)
		else setProjectDialogOpen(true)
	}

	return (
		<Box sx={{ p: 1, display: 'flex', justifyContent: 'flex-end' }}>
			<Tooltip title={label}>
				<Button
					size="small"
					variant="outlined"
					startIcon={<AddIcon />}
					onClick={handleAdd}
				>
					{label}
				</Button>
			</Tooltip>

			<AddProjectDialog
				open={projectDialogOpen}
				onClose={() => setProjectDialogOpen(false)}
			/>
			<AddBookDialog
				open={bookDialogOpen}
				onClose={() => setBookDialogOpen(false)}
				projectId={selection.projectId}
			/>
			<AddChapterDialog
				open={chapterDialogOpen}
				onClose={() => setChapterDialogOpen(false)}
				bookId={selection.bookId}
			/>
			<AddSceneDialog
				open={sceneDialogOpen}
				onClose={() => setSceneDialogOpen(false)}
				chapterId={selection.chapterId}
			/>
		</Box>
	)
}