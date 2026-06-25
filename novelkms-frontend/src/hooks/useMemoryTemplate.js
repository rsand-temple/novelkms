import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { memoryTemplateApi } from '../api/memoryTemplate'

// Kept under one prefix so editing the user global — which can change what every
// project/book resolves to for pre-population — invalidates all scopes at once,
// mirroring useAiFormInstructions.
export const MEMORY_TEMPLATE_KEYS = {
	all:   ['memoryTemplate'],
	scope: (scope, id) => ['memoryTemplate', scope, id ?? 'global'],
}

export function useMemoryTemplate(scope, id, enabled = true) {
	return useQuery({
		queryKey: MEMORY_TEMPLATE_KEYS.scope(scope, id),
		queryFn:  () => memoryTemplateApi.get(scope, id),
		enabled:  enabled && (scope === 'global' || !!id),
	})
}

export function useSaveMemoryTemplate(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (content) => memoryTemplateApi.save(scope, id, content),
		onSuccess:  () => qc.invalidateQueries({ queryKey: MEMORY_TEMPLATE_KEYS.all }),
	})
}

export function useRemoveMemoryTemplate(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => memoryTemplateApi.remove(scope, id),
		onSuccess:  () => qc.invalidateQueries({ queryKey: MEMORY_TEMPLATE_KEYS.all }),
	})
}
