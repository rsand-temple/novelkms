import { useState } from 'react'
import {
	Alert,
	Box,
	Button,
	CircularProgress,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import {
	useAiFormInstructions,
	useSaveAiFormInstructions,
	useRemoveAiFormInstructions,
} from '../../hooks/useAiFormInstructions'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

// Friendly description of where the currently shown text comes from, and what
// Save/Remove will do, given the scope being edited and the server's resolved
// `scope` (BOOK | PROJECT | USER | SYSTEM) + whether this scope owns an override.
function sourceNote(editScope, srcScope, own) {
	if (editScope === 'global') {
		return own
			? 'These are your personal instructions, used for every project unless a project or book overrides them.'
			: 'No personal instructions set — using the built-in default. Saving creates your personal instructions.'
	}
	const owner = editScope === 'project' ? 'project' : 'book'
	if (own) return `This ${owner} has its own instructions, overriding everything more general.`
	const from =
		srcScope === 'SYSTEM' ? 'the built-in default'
			: srcScope === 'USER' ? 'your personal instructions'
				: srcScope === 'PROJECT' ? 'this book’s project instructions'
					: 'the inherited value'
	return `No ${owner} override — currently using ${from}. Saving creates a ${owner} override.`
}

/**
 * Editor body (controlled). Mounted once per (scope, id) via the key in the
 * wrapper below, so its state seeds cleanly from `initial` without any effect
 * syncing. Save/Remove update local state explicitly from the server response,
 * so the displayed text and override indicator always reflect what was stored.
 */
function Editor({ scope, id, initial }) {
	const [text, setText] = useState(initial.instructions ?? '')
	const [own, setOwn] = useState(initial.hasOwnOverride)
	const [srcScope, setSrcScope] = useState(initial.scope)
	const [errorMsg, setErrorMsg] = useState(null)

	const { mutate: save, isPending: saving } = useSaveAiFormInstructions(scope, id)
	const { mutate: remove, isPending: removing } = useRemoveAiFormInstructions(scope, id)
	const busy = saving || removing

	const applyView = (v) => {
		setText(v.instructions ?? '')
		setOwn(v.hasOwnOverride)
		setSrcScope(v.scope)
	}

	const handleSave = () => {
		setErrorMsg(null)
		if (!text.trim()) {
			setErrorMsg('Instructions must not be blank. Use Remove to clear an override.')
			return
		}
		save(text.trim(), { onSuccess: applyView, onError: (e) => setErrorMsg(errMessage(e)) })
	}

	const handleRemove = () => {
		setErrorMsg(null)
		remove(undefined, { onSuccess: applyView, onError: (e) => setErrorMsg(errMessage(e)) })
	}

	const removeLabel = scope === 'global' ? 'Reset to default' : 'Remove override'

	return (
		<Box>
			<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
				Editorial guidance sent to the AI when it reviews your writing — its role and any
				constraints (for example, “do not rewrite the manuscript” or “do not introduce new
				characters”). The response format the app relies on is fixed automatically and is not
				part of this text.
			</Typography>

			<Alert severity="info" icon={false} sx={{ mb: 2, py: 0.5 }}>
				{sourceNote(scope, srcScope, own)}
			</Alert>

			{errorMsg && <Alert severity="error" sx={{ mb: 2 }}>{errorMsg}</Alert>}

			<TextField
				value={text}
				onChange={(e) => setText(e.target.value)}
				fullWidth
				multiline
				minRows={8}
				size="small"
				placeholder="You are an experienced editor reviewing a section of a novel…"
				disabled={busy}
			/>

			<Stack direction="row" spacing={1} sx={{ mt: 2, justifyContent: 'flex-end' }}>
				{own && (
					<Button color="warning" onClick={handleRemove} disabled={busy}>
						{removing ? 'Working…' : removeLabel}
					</Button>
				)}
				<Button variant="contained" onClick={handleSave} disabled={busy}>
					{saving ? 'Saving…' : 'Save'}
				</Button>
			</Stack>
		</Box>
	)
}

/**
 * AiFormInstructionsEditor
 *
 * Edits the AI review "form" instructions at one scope. Shared by the Settings
 * dialog's AI tab (global), Project Properties (project), and Book Properties
 * (book), so all three use one implementation.
 *
 * Props:
 *   scope  {'global'|'project'|'book'}
 *   id     {string|undefined}  project/book id; ignored for 'global'.
 */
export default function AiFormInstructionsEditor({ scope, id }) {
	const { data, isLoading } = useAiFormInstructions(scope, id)

	if (isLoading) {
		return (
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2 }}>
				<CircularProgress size={20} />
				<Typography variant="body2">Loading instructions…</Typography>
			</Box>
		)
	}
	if (!data) return null

	return <Editor key={`${scope}:${id ?? 'global'}`} scope={scope} id={id} initial={data} />
}