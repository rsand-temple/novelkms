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
	useAiPromptTemplate,
	useSaveAiPromptTemplate,
	useRemoveAiPromptTemplate,
} from '../../hooks/useAiPromptTemplate'

function errMessage(err) {
	const data = err?.response?.data
	return data?.message ?? (typeof data === 'string' ? data : null) ?? err?.message ?? 'Something went wrong.'
}

// Friendly description of where the currently shown text comes from, and what
// Save/Remove will do. Mirrors AiFormInstructionsEditor.sourceNote and
// MemoryTemplateEditor.sourceNote.
function sourceNote(editScope, srcScope, own) {
	if (editScope === 'global') {
		return own
			? 'This is your personal template, used for every project unless a project or book overrides it.'
			: 'No personal template set — using the built-in default. Saving creates your personal template.'
	}
	const owner = editScope === 'project' ? 'project' : 'book'
	if (own) return `This ${owner} has its own template, overriding everything more general.`
	const from =
		srcScope === 'SYSTEM'  ? 'the built-in default'
		: srcScope === 'USER'    ? 'your personal template'
		: srcScope === 'PROJECT' ? 'this book\u2019s project template'
		: 'the inherited value'
	return `No ${owner} override — currently using ${from}. Saving creates a ${owner} override.`
}

/**
 * Editor body (controlled). Mounted once per (templateType, scope, id) via the
 * key in the wrapper below, so state seeds cleanly from `initial` without any
 * effect syncing. Save/Remove update local state from the server response.
 */
function Editor({ templateType, scope, id, initial, description, placeholder }) {
	const [text, setText] = useState(initial.content ?? '')
	const [own, setOwn] = useState(initial.hasOwnOverride)
	const [srcScope, setSrcScope] = useState(initial.scope)
	const [errorMsg, setErrorMsg] = useState(null)

	const { mutate: save,   isPending: saving   } = useSaveAiPromptTemplate(templateType, scope, id)
	const { mutate: remove, isPending: removing } = useRemoveAiPromptTemplate(templateType, scope, id)
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
			{description && (
				<Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
					{description}
				</Typography>
			)}

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
				placeholder={placeholder}
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
 * AiPromptTemplateEditor
 *
 * Generic editor for the chapter-summary, book-summary, and editorial system
 * prompts. Each is stored in a separate per-type table with the same
 * book → project → user global → system default cascade used by the memory
 * template and AI review form instructions.
 *
 * Shared by the Settings dialog's AI tab (global scope), and available for
 * Project/Book Properties (project/book scope) as a future addition.
 *
 * Props:
 *   templateType  {'chapterSummary'|'bookSummary'|'editorial'}
 *   scope         {'global'|'project'|'book'}
 *   id            {string|undefined}  project/book id; ignored for 'global'.
 *   description   {string|undefined}  explanatory text shown above the field.
 *   placeholder   {string|undefined}  field placeholder.
 */
export default function AiPromptTemplateEditor({ templateType, scope, id, description, placeholder }) {
	const { data, isLoading } = useAiPromptTemplate(templateType, scope, id)

	if (isLoading) {
		return (
			<Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2 }}>
				<CircularProgress size={20} />
				<Typography variant="body2">Loading template…</Typography>
			</Box>
		)
	}
	if (!data) return null

	return (
		<Editor
			key={`${templateType}:${scope}:${id ?? 'global'}`}
			templateType={templateType}
			scope={scope}
			id={id}
			initial={data}
			description={description}
			placeholder={placeholder}
		/>
	)
}
