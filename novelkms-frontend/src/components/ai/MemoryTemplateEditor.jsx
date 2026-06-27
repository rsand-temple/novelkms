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
	useMemoryTemplate,
	useSaveMemoryTemplate,
	useRemoveMemoryTemplate,
} from '../../hooks/useMemoryTemplate'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

// Friendly description of where the currently shown template comes from, and
// what Save/Remove will do, given the scope being edited and the server's
// resolved `scope` (BOOK | PROJECT | USER | SYSTEM) + whether this scope owns an
// override. Mirrors AiFormInstructionsEditor.sourceNote.
function sourceNote(editScope, srcScope, own) {
	if (editScope === 'global') {
		return own
			? 'This is your personal memory template, used for every project unless a project or book overrides it.'
			: 'No personal template set — using the built-in default. Saving creates your personal template.'
	}
	const owner = editScope === 'project' ? 'project' : 'book'
	if (own) return `This ${owner} has its own memory template, overriding everything more general.`
	const from =
		srcScope === 'SYSTEM' ? 'the built-in default'
			: srcScope === 'USER' ? 'your personal template'
				: srcScope === 'PROJECT' ? 'this book’s project template'
					: 'the inherited value'
	return `No ${owner} override — currently using ${from}. Saving creates a ${owner} override.`
}

/**
 * Editor body (controlled). Mounted once per (scope, id) via the key in the
 * wrapper below, so its state seeds cleanly from `initial` without effect
 * syncing. Save/Remove update local state from the server response.
 */
function Editor({ scope, id, initial }) {
	const [text, setText] = useState(initial.content ?? '')
	const [own, setOwn] = useState(initial.hasOwnOverride)
	const [srcScope, setSrcScope] = useState(initial.scope)
	const [errorMsg, setErrorMsg] = useState(null)

	const { mutate: save, isPending: saving } = useSaveMemoryTemplate(scope, id)
	const { mutate: remove, isPending: removing } = useRemoveMemoryTemplate(scope, id)
	const busy = saving || removing

	const applyView = (v) => {
		setText(v.content ?? '')
		setOwn(v.hasOwnOverride)
		setSrcScope(v.scope)
	}

	const handleSave = () => {
		setErrorMsg(null)
		if (!text.trim()) {
			setErrorMsg('The template must not be blank. Use Remove to clear an override.')
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
				The section structure the AI fills in when it generates a chapter’s memory document.
				These summaries are gathered, in book order, as “story so far” context when you review
				a later chapter. Keep it to section headers and brief guidance; add a fixed character
				roster here if you want one (for example, under “Character State Changes”).
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
				placeholder="CHAPTER {N}&#10;&#10;1. Key Events&#10;- …"
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
 * MemoryTemplateEditor
 *
 * Edits the memory-document template at one scope. Shared by the Settings
 * dialog's AI tab (global), Project Properties (project), and Book Properties
 * (book) — the same three mounts as AiFormInstructionsEditor.
 *
 * Props:
 *   scope  {'global'|'project'|'book'}
 *   id     {string|undefined}  project/book id; ignored for 'global'.
 */
export default function MemoryTemplateEditor({ scope, id }) {
	const { data, isLoading } = useMemoryTemplate(scope, id)

	if (isLoading) {
		return (
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2 }}>
				<CircularProgress size={20} />
				<Typography variant="body2">Loading template…</Typography>
			</Box>
		)
	}
	if (!data) return null

	return <Editor key={`${scope}:${id ?? 'global'}`} scope={scope} id={id} initial={data} />
}