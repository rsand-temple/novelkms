import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Button,
} from '@mui/material'
import { useCreateScene } from '../../../hooks/useScenes'

const DIALOG_TITLES = {
    CHARACTER: 'New Character',
    VOICE:     'New Voice Sheet',
    PLOT:      'New Plot Element',
    WORLD:     'New World Entry',
    TIMELINE:  'New Timeline Entry',
    CANON:     'New Canon Entry',
    NOTES:     'New Note',
}

const FIELD_LABELS = {
    CHARACTER: 'Character Name',
    VOICE:     'Voice Sheet Name',
    PLOT:      'Element Title',
    WORLD:     'Entry Title',
    TIMELINE:  'Entry Title',
    CANON:     'Entry Title',
    NOTES:     'Note Title',
}

export default function AddCodexEntryDialog({ open, onClose, chapterId, codexCategory }) {
    const [title, setTitle] = useState('')
    const createScene = useCreateScene()

    const dialogTitle = DIALOG_TITLES[codexCategory] ?? 'New Entry'
    const fieldLabel  = FIELD_LABELS[codexCategory]  ?? 'Entry Title'

    const handleSubmit = () => {
        if (!title.trim()) return
        createScene.mutate(
            { chapterId, data: { title: title.trim() } },
            { onSuccess: () => { setTitle(''); onClose() } }
        )
    }

    const handleClose = () => {
        setTitle('')
        onClose()
    }

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth disableRestoreFocus>
            <DialogTitle>{dialogTitle}</DialogTitle>
            <DialogContent>
                <TextField
                    autoFocus
                    label={fieldLabel}
                    fullWidth
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
                    sx={{ mt: 1 }}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button onClick={handleSubmit} variant="contained" disabled={!title.trim() || createScene.isPending}>
                    Create
                </Button>
            </DialogActions>
        </Dialog>
    )
}
