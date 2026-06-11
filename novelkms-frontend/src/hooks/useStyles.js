import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { stylesApi } from '../api/styles'

export const STYLE_KEYS = {
	all:          ['styles'],
	global:       ['styles', 'global'],
	globalKey:    (key)       => ['styles', 'global', key],
	projectSheet: (projectId) => ['styles', 'project', projectId],
	bookSheet:    (bookId)    => ['styles', 'book', bookId],
}

// ── Queries (resolved stylesheets) ─────────────────────────────────────────────

export function useGlobalStyles(enabled = true) {
	return useQuery({
		queryKey: STYLE_KEYS.global,
		queryFn:  () => stylesApi.getAllGlobal(),
		enabled,
	})
}

export function useProjectStyles(projectId, enabled = true) {
	return useQuery({
		queryKey: STYLE_KEYS.projectSheet(projectId),
		queryFn:  () => stylesApi.getProjectSheet(projectId),
		enabled:  !!projectId && enabled,
	})
}

export function useBookStyles(bookId, enabled = true) {
	return useQuery({
		queryKey: STYLE_KEYS.bookSheet(bookId),
		queryFn:  () => stylesApi.getBookSheet(bookId),
		enabled:  !!bookId && enabled,
	})
}

// ── Mutations (mostly for the Step 3 style editor) ─────────────────────────────
// Global edits can change any resolved sheet, so they invalidate all styles.

export function useUpdateGlobalStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ key, definition }) => stylesApi.updateGlobal(key, definition),
		onSuccess:  () => qc.invalidateQueries({ queryKey: STYLE_KEYS.all }),
	})
}

export function useResetGlobalStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ key }) => stylesApi.resetGlobal(key),
		onSuccess:  () => qc.invalidateQueries({ queryKey: STYLE_KEYS.all }),
	})
}

export function useUpsertProjectStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId, key, definition }) => stylesApi.upsertProject(projectId, key, definition),
		onSuccess:  (_d, { projectId }) => {
			qc.invalidateQueries({ queryKey: STYLE_KEYS.projectSheet(projectId) })
			qc.invalidateQueries({ queryKey: ['styles', 'book'] }) // book sheets may inherit
		},
	})
}

export function useDeleteProjectStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId, key }) => stylesApi.deleteProject(projectId, key),
		onSuccess:  (_d, { projectId }) => {
			qc.invalidateQueries({ queryKey: STYLE_KEYS.projectSheet(projectId) })
			qc.invalidateQueries({ queryKey: ['styles', 'book'] })
		},
	})
}

export function useUpsertBookStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, key, definition }) => stylesApi.upsertBook(bookId, key, definition),
		onSuccess:  (_d, { bookId }) => qc.invalidateQueries({ queryKey: STYLE_KEYS.bookSheet(bookId) }),
	})
}

export function useDeleteBookStyle() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, key }) => stylesApi.deleteBook(bookId, key),
		onSuccess:  (_d, { bookId }) => qc.invalidateQueries({ queryKey: STYLE_KEYS.bookSheet(bookId) }),
	})
}
