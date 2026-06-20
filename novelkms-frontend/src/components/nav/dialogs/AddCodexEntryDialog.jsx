import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Button,
} from '@mui/material'
import { useCreateScene } from '../../../hooks/useScenes'

/**
 * Adds an entry to a codex category. A codex entry is a scene row whose parent
 * chapter is the category, so this reuses useCreateScene with the category's id.
 */
export default function AddCodexEntryDialog({ open, onClose, chapterId }) {
    const [title, setTitle] = useState('')
    const createScene = useCreateScene()

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
            <DialogTitle>New Entry</DialogTitle>
            <DialogContent>
                <TextField
                    autoFocus
                    label="Entry Title"
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
