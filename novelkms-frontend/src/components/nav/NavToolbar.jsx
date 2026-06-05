import { useState } from 'react'
import { Box, Button, Tooltip } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import AddProjectDialog from './dialogs/AddProjectDialog'

const getAddLabel = (selection) => {
  if (selection.chapterId) return 'Add Scene'
  if (selection.bookId)    return 'Add Chapter'
  if (selection.projectId) return 'Add Book'
  return 'Add Project'
}

export default function NavToolbar({ selection }) {
  const [projectDialogOpen, setProjectDialogOpen] = useState(false)
  const label = getAddLabel(selection)

  const handleAdd = () => {
    if (!selection.projectId) setProjectDialogOpen(true)
    // book, chapter, scene dialogs wired in next step
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
    </Box>
  )
}