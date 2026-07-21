import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Button, Stack, Box, Typography, CircularProgress, ListItemText,
    IconButton, Divider,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import DeleteIcon from '@mui/icons-material/Delete'
import {
    DndContext, closestCenter, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import {
    SortableContext, verticalListSortingStrategy, useSortable, arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
    useCodexType, useUpdateCodexType, useReorderCodexTypeFields,
    useCodexFieldUsage, useRemoveCodexTypeField, useRestoreCodexTypeField,
} from '../../hooks/useCodex'
import CodexFieldEditorDialog from './CodexFieldEditorDialog'

const INPUT_STYLE_LABELS = {
    SHORT_TEXT: 'Single line',
    LONG_TEXT:  'Multiple lines',
    SELECT:     'Choice list',
}

// "1 entry" / "N entries", used in the removed-fields area and the warning.
function entriesLabel(count) {
    return count === 1 ? '1 entry' : `${count} entries`
}

/**
 * The Codex Type editor (design §6.2). Edits the Type name and description
 * (persisted together via Save) and its ordered field set. Field add / edit /
 * reorder persist immediately against the Type's own endpoints, so the list is
 * always a pure function of the Type's read model.
 *
 * Field removal (E6) is non-destructive: removing a field hides it from the
 * entry form but preserves every stored value, and a "Removed fields" area lets
 * the author restore it. When entries already hold values for a field, removal
 * is gated behind a confirmation that names how many would be hidden.
 */
export default function CodexTypeEditorDialog({ open, onClose, typeId, codexId }) {
    const { data: type, isLoading } = useCodexType(open ? typeId : null)

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth disableRestoreFocus>
            {isLoading || !type ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 200 }}>
                    <CircularProgress size={24} />
                </Box>
            ) : (
                <TypeEditorContent key={type.id} type={type} codexId={codexId} onClose={onClose} />
            )}
        </Dialog>
    )
}

function TypeEditorContent({ type, codexId, onClose }) {
    const [name, setName]               = useState(type.name ?? '')
    const [description, setDescription] = useState(type.description ?? '')
    const [error, setError]             = useState(null)

    // Field editor sub-dialog: null when closed; { field } where field is the
    // one being edited, or null-field for "Add".
    const [fieldEditor, setFieldEditor] = useState(null) // { field } | null

    // Pending removal confirmation: { field, count } | null. `count` is null
    // when usage hasn't loaded yet (we still confirm, defensively).
    const [pendingRemoval, setPendingRemoval] = useState(null)

    const updateType    = useUpdateCodexType()
    const reorderFields = useReorderCodexTypeFields()
    const removeField   = useRemoveCodexTypeField()
    const restoreField  = useRestoreCodexTypeField()

    // Field usage (active + removed, with entry counts) drives the removed area
    // and the pre-removal warning. The active editable list stays sourced from
    // the Type read model so E5's optimistic reorder is undisturbed.
    const usageQuery = useCodexFieldUsage(type.id)
    const usage      = usageQuery.data ?? []
    const usageReady = usageQuery.isSuccess
    const removedFields = usage.filter(u => u.removed)
    const countFor = (key) => usage.find(u => u.key === key)?.entryCount ?? 0

    const fields    = type.fields ?? []
    const fieldKeys = fields.map(f => f.key)

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    )

    const canSave = name.trim().length > 0 && !updateType.isPending

    const handleSave = () => {
        if (!canSave) return
        setError(null)
        updateType.mutate(
            { typeId: type.id, codexId, data: { name: name.trim(), description: description.trim() || null } },
            {
                onSuccess: onClose,
                onError: (e) =>
                    setError(e?.response?.data?.message ?? e?.message ?? 'Could not save the type.'),
            },
        )
    }

    const handleDragEnd = (event) => {
        const { active, over } = event
        if (!over || active.id === over.id) return
        const from = fieldKeys.indexOf(String(active.id))
        const to   = fieldKeys.indexOf(String(over.id))
        if (from < 0 || to < 0) return
        reorderFields.mutate({ typeId: type.id, fieldKeys: arrayMove(fieldKeys, from, to) })
    }

    const doRemove = (fieldKey) => {
        setError(null)
        removeField.mutate(
            { typeId: type.id, fieldKey },
            { onError: (e) => setError(e?.response?.data?.message ?? e?.message ?? 'Could not remove the field.') },
        )
    }

    // Remove with a warning when the field holds data (or when usage hasn't
    // loaded and we can't yet be sure it's empty); otherwise remove directly.
    const handleRemoveClick = (field) => {
        const count = countFor(field.key)
        if (!usageReady) {
            setPendingRemoval({ field, count: null })
        } else if (count > 0) {
            setPendingRemoval({ field, count })
        } else {
            doRemove(field.key)
        }
    }

    const confirmRemoval = () => {
        if (pendingRemoval) doRemove(pendingRemoval.field.key)
        setPendingRemoval(null)
    }

    const handleRestore = (fieldKey) => {
        setError(null)
        restoreField.mutate(
            { typeId: type.id, fieldKey },
            { onError: (e) => setError(e?.response?.data?.message ?? e?.message ?? 'Could not restore the field.') },
        )
    }

    return (
        <>
            <DialogTitle>Edit Type</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        autoFocus
                        label="Type name"
                        fullWidth
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                    />
                    <TextField
                        label="Description (optional)"
                        fullWidth
                        multiline
                        minRows={2}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                    />

                    <Box>
                        <Typography variant="subtitle2" sx={{ mb: 1 }}>Fields</Typography>

                        {fields.length === 0 ? (
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                No fields yet. Add one to shape this type's entry form.
                            </Typography>
                        ) : (
                            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                                <SortableContext items={fieldKeys} strategy={verticalListSortingStrategy}>
                                    <Stack spacing={1}>
                                        {fields.map((f) => (
                                            <FieldRow
                                                key={f.key}
                                                field={f}
                                                onEdit={(field) => setFieldEditor({ field })}
                                                onRemove={handleRemoveClick}
                                                removing={removeField.isPending}
                                            />
                                        ))}
                                    </Stack>
                                </SortableContext>
                            </DndContext>
                        )}

                        <Button
                            size="small"
                            startIcon={<AddIcon fontSize="small" />}
                            onClick={() => setFieldEditor({ field: null })}
                            sx={{ mt: 1 }}
                        >
                            Add field
                        </Button>
                    </Box>

                    {removedFields.length > 0 && (
                        <Box>
                            <Divider sx={{ mb: 1.5 }} />
                            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Removed fields</Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                                These fields are hidden from the entry form. Their values are preserved and reappear if restored.
                            </Typography>
                            <Stack spacing={1}>
                                {removedFields.map((f) => (
                                    <Box
                                        key={f.key}
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 1,
                                            px: 1,
                                            py: 0.5,
                                            border: 1,
                                            borderColor: 'divider',
                                            borderRadius: 1,
                                            borderStyle: 'dashed',
                                        }}
                                    >
                                        <ListItemText
                                            primary={f.label}
                                            secondary={`${INPUT_STYLE_LABELS[f.type] ?? f.type} · ${entriesLabel(f.entryCount)}`}
                                            slotProps={{ primary: { variant: 'body2' }, secondary: { variant: 'caption' } }}
                                            sx={{ flex: 1, m: 0, color: 'text.secondary' }}
                                        />
                                        <Button
                                            size="small"
                                            onClick={() => handleRestore(f.key)}
                                            disabled={restoreField.isPending}
                                        >
                                            Restore
                                        </Button>
                                    </Box>
                                ))}
                            </Stack>
                        </Box>
                    )}

                    {error && (
                        <Typography variant="body2" color="error">{error}</Typography>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={updateType.isPending}>Cancel</Button>
                <Button variant="contained" onClick={handleSave} disabled={!canSave}>
                    {updateType.isPending ? 'Saving…' : 'Save'}
                </Button>
            </DialogActions>

            <CodexFieldEditorDialog
                open={!!fieldEditor}
                onClose={() => setFieldEditor(null)}
                typeId={type.id}
                field={fieldEditor?.field ?? null}
            />

            <Dialog open={!!pendingRemoval} onClose={() => setPendingRemoval(null)} maxWidth="xs" fullWidth>
                <DialogTitle>Remove field?</DialogTitle>
                <DialogContent>
                    <Typography variant="body2">
                        {pendingRemoval?.count > 0 ? (
                            <>
                                {pendingRemoval.count === 1
                                    ? '1 entry contains'
                                    : `${pendingRemoval.count} entries contain`} information in
                                {' '}<strong>{pendingRemoval?.field?.label}</strong>. Removing it hides that
                                information from the entry form. The values are preserved and can be restored later.
                            </>
                        ) : (
                            <>
                                Removing <strong>{pendingRemoval?.field?.label}</strong> hides it from the entry form.
                                Any values entered for it are preserved and can be restored later.
                            </>
                        )}
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setPendingRemoval(null)}>Cancel</Button>
                    <Button color="error" variant="contained" onClick={confirmRemoval} disabled={removeField.isPending}>
                        {removeField.isPending ? 'Removing…' : 'Remove'}
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    )
}

function FieldRow({ field, onEdit, onRemove, removing }) {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
        useSortable({ id: field.key })

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.6 : 1,
    }

    return (
        <Box
            ref={setNodeRef}
            style={style}
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                px: 1,
                py: 0.5,
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                bgcolor: 'background.paper',
            }}
        >
            <Box
                {...attributes}
                {...listeners}
                aria-label="Drag to reorder"
                sx={{ cursor: 'grab', color: 'text.disabled', px: 0.5, userSelect: 'none', fontSize: '1.1rem', lineHeight: 1, touchAction: 'none' }}
            >
                ⠿
            </Box>
            <ListItemText
                primary={field.label}
                secondary={INPUT_STYLE_LABELS[field.type] ?? field.type}
                slotProps={{ primary: { variant: 'body2' }, secondary: { variant: 'caption' } }}
                sx={{ flex: 1, m: 0 }}
            />
            <Button size="small" startIcon={<EditOutlinedIcon fontSize="small" />} onClick={() => onEdit(field)}>
                Edit
            </Button>
            <IconButton
                size="small"
                aria-label={`Remove ${field.label}`}
                onClick={() => onRemove(field)}
                disabled={removing}
            >
                <DeleteIcon fontSize="small" />
            </IconButton>
        </Box>
    )
}
