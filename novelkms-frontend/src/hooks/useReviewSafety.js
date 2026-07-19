import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reviewSafetyApi } from '../api/reviewSafety'
import { REVIEW_QUEUE_KEYS } from './useReviewQueue'
import { HUMAN_REVIEW_KEYS } from './useHumanReviews'

export const REVIEW_SAFETY_KEYS = {
	all:    ['reviewSafety'],
	blocks: ['reviewSafety', 'blocks'],
}

// A 404/409/400 will not fix itself on retry; a 5xx might.
function retryServerErrorsOnly(failureCount, error) {
	const status = error?.response?.status ?? 500
	return status >= 500 && failureCount < 2
}

/**
 * The signed-in user's block list: { handle, displayName, blockedAt }[]. Returns 409
 * when the caller has not claimed a handle — the "Blocked users" section only shows
 * for an existing profile, so callers pass enabled=false until they know a profile
 * exists rather than surfacing that 409.
 */
export function useMyBlocks(enabled = true) {
	return useQuery({
		queryKey: REVIEW_SAFETY_KEYS.blocks,
		queryFn:  reviewSafetyApi.blocks,
		enabled,
		retry:    retryServerErrorsOnly,
	})
}

// Blocking (or unblocking) changes which requests, received reviews, and reviews-
// I'm-writing are visible — the queue and both review lists carry a block filter —
// so all three go stale along with the block list itself.
function invalidateBlockAffected(qc) {
	qc.invalidateQueries({ queryKey: REVIEW_SAFETY_KEYS.blocks })
	qc.invalidateQueries({ queryKey: REVIEW_QUEUE_KEYS.all })
	qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.received })
	qc.invalidateQueries({ queryKey: HUMAN_REVIEW_KEYS.writing })
}

export function useBlockUser() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (handle) => reviewSafetyApi.block(handle),
		onSuccess:  () => invalidateBlockAffected(qc),
	})
}

export function useUnblockUser() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: (handle) => reviewSafetyApi.unblock(handle),
		onSuccess:  () => invalidateBlockAffected(qc),
	})
}

/**
 * File a content report. File-and-forget: there is nothing to refetch afterward
 * (the reporter cannot list their own reports in Phase 1), so this mutation touches
 * no cache — the caller just needs the returned { id, status } to confirm receipt.
 */
export function useReportContent() {
	return useMutation({
		mutationFn: (body) => reviewSafetyApi.report(body),
	})
}
