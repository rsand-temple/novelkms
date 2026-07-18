import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { humanReviewApi } from '../api/humanReviews'
import { REVIEW_QUEUE_KEYS } from './useReviewQueue'

export const HUMAN_REVIEW_KEYS = {
	all:      ['humanReviews'],
	mine:     (requestId) => ['humanReviews', 'mine', requestId],
	writing:  ['humanReviews', 'writing'],
	received: ['humanReviews', 'received'],
	unread:   ['humanReviews', 'received', 'unread'],
}

// A 404/409 will not fix itself on retry; a 5xx might.
function retryServerErrorsOnly(failureCount, error) {
	const status = error?.response?.status ?? 500
	return status >= 500 && failureCount < 2
}

/** The caller's own review of one package, or null if they have not started one. */
export function useMyReview(requestId, enabled = true) {
	return useQuery({
		queryKey: HUMAN_REVIEW_KEYS.mine(requestId),
		queryFn:  () => humanReviewApi.myReview(requestId),
		enabled:  !!requestId && enabled,
		retry:    retryServerErrorsOnly,
	})
}

// Save, submit, and withdraw all touch the same three surfaces: the caller's own
// review of this package, their "Reviews I'm Writing" list, and the queue (whose
// per-request review count and cap move once a review is submitted or withdrawn).
function invalidateReviewerViews(qc, requestId) {
	qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.mine(requestId) })
	qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.writing })
	qc.invalidateQueries({ queryKey: REVIEW_QUEUE_KEYS.all })
}

export function useSaveDraft() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ requestId, contentHtml, aiAssisted }) =>
			humanReviewApi.saveDraft(requestId, { contentHtml, aiAssisted }),
		onSuccess: (_saved, { requestId }) => invalidateReviewerViews(qc, requestId),
	})
}

export function useSubmitReview() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ requestId, contentHtml, aiAssisted }) =>
			humanReviewApi.submit(requestId, { contentHtml, aiAssisted }),
		onSuccess: (_saved, { requestId }) => invalidateReviewerViews(qc, requestId),
	})
}

export function useWithdrawReview() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ requestId }) => humanReviewApi.withdraw(requestId),
		onSuccess:  (_saved, { requestId }) => invalidateReviewerViews(qc, requestId),
	})
}

/** The reviewer's own active reviews — drafts and submissions. */
export function useReviewsWriting() {
	return useQuery({
		queryKey: HUMAN_REVIEW_KEYS.writing,
		queryFn:  humanReviewApi.writing,
		retry:    retryServerErrorsOnly,
	})
}

/** Submitted feedback on the author's own requests. */
export function useReviewsReceived() {
	return useQuery({
		queryKey: HUMAN_REVIEW_KEYS.received,
		queryFn:  humanReviewApi.received,
		retry:    retryServerErrorsOnly,
	})
}

/**
 * The unread-feedback count that badges the Reviews Received tab. Returns 0 for a
 * user who has not claimed a handle (the endpoint answers 409) rather than surfacing
 * an error on the tab bar.
 */
export function useUnreadReceivedCount() {
	return useQuery({
		queryKey: HUMAN_REVIEW_KEYS.unread,
		queryFn:  () => humanReviewApi.unread().then(d => d?.count ?? 0).catch(() => 0),
		retry:    false,
	})
}

export function useMarkReceivedRead() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (reviewId) => humanReviewApi.markRead(reviewId),
		onSuccess:  () => {
			qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.received })
			qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.unread })
		},
	})
}
