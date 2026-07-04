import { useMutation, useQueryClient } from '@tanstack/react-query'
import { codexEntryApi } from '../api/codexEntry'
import { SCENE_KEYS } from './useScenes'

/**
 * Triggers a browser download of the codex entry as a DOCX file.
 *
 * Usage:
 *   const exportDocx = useExportCodexDocx()
 *   exportDocx.mutate({ sceneId, filename: 'Elena Vasquez.docx' })
 *
 * The `filename` prop is used as the download attribute on the anchor element.
 * Fall back to 'codex-entry.docx' if omitted.
 */
export const useExportCodexDocx = () => {
	return useMutation({
		mutationFn: ({ sceneId, filename }) =>
			codexEntryApi.exportDocx(sceneId).then((blob) => {
				const url = URL.createObjectURL(blob)
				const a   = document.createElement('a')
				a.href     = url
				a.download = filename || 'codex-entry.docx'
				document.body.appendChild(a)
				a.click()
				document.body.removeChild(a)
				URL.revokeObjectURL(url)
			}),
	})
}

/**
 * Imports a DOCX file for a codex entry, saves the result, and invalidates
 * the scene query cache so the editor reflects the imported content.
 *
 * Usage:
 *   const importDocx = useImportCodexDocx()
 *   importDocx.mutate({ sceneId, file })  // file is a File object
 *
 * onSuccess receives the updated Scene returned by the server.
 */
export const useImportCodexDocx = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ sceneId, file }) => codexEntryApi.importDocx(sceneId, file),
		onSuccess: (updatedScene) => {
			if (!updatedScene) return
			// Seed the detail cache with the freshly-saved scene so the next
			// render doesn't need a round-trip, then invalidate the list so the
			// nav word count and title update.
			queryClient.setQueryData(SCENE_KEYS.detail(updatedScene.id), updatedScene)
			if (updatedScene.chapterId) {
				queryClient.invalidateQueries({
					queryKey: SCENE_KEYS.byChapter(updatedScene.chapterId),
				})
			}
		},
	})
}

/**
 * Requests AI-generated field values and body text for a codex entry.
 * Does NOT save automatically — the caller applies the result to component
 * state, which triggers the existing autosave debounce.
 *
 * Usage:
 *   const fillWithAi = useFillCodexWithAi()
 *   fillWithAi.mutate({ sceneId, credentialId: null, userGuidance: '...' })
 *
 * onSuccess receives { fields: { key: value }, body: string, promptVersion }.
 */
export const useFillCodexWithAi = () => {
	return useMutation({
		mutationFn: ({ sceneId, credentialId, userGuidance }) =>
			codexEntryApi.fillWithAi(sceneId, {
				credentialId: credentialId ?? null,
				userGuidance: userGuidance ?? null,
			}),
	})
}
