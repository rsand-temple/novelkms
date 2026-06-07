import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon  from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import BookmarksIcon   from '@mui/icons-material/Bookmarks'
import { usePartChapters } from '../../hooks/useParts'
import ChapterItem from './ChapterItem'

/**
 * PartItem — nav tree node for a Part.
 *
 * Parts sit at the same indentation level as direct-book chapters (pl=7).
 * Chapters inside a part are rendered one level deeper via ChapterItem's
 * `depth` prop (pl = 7 + 1*3 = 10), and their scenes go to pl=13.
 *
 * Italic title text provides a quick visual cue that this is a structural
 * grouping rather than an authoring node.
 */
export default function PartItem({ part, selection, setSelection }) {
    const [open, setOpen] = useState(false)
    const { data: chapters } = usePartChapters(open ? part.id : null)

    const isSelected = selection.partId === part.id && !selection.chapterId

    const handleExpandToggle = (e) => {
        e.stopPropagation()
        setOpen((prev) => !prev)
    }

    const handleClick = () => {
        if (!open) setOpen(true)
        setSelection((prev) => ({ ...prev, partId: part.id, chapterId: null, sceneId: null }))
    }

    return (
        <Box>
            <ListItemButton
                selected={isSelected}
                onClick={handleClick}
                sx={{ pl: 7 }}
            >
                <ListItemIcon
                    sx={{ minWidth: 28, cursor: 'pointer' }}
                    onClick={handleExpandToggle}
                >
                    {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                </ListItemIcon>
                <ListItemIcon sx={{ minWidth: 28 }}>
                    <BookmarksIcon fontSize="small" />
                </ListItemIcon>
                <ListItemText
                    primary={part.title}
                    slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic' } } }}
                />
            </ListItemButton>

            <Collapse in={open} unmountOnExit>
                <Box>
                    {chapters?.map((chapter) => (
                        <ChapterItem
                            key={chapter.id}
                            chapter={chapter}
                            selection={selection}
                            setSelection={setSelection}
                            depth={1}
                        />
                    ))}
                </Box>
            </Collapse>
        </Box>
    )
}
