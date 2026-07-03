import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiPromptTemplateApi } from '../api/aiPromptTemplate'

// Query keys for AI prompt templates. Kept under a per-type prefix so that
// editing the user global — which can change what every project/book resolves
// to in its dialog pre-population — invalidates all scopes for that type at
// once, mirroring useMemoryTemplate / useAiFormInstructions.
export const AI_PROMPT_TEMPLATE_KEYS = {
	all:   (type) => ['aiPromptTemplate', type],
	scope: (type, scope, id) => ['aiPromptTemplate', type, scope, id ?? 'global'],
}

/** Resolved prompt template for a scope, for editor pre-population. */
export function useAiPromptTemplate(templateType, scope, id, enabled = true) {
	return useQuery({
		queryKey: AI_PROMPT_TEMPLATE_KEYS.scope(templateType, scope, id),
		queryFn:  () => aiPromptTemplateApi.get(templateType, scope, id),
		enabled:  enabled && !!templateType && (scope === 'global' || !!id),
	})
}

/** Saves (creates/updates) the override at this scope. Pass the content string. */
export function useSaveAiPromptTemplate(templateType, scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (content) => aiPromptTemplateApi.save(templateType, scope, id, content),
		onSuccess:  () => qc.invalidateQueries({ queryKey: AI_PROMPT_TEMPLATE_KEYS.all(templateType) }),
	})
}

/** Clears the override at this scope, reverting to the next-most-specific value. */
export function useRemoveAiPromptTemplate(templateType, scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => aiPromptTemplateApi.remove(templateType, scope, id),
		onSuccess:  () => qc.invalidateQueries({ queryKey: AI_PROMPT_TEMPLATE_KEYS.all(templateType) }),
	})
}
