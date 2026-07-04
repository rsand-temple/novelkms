import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { editorialApi } from '../api/editorial'

// Editorials are per-(chapter, provider) and never aggregated (no book-wide
// status), so the cache is a per-chapter preferred-doc key plus a variants key.
export const EDITORIAL_KEYS = {
	all:      ['editorial'],
	doc:      (chapterId) => ['editorial', 'doc', chapterId],
	variants: (chapterId) => ['editorial', 'variants', chapterId],
}

// ── Queries ──────────────────────────────────────────────────────────────────

// The preferred editorial for a chapter (default provider's, else most-recent;
// null when none).
export function useChapterEditorial(chapterId, enabled = true) {
	return useQuery({
		queryKey: EDITORIAL_KEYS.doc(chapterId),
		queryFn:  () => editorialApi.get(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// Every provider variant of a chapter's editorial (newest first). Drives the
// per-document provider selector.
export function useChapterEditorialVariants(chapterId, enabled = true) {
	return useQuery({
		queryKey: EDITORIAL_KEYS.variants(chapterId),
		queryFn:  () => editorialApi.variants(chapterId),
		enabled:  !!chapterId && enabled,
	})
}

// ── Mutations ────────────────────────────────────────────────────────────────

// An editorial change touches only that chapter's variants + preferred doc —
// there is no aggregate or coverage view to refresh.
function refresh(qc, chapterId) {
	qc.invalidateQueries({ queryKey: EDITORIAL_KEYS.doc(chapterId) })
	qc.invalidateQueries({ queryKey: EDITORIAL_KEYS.variants(chapterId) })
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
		onSuccess: (_doc, { chapterId }) => refresh(qc, chapterId),
	})
}

export function useSaveChapterEditorial() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, content, provider }) => editorialApi.save(chapterId, content, provider),
		onSuccess: (_doc, { chapterId }) => refresh(qc, chapterId),
	})
}

export function useDeleteChapterEditorial() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, provider }) => editorialApi.remove(chapterId, provider),
		onSuccess: (_data, { chapterId }) => refresh(qc, chapterId),
	})
}
