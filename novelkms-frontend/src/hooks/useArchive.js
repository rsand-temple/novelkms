import { useMutation, useQueryClient } from '@tanstack/react-query'
import { archiveApi } from '../api/archive'
import { PROJECT_KEYS } from './useProjects'

export const useValidateKmsArchive = () => {
	return useMutation({
		mutationFn: (file) => archiveApi.validate(file),
	})
}

export const useImportKmsArchive = () => {
	const queryClient = useQueryClient()

	return useMutation({
		mutationFn: (file) => archiveApi.importAsNewProjects(file),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
		},
	})
}