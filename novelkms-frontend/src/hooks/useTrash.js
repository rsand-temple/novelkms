import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { trashApi } from '../api/trash'

export const TRASH_KEYS = {
	all: ['trash'],
}

/** List all items in the current user's trash, newest first. */
export function useTrash(enabled = true) {
	return useQuery({
		queryKey: TRASH_KEYS.all,
		queryFn: trashApi.list,
		enabled,
	})
}

/**
 * Restore a single trash item. On success, invalidates the trash list AND
 * every entity list that might now contain the restored item (projects, books,
 * chapters, scenes, codex, AI reviews).
 */
export function useRestoreTrashItem() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (batchId) => trashApi.restore(batchId),
		onSuccess: () => invalidateAll(qc),
	})
}

/** Permanently delete one trash item and its descendants. */
export function usePurgeTrashItem() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (batchId) => trashApi.purge(batchId),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: TRASH_KEYS.all })
		},
	})
}

/** Permanently delete everything in the trash. */
export function useEmptyTrash() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: () => trashApi.empty(),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: TRASH_KEYS.all })
		},
	})
}

/**
 * After a restore, the entity reappears in its parent list. Invalidate
 * broadly — the trash list plus every entity type that could be affected.
 */
function invalidateAll(qc) {
	qc.invalidateQueries({ queryKey: TRASH_KEYS.all })
	qc.invalidateQueries({ queryKey: ['projects'] })
	qc.invalidateQueries({ queryKey: ['books'] })
	qc.invalidateQueries({ queryKey: ['chapters'] })
	qc.invalidateQueries({ queryKey: ['parts'] })
	qc.invalidateQueries({ queryKey: ['scenes'] })
	qc.invalidateQueries({ queryKey: ['codex'] })
	qc.invalidateQueries({ queryKey: ['ai', 'reviews'] })
	qc.invalidateQueries({ queryKey: ['artifacts'] })
}
