import { Box, ListItemButton, ListItemIcon, ListItemText, CircularProgress } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark'
import {
    useProjectCodex, useBookCodex,
    useCreateProjectCodex, useCreateBookCodex,
} from '../../hooks/useCodex'
import CodexItem from './CodexItem'

/**
 * Renders the Codex node for a project or book scope. Shown last under its
 * owner (after books for a project, after parts/chapters for a book).
 *
 * If a codex already exists at this scope it renders the CodexItem container;
 * otherwise it renders an "Add Codex" affordance that creates one on click.
 * Only one codex is permitted per scope, enforced by the backend.
 */
export default function CodexSection({ scope, ownerId, open, selection, setSelection }) {
    const isProject = scope === 'project'

    const projectCodex = useProjectCodex(isProject && open ? ownerId : null)
    const bookCodex    = useBookCodex(!isProject && open ? ownerId : null)
    const codexQuery   = isProject ? projectCodex : bookCodex
    const codex        = codexQuery.data

    const { mutate: createProjectCodex, isPending: creatingP } = useCreateProjectCodex()
    const { mutate: createBookCodex,    isPending: creatingB } = useCreateBookCodex()
    const creating = creatingP || creatingB

    if (!open) return null
    if (codexQuery.isLoading) {
        return (
            <Box sx={{ pl: isProject ? 4 : 7, py: 0.5, display: 'flex', alignItems: 'center' }}>
                <CircularProgress size={14} />
            </Box>
        )
    }

    if (codex) {
        return (
            <CodexItem
                codex={codex}
                scope={scope}
                selection={selection}
                setSelection={setSelection}
            />
        )
    }

    const handleAdd = () => {
        if (creating) return
        if (isProject) createProjectCodex({ projectId: ownerId, data: {} })
        else           createBookCodex({ bookId: ownerId, data: {} })
    }

    return (
        <ListItemButton onClick={handleAdd} disabled={creating} sx={{ pl: isProject ? 4 : 7, opacity: 0.7 }}>
            <ListItemIcon sx={{ minWidth: 28 }}><AddIcon fontSize="small" /></ListItemIcon>
            <ListItemIcon sx={{ minWidth: 28 }}><CollectionsBookmarkIcon fontSize="small" /></ListItemIcon>
            <ListItemText
                primary="Add Codex"
                slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic' } } }}
            />
        </ListItemButton>
    )
}
