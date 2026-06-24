import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { editorSettingsApi } from '../api/editorSettings'

// Resolved editor (document) settings cascade: PROJECT -> USER -> SYSTEM.
//
// Query keys are kept stable and prefix-friendly so a user-default change can
// invalidate every project's resolved settings (projects inherit the default).
export const EDITOR_SETTINGS_KEYS = {
	all:     ['editorSettings'],
	user:    ['editorSettings', 'user'],
	project: (projectId) => ['editorSettings', 'project', projectId],
	book:    (bookId) => ['editorSettings', 'book', bookId],
}

// ── Queries ───────────────────────────────────────────────────────────────────

/** The user default (USER override, else SYSTEM). */
export function useUserEditorSettings(enabled = true) {
	return useQuery({
		queryKey: EDITOR_SETTINGS_KEYS.user,
		queryFn:  editorSettingsApi.getUser,
		enabled,
	})
}

/** Resolved settings for a project (PROJECT override, else user default). */
export function useProjectEditorSettings(projectId, enabled = true) {
	return useQuery({
		queryKey: EDITOR_SETTINGS_KEYS.project(projectId),
		queryFn:  () => editorSettingsApi.getProject(projectId),
		enabled:  !!projectId && enabled,
	})
}

/** Resolved settings for a book (BOOK override, else project, else user default). */
export function useBookEditorSettings(bookId, enabled = true) {
	return useQuery({
		queryKey: EDITOR_SETTINGS_KEYS.book(bookId),
		queryFn:  () => editorSettingsApi.getBook(bookId),
		enabled:  !!bookId && enabled,
	})
}

// ── Mutations ──────────────────────────────────────────────────────────────────
// A user-default change can alter any inheriting project, so it invalidates the
// whole cascade. A project override only affects that project.

export function useUpdateUserEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (definition) => editorSettingsApi.putUser(definition),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}

export function useResetUserEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => editorSettingsApi.resetUser(),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}

export function useUpsertProjectEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId, definition }) => editorSettingsApi.upsertProject(projectId, definition),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}

export function useDeleteProjectEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId }) => editorSettingsApi.deleteProject(projectId),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}

// A book change only affects that book's resolution, but invalidating the whole
// cascade is cheap and keeps the editor's resolved settings in sync.
export function useUpsertBookEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, definition }) => editorSettingsApi.upsertBook(bookId, definition),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}

export function useDeleteBookEditorSettings() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId }) => editorSettingsApi.deleteBook(bookId),
		onSuccess:  () => qc.invalidateQueries({ queryKey: EDITOR_SETTINGS_KEYS.all }),
	})
}
