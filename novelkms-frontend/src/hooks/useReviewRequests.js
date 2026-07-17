import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewRequestApi } from '../api/reviewRequests'

export const REVIEW_REQUEST_KEYS = {
	all:      ['reviewRequests'],
	mine:     ['reviewRequests', 'mine'],
	one:      (id) => ['reviewRequests', 'one', id],
	snapshot: (id) => ['reviewRequests', 'snapshot', id],
}

/** The author's own requests, newest first. */
export function useMyReviewRequests() {
	return useQuery({
		queryKey: REVIEW_REQUEST_KEYS.mine,
		queryFn:  reviewRequestApi.mine,
	})
}

/**
 * One request's full record. The edit dialog needs this rather than the My
 * Requests summary row, because the summary omits authorQuestions,
 * contentWarnings, and maxReviews while PUT rewrites every column — seeding the
 * form from the summary would silently blank those three fields.
 */
export function useReviewRequest(id, enabled = true) {
	return useQuery({
		queryKey: REVIEW_REQUEST_KEYS.one(id),
		queryFn:  () => reviewRequestApi.one(id),
		enabled:  !!id && enabled,
	})
}

/** The frozen snapshot behind a request (includes content_html). */
export function useReviewSnapshot(id, enabled = true) {
	return useQuery({
		queryKey: REVIEW_REQUEST_KEYS.snapshot(id),
		queryFn:  () => reviewRequestApi.snapshot(id),
		enabled:  !!id && enabled,
	})
}

export function usePublishReviewRequest() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, body }) => reviewRequestApi.publish(chapterId, body),
		onSuccess:  () => qc.invalidateQueries({ queryKey: REVIEW_REQUEST_KEYS.mine }),
	})
}

export function useUpdateReviewRequest() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ id, body }) => reviewRequestApi.update(id, body),
		onSuccess:  (_saved, { id }) => {
			qc.invalidateQueries({ queryKey: REVIEW_REQUEST_KEYS.mine })
			qc.invalidateQueries({ queryKey: REVIEW_REQUEST_KEYS.one(id) })
		},
	})
}

// pause/resume/close/withdraw all take an id, return the updated request, and
// only move status, so they share one factory and invalidate the same keys.
function useLifecycleMutation(action) {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (id) => reviewRequestApi[action](id),
		onSuccess:  (_updated, id) => {
			qc.invalidateQueries({ queryKey: REVIEW_REQUEST_KEYS.mine })
			qc.invalidateQueries({ queryKey: REVIEW_REQUEST_KEYS.one(id) })
		},
	})
}

export const usePauseReviewRequest    = () => useLifecycleMutation('pause')
export const useResumeReviewRequest   = () => useLifecycleMutation('resume')
export const useCloseReviewRequest    = () => useLifecycleMutation('close')
export const useWithdrawReviewRequest = () => useLifecycleMutation('withdraw')
