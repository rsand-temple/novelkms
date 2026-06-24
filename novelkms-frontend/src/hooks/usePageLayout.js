import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { pageLayoutApi } from '../api/pageLayout'

// A project-level change can alter what its books resolve to, so writes
// invalidate the whole prefix (cheap, and avoids stale book resolutions).
export const PAGE_LAYOUT_KEYS = {
	all:   ['pageLayout'],
	scope: (scope, id) => ['pageLayout', scope, id],
}

export function usePageLayout(scope, id, enabled = true) {
	return useQuery({
		queryKey: PAGE_LAYOUT_KEYS.scope(scope, id),
		queryFn:  () => pageLayoutApi.get(scope, id),
		enabled:  enabled && !!id,
	})
}

export function useSavePageLayout(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (body) => pageLayoutApi.save(scope, id, body),
		onSuccess:  () => qc.invalidateQueries({ queryKey: PAGE_LAYOUT_KEYS.all }),
	})
}

export function useRemovePageLayout(scope, id) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => pageLayoutApi.remove(scope, id),
		onSuccess:  () => qc.invalidateQueries({ queryKey: PAGE_LAYOUT_KEYS.all }),
	})
}
