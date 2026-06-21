import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import PersonIcon from '@mui/icons-material/Person'
import RecordVoiceOverIcon from '@mui/icons-material/RecordVoiceOver'
import TimelineIcon from '@mui/icons-material/Timeline'
import PublicIcon from '@mui/icons-material/Public'
import ScheduleIcon from '@mui/icons-material/Schedule'
import VerifiedIcon from '@mui/icons-material/Verified'
import StickyNote2Icon from '@mui/icons-material/StickyNote2'
import FolderSpecialIcon from '@mui/icons-material/FolderSpecial'
import { useScenes } from '../../hooks/useScenes'
import { containerIds } from '../../dnd/dndUtils'
import { useNavContextMenu } from './NavContextMenuContext'
import SceneItem from './SceneItem'

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
 * A codex category — a chapter row inside a codex. Categories are fixed
 * (hardcoded at codex creation); they cannot be added, deleted, or renamed.
 * Their entries are scenes rendered via SceneItem inside a scenes-{chapterId}
 * SortableContext, so all existing DnD handlers work unchanged.
 *
 * Right-click opens a context menu with "Add Entry" only (no rename, move,
 * delete). Inline action buttons are intentionally absent.
 */
export default function CodexCategoryItem({ codex, chapter, basePl, selection, setSelection }) {
    const [open, setOpen] = useState(false)
    const { data: entries } = useScenes(open ? chapter.id : null)
    const { openContextMenu } = useNavContextMenu()

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

    const handleContextMenu = (e) => {
        setSelection((prev) => ({
            ...prev,
            bookId: null, partId: null,
            chapterId: chapter.id, sceneId: null,
            codexId: codex.id, codexCategory: chapter.codexCategory ?? null,
        }))
        openContextMenu(e, 'codex-category', {
            id:            chapter.id,
            title:         label,
            codexId:       codex.id,
            codexCategory: chapter.codexCategory ?? null,
            projectId:     selection.projectId,
        })
    }

    return (
        <Box>
            <ListItemButton selected={isSelected} onClick={handleClick} onContextMenu={handleContextMenu} sx={{ pl: basePl + 3 }}>
                <ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }} onClick={handleToggle}>
                    {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                </ListItemIcon>
                <ListItemIcon sx={{ minWidth: 28 }}><Icon fontSize="small" /></ListItemIcon>
                <ListItemText primary={label} slotProps={{ primary: { variant: 'body2' } }} />
            </ListItemButton>

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
        </Box>
    )
}
