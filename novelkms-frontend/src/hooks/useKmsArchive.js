import { useMutation, useQueryClient } from '@tanstack/react-query'
import { kmsArchiveApi } from '../api/kmsArchive'
import { PROJECT_KEYS } from './useProjects'

export const useValidateKmsArchive = () => {
	return useMutation({
		mutationFn: (file) => kmsArchiveApi.validate(file),
	})
}

export const useImportKmsArchive = () => {
	const queryClient = useQueryClient()

	return useMutation({
		mutationFn: (file) => kmsArchiveApi.importAsNewProjects(file),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
		},
	})
}
