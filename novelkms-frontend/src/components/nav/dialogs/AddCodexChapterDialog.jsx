import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Button, MenuItem,
} from '@mui/material'
import { useCodexCategories, useCreateCodexChapter } from '../../../hooks/useCodex'

/**
 * Adds a category chapter to a codex. The category dropdown is built from the
 * codex_category lookup table; selecting a category pre-fills the title with the
 * category label (the user can override). "General" creates an uncategorized
 * chapter (codexCategory = null).
 */
export default function AddCodexChapterDialog({ open, onClose, codexId }) {
    const [title, setTitle] = useState('')
    const [categoryKey, setCategoryKey] = useState('')
    const { data: categories } = useCodexCategories()
    const { mutate: createChapter, isPending } = useCreateCodexChapter()

    const handleCategoryChange = (e) => {
        const key = e.target.value
        setCategoryKey(key)
        const cat = categories?.find(c => c.categoryKey === key)
        if (cat && !title.trim()) setTitle(cat.label)
    }

    const handleCreate = () => {
        if (!title.trim()) return
        createChapter(
            { codexId, data: { title: title.trim(), codexCategory: categoryKey || null } },
            { onSuccess: () => { setTitle(''); setCategoryKey(''); onClose() } }
        )
    }

    const handleClose = () => {
        setTitle('')
        setCategoryKey('')
        onClose()
    }

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth disableRestoreFocus>
            <DialogTitle>New Codex Category</DialogTitle>
            <DialogContent>
                <TextField
                    select
                    label="Category"
                    size="small"
                    fullWidth
                    value={categoryKey}
                    onChange={handleCategoryChange}
                    sx={{ mt: 1 }}
                >
                    <MenuItem value="">General (uncategorized)</MenuItem>
                    {(categories ?? []).map((c) => (
                        <MenuItem key={c.categoryKey} value={c.categoryKey}>{c.label}</MenuItem>
                    ))}
                </TextField>
                <TextField
                    label="Title"
                    size="small"
                    fullWidth
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') handleCreate() }}
                    sx={{ mt: 2 }}
                />
            </DialogContent>
            <DialogActions>
                <Button size="small" onClick={handleClose}>Cancel</Button>
                <Button size="small" variant="contained" onClick={handleCreate} disabled={isPending || !title.trim()}>
                    Create
                </Button>
            </DialogActions>
        </Dialog>
    )
}
