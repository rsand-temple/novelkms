import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templatesApi } from '../api/templates'

export const TEMPLATE_KEYS = {
	global: (type)         => ['templates', 'global', type],
	book:   (bookId, type) => ['templates', 'book', bookId, type],
}

// ── Queries ───────────────────────────────────────────────────────────────────

export function useGlobalTemplate(type, enabled = true) {
	return useQuery({
		queryKey: TEMPLATE_KEYS.global(type),
		queryFn:  () => templatesApi.getGlobal(type),
		enabled:  !!type && enabled,
	})
}

export function useBookTemplate(bookId, type, enabled = true) {
	return useQuery({
		queryKey: TEMPLATE_KEYS.book(bookId, type),
		queryFn:  () => templatesApi.getForBook(bookId, type),
		enabled:  !!bookId && !!type && enabled,
	})
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export function useUpdateGlobalTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ type, content }) => templatesApi.updateGlobal(type, content),
		onSuccess:  (_d, { type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.global(type) }),
	})
}

export function useResetGlobalTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ type }) => templatesApi.resetGlobal(type),
		onSuccess:  (_d, { type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.global(type) }),
	})
}

export function useUpsertBookTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, type, content }) => templatesApi.upsertBook(bookId, type, content),
		onSuccess:  (_d, { bookId, type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.book(bookId, type) }),
	})
}

export function useDeleteBookTemplate() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, type }) => templatesApi.deleteBook(bookId, type),
		// Removing the override reverts the book to the global default; the GET
		// for this key will then resolve to the GLOBAL template.
		onSuccess:  (_d, { bookId, type }) => qc.invalidateQueries({ queryKey: TEMPLATE_KEYS.book(bookId, type) }),
	})
}
