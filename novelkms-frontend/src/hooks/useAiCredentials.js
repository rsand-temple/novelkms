import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiApi } from '../api/ai'

export const AI_CREDENTIAL_KEYS = {
	all: ['ai', 'credentials'],
}

export function useAiCredentials() {
	return useQuery({
		queryKey: AI_CREDENTIAL_KEYS.all,
		queryFn: aiApi.listCredentials,
	})
}

export function useCreateAiCredential() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (data) => aiApi.createCredential(data),
		onSuccess: () => qc.invalidateQueries({ queryKey: AI_CREDENTIAL_KEYS.all }),
	})
}

export function useUpdateAiCredential() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ id, data }) => aiApi.updateCredential(id, data),
		onSuccess: () => qc.invalidateQueries({ queryKey: AI_CREDENTIAL_KEYS.all }),
	})
}

export function useDeleteAiCredential() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ id }) => aiApi.deleteCredential(id),
		onSuccess: () => qc.invalidateQueries({ queryKey: AI_CREDENTIAL_KEYS.all }),
	})
}

export function useSetDefaultAiCredential() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ id }) => aiApi.setDefaultCredential(id),
		onSuccess: () => qc.invalidateQueries({ queryKey: AI_CREDENTIAL_KEYS.all }),
	})
}
