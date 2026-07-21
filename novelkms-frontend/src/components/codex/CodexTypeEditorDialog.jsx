import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Button, Stack, Box, Typography, CircularProgress, ListItemText,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import {
    DndContext, closestCenter, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import {
    SortableContext, verticalListSortingStrategy, useSortable, arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
    useCodexType, useUpdateCodexType, useReorderCodexTypeFields,
} from '../../hooks/useCodex'
import CodexFieldEditorDialog from './CodexFieldEditorDialog'

const INPUT_STYLE_LABELS = {
    SHORT_TEXT: 'Single line',
    LONG_TEXT:  'Multiple lines',
    SELECT:     'Choice list',
}

/**
 * The Codex Type editor (design §6.2). Edits the Type name and description
 * (persisted together via Save) and its ordered field set. Field add / edit /
 * reorder persist immediately against the Type's own endpoints, so the list is
 * always a pure function of the Type's read model. Field removal is not offered
 * here — non-destructive soft-remove arrives in E6.
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

    const updateType    = useUpdateCodexType()
    const reorderFields = useReorderCodexTypeFields()

    const fields = type.fields ?? []
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
                                            <FieldRow key={f.key} field={f} onEdit={(field) => setFieldEditor({ field })} />
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
        </>
    )
}

function FieldRow({ field, onEdit }) {
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
        </Box>
    )
}
