import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '../api/projects'

export const PROJECT_KEYS = {
	all: ['projects'],
	detail: (id) => ['projects', id],
}

export const useProjects = () => {
	return useQuery({
		queryKey: PROJECT_KEYS.all,
		queryFn: projectsApi.getAll,
	})
}

export const useProject = (id) => {
	return useQuery({
		queryKey: PROJECT_KEYS.detail(id),
		queryFn: () => projectsApi.getById(id),
		enabled: !!id,
	})
}

export const useCreateProject = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (data) => projectsApi.create(data),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
		},
	})
}

export const useUpdateProject = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id, data }) => projectsApi.update(id, data),
		onSuccess: (_, { id }) => {
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.detail(id) })
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
		},
	})
}

export const useDeleteProject = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (id) => projectsApi.delete(id),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
			queryClient.invalidateQueries({ queryKey: ['trash'] })
		},
	})
}