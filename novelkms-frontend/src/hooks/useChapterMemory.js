import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chapterMemoryApi } from '../api/chapterMemory'

export const CHAPTER_MEMORY_KEYS = {
	all:    ['chapterMemory'],
	doc:    (chapterId) => ['chapterMemory', 'doc', chapterId],
	status: (bookId)    => ['chapterMemory', 'status', bookId],
}

// The current memory document for a chapter (null when none exists).
export function useChapterMemory(chapterId, enabled = true) {
	return useQuery({
		queryKey: CHAPTER_MEMORY_KEYS.doc(chapterId),
		queryFn:  () => chapterMemoryApi.get(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// Per-chapter memory status for a book, in linear book order.
export function useChapterMemoryStatus(bookId, enabled = true) {
	return useQuery({
		queryKey: CHAPTER_MEMORY_KEYS.status(bookId),
		queryFn:  () => chapterMemoryApi.bookStatus(bookId),
		enabled:  !!bookId && enabled,
	})
}

// A generate/edit/delete changes both the chapter's doc and the book-wide status
// (a fresh generation can re-order the staleness picture), so invalidate both.
function refresh(qc, chapterId, bookId) {
	qc.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.doc(chapterId) })
	if (bookId) qc.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.status(bookId) })
}

export function useGenerateChapterMemory() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, credentialId, model, userGuidance }) =>
			chapterMemoryApi.generate(chapterId, {
				credentialId: credentialId ?? null,
				model: model ?? null,
				userGuidance: userGuidance ?? null,
			}),
		onSuccess: (doc, { chapterId, bookId }) => {
			if (doc) qc.setQueryData(CHAPTER_MEMORY_KEYS.doc(chapterId), doc)
			refresh(qc, chapterId, bookId)
		},
	})
}

export function useSaveChapterMemory() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content }) => chapterMemoryApi.save(chapterId, content),
		onSuccess: (doc, { chapterId, bookId }) => {
			if (doc) qc.setQueryData(CHAPTER_MEMORY_KEYS.doc(chapterId), doc)
			refresh(qc, chapterId, bookId)
		},
	})
}

export function useDeleteChapterMemory() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId }) => chapterMemoryApi.remove(chapterId),
		onSuccess: (_data, { chapterId, bookId }) => {
			qc.setQueryData(CHAPTER_MEMORY_KEYS.doc(chapterId), null)
			refresh(qc, chapterId, bookId)
		},
	})
}
