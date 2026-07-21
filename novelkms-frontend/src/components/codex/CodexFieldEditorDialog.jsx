import { useState } from 'react'
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, MenuItem, Button, Stack, Switch, FormControlLabel, Typography,
} from '@mui/material'
import { useAddCodexTypeField, useUpdateCodexTypeField } from '../../hooks/useCodex'

// User-facing input styles map to the backend's three field input types.
// (Decision 4: SHORT_TEXT / LONG_TEXT / SELECT are retained.)
const INPUT_STYLES = [
    { value: 'SHORT_TEXT', label: 'Single line' },
    { value: 'LONG_TEXT',  label: 'Multiple lines' },
    { value: 'SELECT',     label: 'Choice list' },
]

/**
 * Add or edit one field on a Codex Type. Reused for both create (field=null)
 * and edit. Help text and "feeds AI" are included deliberately: the field write
 * endpoint replaces the whole field definition, so an editor that omitted them
 * would silently blank those attributes on a seeded field. The immutable field
 * key is never shown or edited — renaming the label leaves stored entry values
 * untouched.
 *
 * The form body is mounted with a key derived from the field so switching which
 * field is edited re-initializes state cleanly (no useEffect init pattern).
 */
export default function CodexFieldEditorDialog({ open, onClose, typeId, field }) {
    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth disableRestoreFocus>
            {open && (
                <FieldForm
                    key={field?.key ?? 'new'}
                    typeId={typeId}
                    field={field ?? null}
                    onClose={onClose}
                />
            )}
        </Dialog>
    )
}

function FieldForm({ typeId, field, onClose }) {
    const isEdit = !!field

    const [label, setLabel]           = useState(field?.label ?? '')
    const [inputType, setInputType]   = useState(field?.type ?? 'SHORT_TEXT')
    const [optionsText, setOptionsText] = useState((field?.options ?? []).join('\n'))
    const [help, setHelp]             = useState(field?.help ?? '')
    const [feedsAi, setFeedsAi]       = useState(field?.feedsAi ?? true)
    const [error, setError]           = useState(null)

    const addField    = useAddCodexTypeField()
    const updateField = useUpdateCodexTypeField()
    const saving      = addField.isPending || updateField.isPending

    const options = optionsText.split('\n').map(s => s.trim()).filter(Boolean)
    const canSave = label.trim().length > 0 && !saving

    const handleSave = () => {
        if (!canSave) return
        setError(null)
        const data = {
            label:     label.trim(),
            inputType,
            options:   inputType === 'SELECT' ? options : null,
            help:      help.trim() || null,
            feedsAi,
        }
        const onError = (e) =>
            setError(e?.response?.data?.message ?? e?.message ?? 'Could not save the field.')

        if (isEdit) {
            updateField.mutate({ typeId, fieldKey: field.key, data }, { onSuccess: onClose, onError })
        } else {
            addField.mutate({ typeId, data }, { onSuccess: onClose, onError })
        }
    }

    return (
        <>
            <DialogTitle>{isEdit ? 'Edit Field' : 'Add Field'}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        autoFocus
                        label="Field label"
                        fullWidth
                        value={label}
                        onChange={(e) => setLabel(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter' && inputType !== 'SELECT') handleSave() }}
                    />

                    <TextField
                        select
                        label="Input style"
                        fullWidth
                        value={inputType}
                        onChange={(e) => setInputType(e.target.value)}
                    >
                        {INPUT_STYLES.map((s) => (
                            <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>
                        ))}
                    </TextField>

                    {inputType === 'SELECT' && (
                        <TextField
                            label="Choices (one per line)"
                            fullWidth
                            multiline
                            minRows={3}
                            value={optionsText}
                            onChange={(e) => setOptionsText(e.target.value)}
                            helperText="Each non-empty line is one selectable choice."
                        />
                    )}

                    <TextField
                        label="Help text (optional)"
                        fullWidth
                        value={help}
                        onChange={(e) => setHelp(e.target.value)}
                        helperText="Shown beneath the field to guide entry."
                    />

                    <FormControlLabel
                        control={<Switch checked={feedsAi} onChange={(e) => setFeedsAi(e.target.checked)} />}
                        label="Include this field's value in AI context"
                    />

                    {error && (
                        <Typography variant="body2" color="error">{error}</Typography>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={saving}>Cancel</Button>
                <Button variant="contained" onClick={handleSave} disabled={!canSave}>
                    {saving ? 'Saving…' : 'Save'}
                </Button>
            </DialogActions>
        </>
    )
}
