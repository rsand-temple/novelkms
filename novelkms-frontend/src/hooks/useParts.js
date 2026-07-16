import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { partsApi } from '../api/parts'
import { CHAPTER_KEYS } from './useChapters'

// ── Query key factory ─────────────────────────────────────────────────────────

export const PART_KEYS = {
    all:      ()       => ['parts'],
    byBook:   (bookId) => ['parts', 'byBook', bookId],
    detail:   (id)     => ['parts', id],
    chapters: (partId) => ['parts', partId, 'chapters'],
}

// ── Queries ───────────────────────────────────────────────────────────────────

export function useParts(bookId) {
    return useQuery({
        queryKey: PART_KEYS.byBook(bookId),
        queryFn:  () => partsApi.getByBook(bookId),
        enabled:  !!bookId,
    })
}

export function usePart(id) {
    return useQuery({
        queryKey: PART_KEYS.detail(id),
        queryFn:  () => partsApi.getById(id),
        enabled:  !!id,
    })
}

export function usePartChapters(partId) {
    return useQuery({
        queryKey: PART_KEYS.chapters(partId),
        queryFn:  () => partsApi.getChapters(partId),
        enabled:  !!partId,
    })
}

// ── Mutations ─────────────────────────────────────────────────────────────────

/**
 * Creates a part. `data` may carry { anchorId, before } to insert it relative to
 * an existing outline item rather than appending.
 *
 * Both book-level lists are invalidated, not just the parts: inserting a part
 * into the shared outline sequence pushes the direct-book chapters after it down
 * one, so the chapter list's display_orders are stale too.
 */
export function useCreatePart() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ bookId, data }) => partsApi.create(bookId, data),
        onSuccess:  (_, { bookId }) => {
            qc.invalidateQueries({ queryKey: PART_KEYS.byBook(bookId) })
            qc.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
        },
    })
}

export function useUpdatePart() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ id, data }) => partsApi.update(id, data),
        onSuccess:  (part) => {
            qc.invalidateQueries({ queryKey: PART_KEYS.detail(part.id) })
            qc.invalidateQueries({ queryKey: PART_KEYS.byBook(part.bookId) })
        },
    })
}

export function useDeletePart() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ id }) => partsApi.delete(id),
        onSuccess:  (_, { bookId }) => {
            qc.invalidateQueries({ queryKey: PART_KEYS.byBook(bookId) })
            // ON DELETE SET NULL: the part's chapters become direct-book chapters.
            // Invalidate the book-level chapter list so they appear immediately.
            qc.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
        },
    })
}

// useReorderParts is gone. Parts no longer have an order of their own — they
// share one display_order sequence with the book's direct chapters, so the two
// can only be reordered together. Use useReorderOutline (hooks/useOutline.js).

/**
 * Creates a chapter inside a part. `data` may carry { anchorId, before } to
 * insert it relative to a sibling chapter in the same part rather than appending.
 */
export function useCreatePartChapter() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ partId, data }) => partsApi.createChapter(partId, data),
        onSuccess:  (_, { partId }) =>
            qc.invalidateQueries({ queryKey: PART_KEYS.chapters(partId) }),
    })
}

export function useReorderPartChapters() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: ({ partId, ids }) => partsApi.reorderChapters(partId, ids),
        onSuccess:  (_, { partId }) =>
            qc.invalidateQueries({ queryKey: PART_KEYS.chapters(partId) }),
    })
}
