import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { summaryApi } from '../api/summary'

export const SUMMARY_KEYS = {
	all:        ['summary'],
	chapterDoc: (chapterId) => ['summary', 'chapterDoc', chapterId],
	bookDoc:    (bookId)    => ['summary', 'bookDoc', bookId],
	chapters:   (bookId)    => ['summary', 'chapters', bookId],
	bookStatus: (bookId)    => ['summary', 'bookStatus', bookId],
}

// ── Queries ───────────────────────────────────────────────────────────────────

// The current summary for a chapter (null when none exists).
export function useChapterSummary(chapterId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.chapterDoc(chapterId),
		queryFn:  () => summaryApi.getChapter(chapterId),
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

// The current book summary (null when none exists).
export function useBookSummary(bookId, enabled = true) {
	return useQuery({
		queryKey: SUMMARY_KEYS.bookDoc(bookId),
		queryFn:  () => summaryApi.getBook(bookId),
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

// A chapter-summary change moves both the chapter's summary and the book-wide
// coverage/status (which feeds book-summary staleness), so invalidate all three.
function refreshChapter(qc, chapterId, bookId) {
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.chapterDoc(chapterId) })
	if (bookId) {
		qc.invalidateQueries({ queryKey: SUMMARY_KEYS.chapters(bookId) })
		qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookStatus(bookId) })
	}
}

function refreshBook(qc, bookId) {
	qc.invalidateQueries({ queryKey: SUMMARY_KEYS.bookDoc(bookId) })
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
		onSuccess: (doc, { chapterId, bookId }) => {
			if (doc) qc.setQueryData(SUMMARY_KEYS.chapterDoc(chapterId), doc)
			refreshChapter(qc, chapterId, bookId)
		},
	})
}

export function useSaveChapterSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content }) => summaryApi.saveChapter(chapterId, content),
		onSuccess: (doc, { chapterId, bookId }) => {
			if (doc) qc.setQueryData(SUMMARY_KEYS.chapterDoc(chapterId), doc)
			refreshChapter(qc, chapterId, bookId)
		},
	})
}

export function useDeleteChapterSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId }) => summaryApi.removeChapter(chapterId),
		onSuccess: (_data, { chapterId, bookId }) => {
			qc.setQueryData(SUMMARY_KEYS.chapterDoc(chapterId), null)
			refreshChapter(qc, chapterId, bookId)
		},
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
		onSuccess: (doc, { bookId }) => {
			if (doc) qc.setQueryData(SUMMARY_KEYS.bookDoc(bookId), doc)
			refreshBook(qc, bookId)
		},
	})
}

export function useSaveBookSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, content }) => summaryApi.saveBook(bookId, content),
		onSuccess: (doc, { bookId }) => {
			if (doc) qc.setQueryData(SUMMARY_KEYS.bookDoc(bookId), doc)
			refreshBook(qc, bookId)
		},
	})
}

export function useDeleteBookSummary() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId }) => summaryApi.removeBook(bookId),
		onSuccess: (_data, { bookId }) => {
			qc.setQueryData(SUMMARY_KEYS.bookDoc(bookId), null)
			refreshBook(qc, bookId)
		},
	})
}
