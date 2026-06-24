import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiFormInstructionsApi } from '../api/aiFormInstructions'

// Query keys for AI form instructions. Kept under one prefix so that editing the
// user global — which can change what every project/book resolves to in its
// dialog pre-population — invalidates all scopes at once, mirroring how
// useEditorSettings invalidates its whole cascade.
export const AI_FORM_KEYS = {
	all:   ['aiFormInstructions'],
	scope: (scope, id) => ['aiFormInstructions', scope, id ?? 'global'],
}

/** Resolved form instructions for a scope, for editor pre-population. */
export function useAiFormInstructions(scope, id, enabled = true) {
	return useQuery({
		queryKey: AI_FORM_KEYS.scope(scope, id),
		queryFn:  () => aiFormInstructionsApi.get(scope, id),
		enabled:  enabled && (scope === 'global' || !!id),
	})
}

/** Saves (creates/updates) the override at this scope. Pass the instructions string. */
export function useSaveAiFormInstructions(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (instructions) => aiFormInstructionsApi.save(scope, id, instructions),
		onSuccess:  () => qc.invalidateQueries({ queryKey: AI_FORM_KEYS.all }),
	})
}

/** Clears the override at this scope, reverting to the next-most-specific value. */
export function useRemoveAiFormInstructions(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => aiFormInstructionsApi.remove(scope, id),
		onSuccess:  () => qc.invalidateQueries({ queryKey: AI_FORM_KEYS.all }),
	})
}
