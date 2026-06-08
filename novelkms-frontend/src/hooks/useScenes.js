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

/**
 * Reorders scenes within a chapter.
 * Call with: reorderScenes({ chapterId, ids: [uuid, uuid, ...] })
 * ids must be the complete ordered list of scene IDs for the chapter,
 * including the first scene (which has no SceneBreak node preceding it).
 */
export const useReorderScenes = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, ids }) => scenesApi.reorderInChapter(chapterId, ids),
		onSuccess: (_, { chapterId }) => {
			queryClient.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) })
		},
	})
}

export function useMoveScene() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, chapterId, sourceIds, targetIds }) =>
      scenesApi.moveScene(id, { chapterId, sourceIds, targetIds }),
    onSuccess: () => {
      // Invalidates all scene lists — covers both source and target chapters
      queryClient.invalidateQueries({ queryKey: ['scenes'] })
    },
  })
}
