import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { booksApi } from '../api/books'
import { PROJECT_KEYS } from './useProjects'

export const BOOK_KEYS = {
  byProject: (projectId) => ['books', 'byProject', projectId],
  detail: (id) => ['books', id],
}

export const useBooks = (projectId) => {
  return useQuery({
    queryKey: BOOK_KEYS.byProject(projectId),
    queryFn: () => booksApi.getByProject(projectId),
    enabled: !!projectId,
  })
}

export const useBook = (id) => {
  return useQuery({
    queryKey: BOOK_KEYS.detail(id),
    queryFn: () => booksApi.getById(id),
    enabled: !!id,
  })
}

export const useCreateBook = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, data }) => booksApi.create(projectId, data),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) })
    },
  })
}

export const useUpdateBook = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }) => booksApi.update(id, data),
    onSuccess: (_, { id, data }) => {
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.detail(id) })
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(data.projectId) })
    },
  })
}

export const useDeleteBook = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, projectId }) => booksApi.delete(id),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) })
      queryClient.invalidateQueries({ queryKey: PROJECT_KEYS.all })
    },
  })
}