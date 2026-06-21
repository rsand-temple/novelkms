import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark'
import { useCodexChapters } from '../../hooks/useCodex'
import { useNavContextMenu } from './NavContextMenuContext'
import CodexCategoryItem from './CodexCategoryItem'

/**
 * Codex container node. Clicking selects the codex (enabling toolbar delete);
 * right-click opens the context menu. Categories are rendered as children.
 * Inline action buttons are intentionally absent — use the toolbar and context
 * menu instead, matching the pattern of books and parts.
 */
export default function CodexItem({ codex, scope, selection, setSelection }) {
    const [open, setOpen] = useState(true)
    const { data: chapters } = useCodexChapters(open ? codex.id : null)
    const { openContextMenu } = useNavContextMenu()

    const basePl = scope === 'project' ? 4 : 7

    const isSelected =
        selection.codexId === codex.id &&
        !selection.chapterId &&
        !selection.sceneId

    const handleExpandToggle = (e) => {
        e.stopPropagation()
        setOpen(o => !o)
    }

    const handleClick = () => {
        if (!open) setOpen(true)
        setSelection((prev) => ({
            ...prev,
            bookId:        null,
            partId:        null,
            chapterId:     null,
            sceneId:       null,
            codexId:       codex.id,
            codexCategory: null,
        }))
    }

    const handleContextMenu = (e) => {
        // Select so toolbar reflects the codex, then open context menu.
        setSelection((prev) => ({
            ...prev,
            bookId: null, partId: null, chapterId: null, sceneId: null,
            codexId: codex.id, codexCategory: null,
        }))
        openContextMenu(e, 'codex', {
            id:        codex.id,
            title:     codex.title || 'Codex',
            projectId: selection.projectId,
        })
    }

    return (
        <Box>
            <ListItemButton selected={isSelected} onClick={handleClick} onContextMenu={handleContextMenu} sx={{ pl: basePl }}>
                <ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }} onClick={handleExpandToggle}>
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
        </Box>
    )
}
