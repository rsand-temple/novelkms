import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { editorialApi } from '../api/editorial'

// Editorials are one-per-chapter and never aggregated (no book-wide status),
// so the cache is just a per-chapter document key.
export const EDITORIAL_KEYS = {
	all: ['editorial'],
	doc: (chapterId) => ['editorial', 'doc', chapterId],
}

// ── Query ──────────────────────────────────────────────────────────────────

// The current editorial for a chapter (null when none exists).
export function useChapterEditorial(chapterId, enabled = true) {
	return useQuery({
		queryKey: EDITORIAL_KEYS.doc(chapterId),
		queryFn:  () => editorialApi.get(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// ── Mutations ────────────────────────────────────────────────────────────────

// An editorial change touches only that chapter's document — there is no
// aggregate or coverage view to refresh.
function refresh(qc, chapterId) {
	qc.invalidateQueries({ queryKey: EDITORIAL_KEYS.doc(chapterId) })
}

export function useGenerateChapterEditorial() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, credentialId, model, userGuidance }) =>
			editorialApi.generate(chapterId, {
				credentialId: credentialId ?? null,
				model: model ?? null,
				userGuidance: userGuidance ?? null,
			}),
		onSuccess: (doc, { chapterId }) => {
			if (doc) qc.setQueryData(EDITORIAL_KEYS.doc(chapterId), doc)
			refresh(qc, chapterId)
		},
	})
}

export function useSaveChapterEditorial() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content }) => editorialApi.save(chapterId, content),
		onSuccess: (doc, { chapterId }) => {
			if (doc) qc.setQueryData(EDITORIAL_KEYS.doc(chapterId), doc)
			refresh(qc, chapterId)
		},
	})
}

export function useDeleteChapterEditorial() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId }) => editorialApi.remove(chapterId),
		onSuccess: (_data, { chapterId }) => {
			qc.setQueryData(EDITORIAL_KEYS.doc(chapterId), null)
			refresh(qc, chapterId)
		},
	})
}
