import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chapterMemoryApi } from '../api/chapterMemory'

export const CHAPTER_MEMORY_KEYS = {
	all:      ['chapterMemory'],
	doc:      (chapterId) => ['chapterMemory', 'doc', chapterId],
	variants: (chapterId) => ['chapterMemory', 'variants', chapterId],
	status:   (bookId)    => ['chapterMemory', 'status', bookId],
}

// The preferred memory document for a chapter (default provider's, else the
// most-recent; null when none). Used by the read-only peek surfaces and by
// AiDocProperties as a fallback when no provider is selected.
export function useChapterMemory(chapterId, enabled = true) {
	return useQuery({
		queryKey: CHAPTER_MEMORY_KEYS.doc(chapterId),
		queryFn:  () => chapterMemoryApi.get(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// Every provider variant of a chapter's memory document (newest first). Drives
// the per-document provider selector in the editor.
export function useChapterMemoryVariants(chapterId, enabled = true) {
	return useQuery({
		queryKey: CHAPTER_MEMORY_KEYS.variants(chapterId),
		queryFn:  () => chapterMemoryApi.variants(chapterId),
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

// A generate/edit/delete changes the chapter's variants, its preferred document,
// and the book-wide status (a fresh generation can re-order the staleness
// picture), so invalidate all three. We invalidate rather than write the result
// straight into the preferred-doc cache because the generated variant may not be
// the preferred one (a non-default provider).
function refresh(qc, chapterId, bookId) {
	qc.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.doc(chapterId) })
	qc.invalidateQueries({ queryKey: CHAPTER_MEMORY_KEYS.variants(chapterId) })
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
		onSuccess: (_doc, { chapterId, bookId }) => refresh(qc, chapterId, bookId),
	})
}

export function useSaveChapterMemory() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content, provider }) => chapterMemoryApi.save(chapterId, content, provider),
		onSuccess: (_doc, { chapterId, bookId }) => refresh(qc, chapterId, bookId),
	})
}

export function useDeleteChapterMemory() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, provider }) => chapterMemoryApi.remove(chapterId, provider),
		onSuccess: (_data, { chapterId, bookId }) => refresh(qc, chapterId, bookId),
	})
}
