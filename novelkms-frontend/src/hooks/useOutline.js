import { useMutation, useQueryClient } from '@tanstack/react-query'
import { outlineApi } from '../api/outline'
import { PART_KEYS } from './useParts'
import { CHAPTER_KEYS } from './useChapters'

/**
 * Reorders a book's whole outline: its parts and its direct-book chapters, in
 * one ordered list.
 *
 * Call with: reorderOutline({ bookId, items: [{ type: 'PART'|'CHAPTER', id }] })
 * `items` must be the COMPLETE outline in the desired order — the server leaves
 * anything omitted at its current position, which would corrupt the sequence.
 *
 * Cache invalidation must match the blast radius of the change: a single drag
 * can renumber rows in BOTH the part table and the chapter table (moving a
 * prologue above Part I shifts every part down one), so both lists are stale
 * afterwards. Invalidating only the one the user visibly dragged would leave
 * the other rendering at yesterday's display_order and the merged outline would
 * come out scrambled.
 */
export function useReorderOutline() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, items }) => outlineApi.reorder(bookId, items),
		onSuccess: (_, { bookId }) => {
			qc.invalidateQueries({ queryKey: PART_KEYS.byBook(bookId) })
			qc.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
		},
	})
}
