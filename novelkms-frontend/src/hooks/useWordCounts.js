import { useQuery } from '@tanstack/react-query'
import client from '../api/client'

/**
 * Word-count queries — single source of truth.
 *
 * These endpoints are consumed from several places at once (the editor status
 * bar, the book cover preview's WORDS token, the project properties panel).
 * They previously each declared their own useQuery with the same queryKey but
 * *different* queryFn return shapes — one unwrapped `data.wordCount` to a bare
 * number, another returned the whole `{ wordCount, paragraphCount }` DTO.
 * TanStack Query deduplicates by key, so whichever observer registered first
 * decided the cached shape, and the loser read garbage: `Number(object)` → NaN
 * on the cover, `undefined?.wordCount ?? 0` → "0 words" in the toolbar.
 *
 * Every consumer must go through these hooks. They always resolve to the full
 * DTO shape:
 *
 *     { wordCount: number, paragraphCount: number }
 *
 * NOTE: the trailing 'word-count' key segment is load-bearing. useScenes and
 * EditorPanel invalidate these by predicate on the *last* element of the query
 * key, so the segment must stay last in every key below.
 */
export const WORD_COUNT_KEYS = {
	project: (projectId) => ['projects', projectId, 'word-count'],
	book:    (bookId)    => ['books', bookId, 'word-count'],
	part:    (partId)    => ['parts', partId, 'word-count'],
}

// Totals move only when scenes are saved, and those saves already invalidate
// by predicate — so a generous stale time avoids refetching on every remount
// of the cover preview / toolbar as the user clicks around the nav tree.
const WORD_COUNT_STALE_TIME = 60_000

/**
 * Normalizes an axios response into the canonical DTO shape. Missing or
 * non-numeric fields collapse to 0 rather than undefined so downstream
 * arithmetic (page estimates, "About N words" rounding) can never produce NaN.
 */
function toTotals(res) {
	const d = res?.data ?? {}
	return {
		wordCount:      Number.isFinite(d.wordCount)      ? d.wordCount      : 0,
		paragraphCount: Number.isFinite(d.paragraphCount) ? d.paragraphCount : 0,
	}
}

/** Project total across all books. */
export function useProjectWordCount(projectId, enabled = true) {
	return useQuery({
		queryKey:  WORD_COUNT_KEYS.project(projectId),
		queryFn:   () => client.get(`/projects/${projectId}/word-count`).then(toTotals),
		enabled:   !!projectId && enabled,
		staleTime: WORD_COUNT_STALE_TIME,
	})
}

/** Book total, including part and chapter headings. */
export function useBookWordCount(bookId, enabled = true) {
	return useQuery({
		queryKey:  WORD_COUNT_KEYS.book(bookId),
		queryFn:   () => client.get(`/books/${bookId}/word-count`).then(toTotals),
		enabled:   !!bookId && enabled,
		staleTime: WORD_COUNT_STALE_TIME,
	})
}

/** Part total, including the part heading and its chapter headings. */
export function usePartWordCount(partId, enabled = true) {
	return useQuery({
		queryKey:  WORD_COUNT_KEYS.part(partId),
		queryFn:   () => client.get(`/parts/${partId}/word-count`).then(toTotals),
		enabled:   !!partId && enabled,
		staleTime: WORD_COUNT_STALE_TIME,
	})
}
