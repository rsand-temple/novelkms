import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { codexApi } from '../api/codex'
import { chaptersApi } from '../api/chapters'

// ── Query key factory ─────────────────────────────────────────────────────────

export const CODEX_KEYS = {
    categories: ()          => ['codex', 'categories'],
    byProject:  (projectId) => ['codex', 'byProject', projectId],
    byBook:     (bookId)    => ['codex', 'byBook', bookId],
    detail:     (id)        => ['codex', id],
    chapters:   (codexId)   => ['codex', codexId, 'chapters'],
}

// A project/book may legitimately have no codex — the GET returns 404 in that
// case. Treat that as a normal "null" result rather than a query error.
async function getOrNull(promise) {
    try {
        return await promise
    } catch (e) {
        if (e?.response?.status === 404) return null
        throw e
    }
}

// ── Queries ───────────────────────────────────────────────────────────────────

export function useCodexCategories() {
    return useQuery({
        queryKey:  CODEX_KEYS.categories(),
        queryFn:   () => codexApi.getCategories(),
        staleTime: 5 * 60 * 1000,
    })
}

export function useProjectCodex(projectId) {
    return useQuery({
        queryKey: CODEX_KEYS.byProject(projectId),
        queryFn:  () => getOrNull(codexApi.getByProject(projectId)),
        enabled:  !!projectId,
        retry:    false,
    })
}

export function useBookCodex(bookId) {
    return useQuery({
        queryKey: CODEX_KEYS.byBook(bookId),
        queryFn:  () => getOrNull(codexApi.getByBook(bookId)),
        enabled:  !!bookId,
        retry:    false,
    })
}

export function useCodexChapters(codexId) {
    return useQuery({
        queryKey: CODEX_KEYS.chapters(codexId),
        queryFn:  () => codexApi.getChapters(codexId),
        enabled:  !!codexId,
    })
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export function useCreateProjectCodex() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ projectId, data }) => codexApi.createForProject(projectId, data),
        onSuccess:  (_codex, { projectId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.byProject(projectId) }),
    })
}

export function useCreateBookCodex() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ bookId, data }) => codexApi.createForBook(bookId, data),
        onSuccess:  (_codex, { bookId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.byBook(bookId) }),
    })
}

export function useDeleteCodex() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ id }) => codexApi.delete(id),
        onSuccess:  (_d, { projectId, bookId }) => {
            if (projectId) qc.invalidateQueries({ queryKey: CODEX_KEYS.byProject(projectId) })
            if (bookId)    qc.invalidateQueries({ queryKey: CODEX_KEYS.byBook(bookId) })
        },
    })
}

export function useCreateCodexChapter() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ codexId, data }) => codexApi.createChapter(codexId, data),
        onSuccess:  (_c, { codexId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.chapters(codexId) }),
    })
}

export function useDeleteCodexChapter() {
    const qc = useQueryClient()
    return useMutation({
        // A codex category is a chapter row — delete via the chapter endpoint,
        // then refresh the codex's category list.
        mutationFn: ({ id }) => chaptersApi.delete(id),
        onSuccess:  (_d, { codexId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.chapters(codexId) }),
    })
}

export function useReorderCodexChapters() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ codexId, ids }) => codexApi.reorderChapters(codexId, ids),
        onSuccess:  (_r, { codexId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.chapters(codexId) }),
    })
}
