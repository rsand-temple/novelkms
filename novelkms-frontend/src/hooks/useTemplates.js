import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templatesApi } from '../api/templates'

export const TEMPLATE_KEYS = {
	// type is always lowercased so callers that pass 'COVER' and 'cover'
	// share the same cache entry.  The backend normalises case server-side.
	global: (type) => ['templates', 'global', type?.toLowerCase()],
	book: (bookId, type) => ['templates', 'book', bookId, type?.toLowerCase()],
}

// ── Queries ───────────────────────────────────────────────────────────────────

export function useGlobalTemplate(type, enabled = true) {
	return useQuery({
		queryKey: TEMPLATE_KEYS.global(type),
		queryFn: () => templatesApi.getGlobal(type),
		enabled: !!type && enabled,
	})
}

export function useBookTemplate(bookId, type, enabled = true) {
	return useQuery({
		queryKey: TEMPLATE_KEYS.book(bookId, type),
		queryFn: () => templatesApi.getForBook(bookId, type),
		enabled: !!bookId && !!type && enabled,
	})
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export function useUpdateGlobalTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ type, content }) => templatesApi.updateGlobal(type, content),
		onSuccess: (_d, { type }) => {
			qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.global(type) })
			// Any book that has no override resolves to the global — invalidate
			// all cached book-template queries so BookCoverPreview refreshes.
			qc.invalidateQueries({ queryKey: ['templates', 'book'] })
		},
	})
}

export function useResetGlobalTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ type }) => templatesApi.resetGlobal(type),
		onSuccess: (_d, { type }) => {
			qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.global(type) })
			// Reset also reverts every book that has no override back to the
			// factory default — same cross-key invalidation as updateGlobal.
			qc.invalidateQueries({ queryKey: ['templates', 'book'] })
		},
	})
}

export function useUpsertBookTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, type, content }) => templatesApi.upsertBook(bookId, type, content),
		onSuccess: (_d, { bookId, type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.book(bookId, type) }),
	})
}

export function useDeleteBookTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, type }) => templatesApi.deleteBook(bookId, type),
		// Removing the override reverts the book to the global default; the GET
		// for this key will then resolve to the GLOBAL template.
		onSuccess: (_d, { bookId, type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.book(bookId, type) }),
	})
}