import { useQuery } from '@tanstack/react-query'
import { scratchpadApi } from '../api/scratchpad'

export const SCRATCHPAD_KEYS = {
	byBook: (bookId) => ['scratchpad', 'byBook', bookId],
}

/**
 * The book's Scratchpad chapter. The endpoint is get-or-create, so the first
 * call for a book writes the row — which is why this is deliberately cached hard
 * (`staleTime: Infinity`): the answer for a given book never changes, and a
 * refetch would be a pointless round trip on a write endpoint.
 *
 * The returned chapter's `id` is an ordinary chapter id, so the Scratchpad's
 * contents come from `useScenes(scratchpad.id)` and every scene mutation —
 * create, rename, reorder, move, trash — works on it unchanged. Nothing here
 * needs its own scene plumbing.
 */
export const useScratchpad = (bookId) => {
	return useQuery({
		queryKey: SCRATCHPAD_KEYS.byBook(bookId),
		queryFn: () => scratchpadApi.getByBook(bookId),
		enabled: !!bookId,
		staleTime: Infinity,
	})
}
