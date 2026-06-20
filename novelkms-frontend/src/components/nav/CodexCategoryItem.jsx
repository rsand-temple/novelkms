import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText, IconButton, Tooltip } from '@mui/material'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import PersonIcon from '@mui/icons-material/Person'
import RecordVoiceOverIcon from '@mui/icons-material/RecordVoiceOver'
import TimelineIcon from '@mui/icons-material/Timeline'
import PublicIcon from '@mui/icons-material/Public'
import ScheduleIcon from '@mui/icons-material/Schedule'
import VerifiedIcon from '@mui/icons-material/Verified'
import StickyNote2Icon from '@mui/icons-material/StickyNote2'
import FolderSpecialIcon from '@mui/icons-material/FolderSpecial'
import { useScenes } from '../../hooks/useScenes'
import { useDeleteCodexChapter } from '../../hooks/useCodex'
import { containerIds } from '../../dnd/dndUtils'
import SceneItem from './SceneItem'
import AddCodexEntryDialog from './dialogs/AddCodexEntryDialog'
import DeleteConfirmDialog from './dialogs/DeleteConfirmDialog'

const CATEGORY_ICONS = {
    CHARACTER: PersonIcon,
    VOICE:     RecordVoiceOverIcon,
    PLOT:      TimelineIcon,
    WORLD:     PublicIcon,
    TIMELINE:  ScheduleIcon,
    CANON:     VerifiedIcon,
    NOTES:     StickyNote2Icon,
}

/**
 * A codex category — a chapter row inside a codex. Its entries are scenes, so
 * they render with the existing SceneItem inside a scenes-{chapterId}
 * SortableContext. That makes dragging a manuscript scene into this category
 * (and reordering / cross-category drags) work through the existing NavPanel
 * scene-reparent handlers with no DnD changes.
 *
 * Selecting the category sets chapterId (so the editor shows its entries and
 * the inspector lets you rename it) plus codexId for context. Selecting an
 * entry sets sceneId; SceneItem spreads the previous selection so codexId is
 * preserved.
 */
export default function CodexCategoryItem({ codex, chapter, basePl, selection, setSelection }) {
    const [open, setOpen]       = useState(false)
    const [addOpen, setAddOpen] = useState(false)
    const [delOpen, setDelOpen] = useState(false)

    const { data: entries } = useScenes(open ? chapter.id : null)
    const { mutate: deleteCategory, isPending: deleting } = useDeleteCodexChapter()

    const Icon = CATEGORY_ICONS[chapter.codexCategory] ?? FolderSpecialIcon
    const label = chapter.title?.trim() ? chapter.title : 'Category'

    const isSelected =
        selection.codexId === codex.id &&
        selection.chapterId === chapter.id &&
        !selection.sceneId

    const sceneContainerId = containerIds.scenes(String(chapter.id))
    const entryIds = (entries ?? []).map(s => String(s.id))

    const handleToggle = (e) => { e.stopPropagation(); setOpen(o => !o) }

    const handleClick = () => {
        if (!open) setOpen(true)
        setSelection((prev) => ({
            ...prev,
            bookId:        null,
            partId:        null,
            chapterId:     chapter.id,
            sceneId:       null,
            codexId:       codex.id,
            codexCategory: chapter.codexCategory ?? null,
        }))
    }

    const handleDelete = () => {
        deleteCategory(
            { id: chapter.id, codexId: codex.id },
            { onSuccess: () => {
                setDelOpen(false)
                if (selection.chapterId === chapter.id) {
                    setSelection((prev) => ({ ...prev, chapterId: null, sceneId: null }))
                }
            } }
        )
    }

    return (
        <Box sx={{ position: 'relative' }}>
            <ListItemButton selected={isSelected} onClick={handleClick} sx={{ pl: basePl + 3, pr: 9 }}>
                <ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }} onClick={handleToggle}>
                    {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                </ListItemIcon>
                <ListItemIcon sx={{ minWidth: 28 }}><Icon fontSize="small" /></ListItemIcon>
                <ListItemText primary={label} slotProps={{ primary: { variant: 'body2' } }} />
            </ListItemButton>

            <Box sx={{ position: 'absolute', right: 4, top: 0, height: 34, display: 'flex', alignItems: 'center' }}>
                <Tooltip title="Add entry">
                    <IconButton size="small" onClick={() => setAddOpen(true)} aria-label="Add codex entry">
                        <AddIcon fontSize="inherit" />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Delete category">
                    <IconButton size="small" onClick={() => setDelOpen(true)} aria-label="Delete codex category">
                        <DeleteIcon fontSize="inherit" />
                    </IconButton>
                </Tooltip>
            </Box>

            <Collapse in={open} unmountOnExit>
                <SortableContext id={sceneContainerId} items={entryIds} strategy={verticalListSortingStrategy}>
                    {(entries ?? []).map((scene) => (
                        <SceneItem
                            key={scene.id}
                            scene={scene}
                            chapterId={chapter.id}
                            partId={null}
                            selection={selection}
                            setSelection={setSelection}
                            depth={1}
                        />
                    ))}
                </SortableContext>
            </Collapse>

            <AddCodexEntryDialog open={addOpen} onClose={() => setAddOpen(false)} chapterId={chapter.id} />
            <DeleteConfirmDialog
                open={delOpen}
                onClose={() => setDelOpen(false)}
                onConfirm={handleDelete}
                title="Delete Category"
                message={`Delete category \u201C${label}\u201D? This permanently deletes the category and all of its entries. This cannot be undone.`}
                isPending={deleting}
            />
        </Box>
    )
}
