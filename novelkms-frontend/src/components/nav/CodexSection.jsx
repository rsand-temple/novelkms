import { Box, CircularProgress } from '@mui/material'
import { useProjectCodex, useBookCodex } from '../../hooks/useCodex'
import CodexItem from './CodexItem'

/**
 * Renders the Codex node for a project or book scope, if one exists. Shown
 * last under its owner (after books for a project, after parts/chapters for a
 * book). Creating a codex is handled via the Add… toolbar button and the
 * project/book context menu — no ghost row here.
 */
export default function CodexSection({ scope, ownerId, open, selection, setSelection }) {
    const isProject = scope === 'project'

    const projectCodex = useProjectCodex(isProject && open ? ownerId : null)
    const bookCodex    = useBookCodex(!isProject && open ? ownerId : null)
    const codexQuery   = isProject ? projectCodex : bookCodex
    const codex        = codexQuery.data

    if (!open || !codex) return null

    if (codexQuery.isLoading) {
        return (
            <Box sx={{ pl: isProject ? 4 : 7, py: 0.5, display: 'flex', alignItems: 'center' }}>
                <CircularProgress size={14} />
            </Box>
        )
    }

    return (
        <CodexItem
            codex={codex}
            scope={scope}
            selection={selection}
            setSelection={setSelection}
        />
    )
}
