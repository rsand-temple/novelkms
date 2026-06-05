import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { scenesApi } from '../api/scenes'
import { CHAPTER_KEYS } from './useChapters'

export const SCENE_KEYS = {
	byChapter: (chapterId) => ['scenes', 'byChapter', chapterId],
	detail: (id) => ['scenes', id],
}

export const useScenes = (chapterId) => {
	return useQuery({
		queryKey: SCENE_KEYS.byChapter(chapterId),
		queryFn: () => scenesApi.getByChapter(chapterId),
		enabled: !!chapterId,
	})
}

export const useScene = (id) => {
	return useQuery({
		queryKey: SCENE_KEYS.detail(id),
		queryFn: () => scenesApi.getById(id),
		enabled: !!id,
	})
}

export const useCreateScene = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, data }) => scenesApi.create(chapterId, data),
		onSuccess: (_, { chapterId }) => {
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) })
		},
	})
}

export const useUpdateScene = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id, data }) => scenesApi.update(id, data),
		onSuccess: (_, { id, data }) => {
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(id) })
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(data.chapterId) })
		},
	})
}

export const useUpdateSceneContent = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id, content }) => scenesApi.updateContent(id, content),
		onSuccess: (_, { id }) => {
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.detail(id) })
		},
	})
}

export const useDeleteScene = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id }) => scenesApi.delete(id),
		onSuccess: (_, { chapterId }) => {
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) })
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.detail(chapterId) })
		},
	})
}
