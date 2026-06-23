import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { preferencesApi } from '../api/preferences'

export const PREFERENCE_KEYS = {
	all: ['preferences'],
}

/** All per-user preferences as a { key: value } map. */
export function usePreferences(enabled = true) {
	return useQuery({
		queryKey: PREFERENCE_KEYS.all,
		queryFn:  preferencesApi.getAll,
		enabled,
	})
}

export function useSetPreference() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ key, value }) => preferencesApi.set(key, value),
		// Optimistically patch the cached map so consumers update immediately.
		onSuccess: (_d, { key, value }) => {
			qc.setQueryData(PREFERENCE_KEYS.all, (prev) => ({ ...(prev ?? {}), [key]: value }))
		},
	})
}
