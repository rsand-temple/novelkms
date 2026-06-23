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
      queryClient.invalidateQueries({ queryKey: ['trash'] })
    },
  })
}

// ── Cover image mutations ─────────────────────────────────────────────────────

/**
 * Uploads a cover image for a book.
 * Variables: { id, file, projectId? }
 *   id        — book UUID
 *   file      — File object from <input type="file">
 *   projectId — if provided, also invalidates the book list so hasCoverImage
 *               is fresh in any list view.
 */
export const useUploadCoverImage = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }) => booksApi.uploadCoverImage(id, file),
    onSuccess: (_, { id, projectId }) => {
      // Invalidating the detail re-fetches hasCoverImage and the new updatedAt,
      // which the BookCoverPreview uses as a cache-buster on the image URL.
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.detail(id) })
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) })
      }
    },
  })
}

/**
 * Removes the cover image from a book.
 * Variables: { id, projectId? }
 */
export const useDeleteCoverImage = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }) => booksApi.deleteCoverImage(id),
    onSuccess: (_, { id, projectId }) => {
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.detail(id) })
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) })
      }
    },
  })
}
