import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { codexApi } from '../api/codex'
import { chaptersApi } from '../api/chapters'

// ── Query key factory ─────────────────────────────────────────────────────────

export const CODEX_KEYS = {
    categories: ()          => ['codex', 'categories'],
    type:       (typeId)    => ['codex', 'type', typeId],
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

// Resolves a codex entry's form schema from its own Type instance (the parent
// category chapter) rather than matching the global categories list by key.
// Each Type owns its active fields, so renaming/removing a field affects only
// that project. Returns { id, name, description, systemKey, fields }.
export function useCodexType(typeId) {
    return useQuery({
        queryKey: CODEX_KEYS.type(typeId),
        queryFn:  () => codexApi.getType(typeId),
        enabled:  !!typeId,
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
        onSuccess:  () => {
            // Broad invalidation covers both project-level and book-level queries
            // so the toolbar delete doesn't need to know the codex's owner scope.
            qc.invalidateQueries({ queryKey: ['codex'] })
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

// ── Type-editor mutations (E5) ────────────────────────────────────────────────
//
// A "Type" is a category chapter row. Creating one adds a chapter under the
// codex (so the nav chapter list must refresh); renaming writes chapter.title
// (nav row) plus chapter.codex_type_description. Field writes affect only the
// Type's own field set, so they invalidate that Type's read model, which the
// entry form and Manage Types dialog both consume.

export function useCreateCodexType() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ codexId, data }) => codexApi.createType(codexId, data),
        onSuccess:  (_type, { codexId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.chapters(codexId) }),
    })
}

export function useUpdateCodexType() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ typeId, data }) => codexApi.updateType(typeId, data),
        onSuccess:  (_type, { typeId, codexId }) => {
            qc.invalidateQueries({ queryKey: CODEX_KEYS.type(typeId) })
            // The name is chapter.title — the nav row reads it — so refresh the
            // owning codex's chapter list too when we know the codex.
            if (codexId) qc.invalidateQueries({ queryKey: CODEX_KEYS.chapters(codexId) })
        },
    })
}

export function useAddCodexTypeField() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ typeId, data }) => codexApi.addField(typeId, data),
        onSuccess:  (_field, { typeId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.type(typeId) }),
    })
}

export function useUpdateCodexTypeField() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ typeId, fieldKey, data }) => codexApi.updateField(typeId, fieldKey, data),
        onSuccess:  (_field, { typeId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.type(typeId) }),
    })
}

// Optimistically reorders the cached Type's fields by key so the drag settles
// instantly; the component stays a pure function of the query. Rolls back on
// error and reconciles with the server on settle.
export function useReorderCodexTypeFields() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ typeId, fieldKeys }) => codexApi.reorderFields(typeId, fieldKeys),
        onMutate: async ({ typeId, fieldKeys }) => {
            const key = CODEX_KEYS.type(typeId)
            await qc.cancelQueries({ queryKey: key })
            const prev = qc.getQueryData(key)
            if (prev?.fields) {
                const byKey = new Map(prev.fields.map(f => [f.key, f]))
                const next = fieldKeys.map(k => byKey.get(k)).filter(Boolean)
                // Defensive: keep any field not named in the order at the end so
                // an out-of-sync client never drops a field from the view.
                const named = new Set(fieldKeys)
                for (const f of prev.fields) if (!named.has(f.key)) next.push(f)
                qc.setQueryData(key, { ...prev, fields: next })
            }
            return { prev, typeId }
        },
        onError: (_e, _vars, ctx) => {
            if (ctx?.prev) qc.setQueryData(CODEX_KEYS.type(ctx.typeId), ctx.prev)
        },
        onSettled: (_d, _e, { typeId }) =>
            qc.invalidateQueries({ queryKey: CODEX_KEYS.type(typeId) }),
    })
}
