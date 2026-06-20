import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText, IconButton, Tooltip } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import { useCodexChapters, useDeleteCodex } from '../../hooks/useCodex'
import CodexCategoryItem from './CodexCategoryItem'
import AddCodexChapterDialog from './dialogs/AddCodexChapterDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'

/**
 * Codex container node — a Part-like container appearing last under its
 * project/book. Reuses the chapter/scene tables: its categories are chapters
 * (codex_id set, book_id null) and its entries are scenes. Clicking the row
 * only toggles expansion so it never disturbs the editor selection; the
 * inline action buttons add a category or delete the whole codex.
 */
export default function CodexItem({ codex, scope, selection, setSelection }) {
    const [open, setOpen]       = useState(true)   // open by default so seeded categories show
    const [addOpen, setAddOpen] = useState(false)
    const [delOpen, setDelOpen] = useState(false)

    const { data: chapters } = useCodexChapters(open ? codex.id : null)
    const { mutate: deleteCodex, isPending: deleting } = useDeleteCodex()

    const basePl = scope === 'project' ? 4 : 7

    const handleToggle = () => setOpen(o => !o)

    const handleDelete = () => {
        deleteCodex(
            { id: codex.id, projectId: codex.projectId, bookId: codex.bookId },
            { onSuccess: () => {
                setDelOpen(false)
                // Clear selection if it was pointing inside this codex.
                if (selection.codexId === codex.id) {
                    setSelection((prev) => ({ ...prev, chapterId: null, sceneId: null, codexId: null, codexCategory: null }))
                }
            } }
        )
    }

    return (
        <Box sx={{ position: 'relative' }}>
            <ListItemButton onClick={handleToggle} sx={{ pl: basePl, pr: 9 }}>
                <ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }}>
                    {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                </ListItemIcon>
                <ListItemIcon sx={{ minWidth: 28 }}>
                    <CollectionsBookmarkIcon fontSize="small" sx={{ color: 'secondary.main' }} />
                </ListItemIcon>
                <ListItemText
                    primary={codex.title || 'Codex'}
                    slotProps={{ primary: {
                        variant: 'body2',
                        sx: { fontWeight: 650, fontSize: '0.74rem', textTransform: 'uppercase', letterSpacing: '0.055em', color: 'text.secondary' },
                    } }}
                />
            </ListItemButton>

            <Box sx={{ position: 'absolute', right: 4, top: 0, height: 34, display: 'flex', alignItems: 'center' }}>
                <Tooltip title="Add category">
                    <IconButton size="small" onClick={() => setAddOpen(true)} aria-label="Add codex category">
                        <AddIcon fontSize="inherit" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Delete codex">
                    <IconButton size="small" onClick={() => setDelOpen(true)} aria-label="Delete codex">
                        <DeleteIcon fontSize="inherit" />
                    </IconButton>
                </Tooltip>
            </Box>

            <Collapse in={open} unmountOnExit>
                <Box>
                    {(chapters ?? []).map((chapter) => (
                        <CodexCategoryItem
                            key={chapter.id}
                            codex={codex}
                            chapter={chapter}
                            basePl={basePl}
                            selection={selection}
                            setSelection={setSelection}
                        />
                    ))}
                </Box>
            </Collapse>

            <AddCodexChapterDialog open={addOpen} onClose={() => setAddOpen(false)} codexId={codex.id} />
            <DeleteConfirmDialog
                open={delOpen}
                onClose={() => setDelOpen(false)}
                onConfirm={handleDelete}
                title="Delete Codex"
                message="Delete this codex? This permanently deletes all of its categories and entries. This cannot be undone."
                isPending={deleting}
            />
        </Box>
    )
}
