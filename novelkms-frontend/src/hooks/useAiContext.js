import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiContextApi } from '../api/aiContext'
import { SCENE_KEYS } from './useScenes'

// ── Query key factory ─────────────────────────────────────────────────────────

export const AI_CONTEXT_KEYS = {
    codex:       (codexId) => ['aiContext', 'codex', codexId],
    bookSummary: (bookId)  => ['aiContext', 'bookSummary', bookId],
}

// ── Queries ───────────────────────────────────────────────────────────────────

/** All entries in one codex with their pinned flag — drives the Manage dialog. */
export function useCodexAiContext(codexId, enabled = true) {
    return useQuery({
        queryKey: AI_CONTEXT_KEYS.codex(codexId),
        queryFn:  () => aiContextApi.listForCodex(codexId),
        enabled:  !!codexId && enabled,
    })
}

/** Pinned count + words for a book's review scope — drives the review rail line. */
export function useBookAiContextSummary(bookId, enabled = true) {
    return useQuery({
        queryKey: AI_CONTEXT_KEYS.bookSummary(bookId),
        queryFn:  () => aiContextApi.bookSummary(bookId),
        enabled:  !!bookId && enabled,
    })
}

// ── Mutations ─────────────────────────────────────────────────────────────────

// A pin change can alter: the per-entry indicator (scene list + scene detail),
// the Manage dialog totals (codex query), and a book's review summary. Because a
// codex may be project-wide (and thus feed any book's review), the book summary
// key isn't always known here, so we invalidate the whole aiContext prefix.
function invalidatePins(qc, { chapterId, codexId, sceneId } = {}) {
    if (chapterId) qc.invalidateQueries({ queryKey: SCENE_KEYS.byChapter(chapterId) })
    if (sceneId)   qc.invalidateQueries({ queryKey: SCENE_KEYS.detail(sceneId) })
    if (codexId)   qc.invalidateQueries({ queryKey: AI_CONTEXT_KEYS.codex(codexId) })
    qc.invalidateQueries({ queryKey: ['aiContext'] })
}

/** Toggle a single Codex entry's pin. Pass chapterId/codexId for cache refresh. */
export function useSetScenePinned() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ sceneId, pinned }) => aiContextApi.setScenePinned(sceneId, pinned),
        onSuccess:  (_data, { sceneId, chapterId, codexId }) => invalidatePins(qc, { sceneId, chapterId, codexId }),
    })
}

/** Toggle every entry under one Codex category. chapterId is the category. */
export function useSetCategoryPinned() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ chapterId, pinned }) => aiContextApi.setCategoryPinned(chapterId, pinned),
        onSuccess:  (_data, { chapterId, codexId }) => invalidatePins(qc, { chapterId, codexId }),
    })
}
