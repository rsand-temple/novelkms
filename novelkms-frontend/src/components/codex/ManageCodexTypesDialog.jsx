import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, List, ListItem, ListItemText, Typography, Box, TextField, Stack, CircularProgress,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useQueries } from '@tanstack/react-query'
import { codexApi } from '../../api/codex'
import { useCodexChapters, useCreateCodexType, CODEX_KEYS } from '../../hooks/useCodex'
import CodexTypeEditorDialog from './CodexTypeEditorDialog'

/**
 * Manage Codex Types (design §6.1). Lists the codex's Types (each a category
 * chapter) with its active-field count, lets the author create a new Type, and
 * opens the Type editor. Type reordering and Type deletion (Trash) are handled
 * elsewhere — via the nav and the existing Codex Trash flow — and are out of
 * this E5 surface. Per-Type entry counts are deferred.
 */
export default function ManageCodexTypesDialog({ open, onClose, codexId, codexTitle }) {
    const { data: chapters, isLoading } = useCodexChapters(open ? codexId : null)
    const types = chapters ?? []

    // Field counts come from each Type's read model. These share the entry
    // form's cache key, so open Types cost no extra request.
    const typeQueries = useQueries({
        queries: types.map((t) => ({
            queryKey:  CODEX_KEYS.type(t.id),
            queryFn:   () => codexApi.getType(t.id),
            enabled:   open && !!t.id,
            staleTime: 5 * 60 * 1000,
        })),
    })
    const fieldCountById = {}
    types.forEach((t, i) => { fieldCountById[t.id] = typeQueries[i]?.data?.fields?.length })

    const [createOpen, setCreateOpen] = useState(false)
    const [newName, setNewName]       = useState('')
    const [newDesc, setNewDesc]       = useState('')
    const [createError, setCreateError] = useState(null)
    const createType = useCreateCodexType()

    const [editingTypeId, setEditingTypeId] = useState(null)

    const openCreate = () => {
        setNewName(''); setNewDesc(''); setCreateError(null); setCreateOpen(true)
    }

    const handleCreate = () => {
        const name = newName.trim()
        if (!name) return
        setCreateError(null)
        createType.mutate(
            { codexId, data: { name, description: newDesc.trim() || null } },
            {
                onSuccess: (type) => {
                    setCreateOpen(false)
                    setNewName(''); setNewDesc('')
                    // Chain straight into the editor so the author can add fields.
                    setEditingTypeId(type.id)
                },
                onError: (e) =>
                    setCreateError(e?.response?.data?.message ?? e?.message ?? 'Could not create the type.'),
            },
        )
    }

    const countLabel = (id) => {
        const n = fieldCountById[id]
        if (n === undefined) return '…'
        return `${n} field${n === 1 ? '' : 's'}`
    }

    return (
        <>
            <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth disableRestoreFocus>
                <DialogTitle>Manage Codex Types{codexTitle ? ` — ${codexTitle}` : ''}</DialogTitle>
                <DialogContent>
                    {isLoading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                            <CircularProgress size={24} />
                        </Box>
                    ) : types.length === 0 ? (
                        <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                            No types yet. Create one to start structuring this codex.
                        </Typography>
                    ) : (
                        <List dense disablePadding>
                            {types.map((t) => (
                                <ListItem
                                    key={t.id}
                                    disableGutters
                                    secondaryAction={
                                        <Button
                                            size="small"
                                            startIcon={<EditOutlinedIcon fontSize="small" />}
                                            onClick={() => setEditingTypeId(t.id)}
                                        >
                                            Edit
                                        </Button>
                                    }
                                >
                                    <ListItemText
                                        primary={t.title?.trim() ? t.title : 'Untitled type'}
                                        secondary={countLabel(t.id)}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    )}
                </DialogContent>
                <DialogActions sx={{ justifyContent: 'space-between' }}>
                    <Button startIcon={<AddIcon fontSize="small" />} onClick={openCreate}>
                        New Type
                    </Button>
                    <Button onClick={onClose}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Create Type */}
            <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth disableRestoreFocus>
                <DialogTitle>New Type</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ mt: 1 }}>
                        <TextField
                            autoFocus
                            label="Type name"
                            fullWidth
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                        />
                        <TextField
                            label="Description (optional)"
                            fullWidth
                            multiline
                            minRows={2}
                            value={newDesc}
                            onChange={(e) => setNewDesc(e.target.value)}
                        />
                        {createError && (
                            <Typography variant="body2" color="error">{createError}</Typography>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateOpen(false)} disabled={createType.isPending}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleCreate}
                        disabled={!newName.trim() || createType.isPending}
                    >
                        {createType.isPending ? 'Creating…' : 'Create'}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Type editor (from the list, or chained after create) */}
            <CodexTypeEditorDialog
                open={!!editingTypeId}
                onClose={() => setEditingTypeId(null)}
                typeId={editingTypeId}
                codexId={codexId}
            />
        </>
    )
}
