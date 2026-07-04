import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { summaryApi } from '../api/summary'

export const SUMMARY_KEYS = {
	all:             ['summary'],
	chapterDoc:      (chapterId) => ['summary', 'chapterDoc', chapterId],
	chapterVariants: (chapterId) => ['summary', 'chapterVariants', chapterId],
	bookDoc:         (bookId)    => ['summary', 'bookDoc', bookId],
	bookVariants:    (bookId)    => ['summary', 'bookVariants', bookId],
	chapters:        (bookId)    => ['summary', 'chapters', bookId],
	bookStatus:      (bookId)    => ['summary', 'bookStatus', bookId],
}

// ── Queries ───────────────────────────────────────────────────────────────────

// The preferred summary for a chapter (default provider's, else most-recent; null
// when none).
export function useChapterSummary(chapterId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.chapterDoc(chapterId),
		queryFn:  () => summaryApi.getChapter(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// Every provider variant of a chapter's summary (newest first). Drives the
// per-document provider selector.
export function useChapterSummaryVariants(chapterId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.chapterVariants(chapterId),
		queryFn:  () => summaryApi.chapterVariants(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// Every chapter's summary for a book, in linear book order (read-only aggregate).
export function useBookChapterSummaries(bookId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.chapters(bookId),
		queryFn:  () => summaryApi.bookChapterSummaries(bookId),
		enabled:  !!bookId && enabled,
	})
}

// The preferred book summary (default provider's, else most-recent; null when none).
export function useBookSummary(bookId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.bookDoc(bookId),
		queryFn:  () => summaryApi.getBook(bookId),
		enabled:  !!bookId && enabled,
	})
}

// Every provider variant of a book's summary (newest first).
export function useBookSummaryVariants(bookId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.bookVariants(bookId),
		queryFn:  () => summaryApi.bookVariants(bookId),
		enabled:  !!bookId && enabled,
	})
}

// Book-summary status + chapter-summary coverage counts.
export function useBookSummaryStatus(bookId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.bookStatus(bookId),
		queryFn:  () => summaryApi.bookStatus(bookId),
		enabled:  !!bookId && enabled,
	})
}

// A chapter-summary change moves that chapter's variants + preferred doc and the
// book-wide coverage/status (which feeds book-summary staleness); a book-summary
// change moves the book's variants + preferred doc and its status. We invalidate
// (not write straight into the preferred cache) because the changed variant may
// not be the preferred one.
function refreshChapter(qc, chapterId, bookId) {
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.chapterDoc(chapterId) })
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.chapterVariants(chapterId) })
	if (bookId) {
		qc.invalidateQueries({ queryKey: SUMMARY_KEYS.chapters(bookId) })
		qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookStatus(bookId) })
	}
}

function refreshBook(qc, bookId) {
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookDoc(bookId) })
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookVariants(bookId) })
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookStatus(bookId) })
}

// ── Chapter-summary mutations ─────────────────────────────────────────────────

export function useGenerateChapterSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, credentialId, model, userGuidance }) =>
			summaryApi.generateChapter(chapterId, {
				credentialId: credentialId ?? null,
				model: model ?? null,
				userGuidance: userGuidance ?? null,
			}),
		onSuccess: (_doc, { chapterId, bookId }) => refreshChapter(qc, chapterId, bookId),
	})
}

export function useSaveChapterSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content, provider }) => summaryApi.saveChapter(chapterId, content, provider),
		onSuccess: (_doc, { chapterId, bookId }) => refreshChapter(qc, chapterId, bookId),
	})
}

export function useDeleteChapterSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, provider }) => summaryApi.removeChapter(chapterId, provider),
		onSuccess: (_data, { chapterId, bookId }) => refreshChapter(qc, chapterId, bookId),
	})
}

// ── Book-summary mutations ────────────────────────────────────────────────────

export function useGenerateBookSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, credentialId, model, userGuidance }) =>
			summaryApi.generateBook(bookId, {
				credentialId: credentialId ?? null,
				model: model ?? null,
				userGuidance: userGuidance ?? null,
			}),
		onSuccess: (_doc, { bookId }) => refreshBook(qc, bookId),
	})
}

export function useSaveBookSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, content, provider }) => summaryApi.saveBook(bookId, content, provider),
		onSuccess: (_doc, { bookId }) => refreshBook(qc, bookId),
	})
}

export function useDeleteBookSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, provider }) => summaryApi.removeBook(bookId, provider),
		onSuccess: (_data, { bookId }) => refreshBook(qc, bookId),
	})
}
