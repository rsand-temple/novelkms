import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chaptersApi } from '../api/chapters'
import { scenesApi } from '../api/scenes'
import { BOOK_KEYS } from './useBooks'

export const CHAPTER_KEYS = {
	byBook: (bookId) => ['chapters', 'byBook', bookId],
	detail: (id) => ['chapters', id],
}

export const useChapters = (bookId) => {
	return useQuery({
		queryKey: CHAPTER_KEYS.byBook(bookId),
		queryFn: () => chaptersApi.getByBook(bookId),
		enabled: !!bookId,
	})
}

export const useChapter = (id) => {
	return useQuery({
		queryKey: CHAPTER_KEYS.detail(id),
		queryFn: () => chaptersApi.getById(id),
		enabled: !!id,
	})
}

/**
 * Creates a direct-book chapter. `data` may carry { anchorId, before } to insert
 * it relative to an existing OUTLINE item — a part or another chapter — rather
 * than appending. Inserting before Part I is how a prologue gets made.
 */
export const useCreateChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ bookId, data }) => chaptersApi.create(bookId, data),
		onSuccess: async (chapter, { bookId }) => {
			// Auto-create an initial scene so content typed immediately after
			// chapter creation is preserved.  Blank title → backend generates
			// "New Scene {shortId}" via SceneDao.create.
			try {
				await scenesApi.create(chapter.id, { title: '' })
			} catch (err) {
				console.error('[useCreateChapter] Failed to create initial scene:', err)
			}
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
			// Inserting a chapter into the shared outline sequence pushes the parts
			// after it down one, so their display_orders are stale too. The key is
			// inlined rather than imported from useParts: useParts already imports
			// CHAPTER_KEYS from here, and importing back would close the cycle.
			queryClient.invalidateQueries({ queryKey: ['parts', 'byBook', bookId] })
		},
	})
}

export const useUpdateChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id, data }) => chaptersApi.update(id, data),
		onSuccess: (_, { id, data }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.detail(id) })
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(data.bookId) })
			// A codex category is a chapter row; refresh codex lists too so an
			// inspector rename of a category reflects immediately.
			queryClient.invalidateQueries({ queryKey: ['codex'] })
		},
	})
}

export const useDeleteChapter = () => {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ id }) => chaptersApi.delete(id),
		onSuccess: (_, { bookId }) => {
			queryClient.invalidateQueries({ queryKey: CHAPTER_KEYS.byBook(bookId) })
			queryClient.invalidateQueries({ queryKey: BOOK_KEYS.detail(bookId) })
			queryClient.invalidateQueries({ queryKey: ['codex'] })
			queryClient.invalidateQueries({ queryKey: ['trash'] })
		},
	})
}

// useReorderChapters is gone. A book's direct chapters share one display_order
// sequence with its parts and can only be reordered together — see
// useReorderOutline (hooks/useOutline.js). Chapters INSIDE a part still have
// their own sequence: useReorderPartChapters.

/**
 * Moves a chapter between containers.
 *
 * Call with: moveChapter({ id, partId, sourcePartId, sourceItems, targetItems })
 *
 * Both containers are named explicitly, and the item lists are typed refs
 * ([{ type: 'PART'|'CHAPTER', id }]) rather than bare UUIDs — either end may be
 * the book outline, which spans two tables, and the server has to know which one
 * each entry lives in. partId / sourcePartId are null when that end is the
 * outline rather than a part.
 */
export function useMoveChapter() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ id, partId, sourcePartId, sourceItems, targetItems }) =>
			chaptersApi.moveChapter(id, { partId, sourcePartId, sourceItems, targetItems }),
		onSuccess: () => {
			// Broad invalidation — a move can renumber the outline (both tables) and
			// a part's chapter list at once.
			queryClient.invalidateQueries({ queryKey: ['chapters'] });
			queryClient.invalidateQueries({ queryKey: ['parts'] });
		},
	});
}