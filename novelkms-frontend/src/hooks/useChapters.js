import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chaptersApi } from '../api/chapters'
import { BOOK_KEYS } from './useBooks'

export const CHAPTER_KEYS = {
	byBook: (bookId) => ['chapters', 'byBook', bookId],
	detail: (id) => ['chapters', id],
}

export const useChapters = (bookId) => {
	return useQuery({
		queryKey: CHAPTER_KEYS.byBook(bookId),
		queryFn: () => chaptersApi.getByBook(bookId),
		enabled: !!bookId,
	})
}

export const useChapter = (id) => {
	return useQuery({
		queryKey: CHAPTER_KEYS.detail(id),
		queryFn: () => chaptersApi.getById(id),
		enabled: !!id,
	})
}

export const useCreateChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, data }) => chaptersApi.create(bookId, data),
		onSuccess: (_, { bookId }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
		},
	})
}

export const useUpdateChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id, data }) => chaptersApi.update(id, data),
		onSuccess: (_, { id, data }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.detail(id) })
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(data.bookId) })
		},
	})
}

export const useDeleteChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id }) => chaptersApi.delete(id),
		onSuccess: (_, { bookId }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
			queryClient.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) })
		},
	})
}

/**
 * Reorders chapters within a book.
 * Call with: reorderChapters({ bookId, ids: [uuid, uuid, ...] })
 * ids must be the complete ordered list of chapter IDs for the book.
 */
export const useReorderChapters = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, ids }) => chaptersApi.reorderInBook(bookId, ids),
		onSuccess: (_, { bookId }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
		},
	})
}
