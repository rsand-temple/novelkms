import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { aiApi } from '../api/ai'

export const AI_REVIEW_KEYS = {
	byChapter: (chapterId) => ['ai', 'reviews', 'byChapter', chapterId],
	detail: (reviewId) => ['ai', 'reviews', reviewId],
}

// List of reviews for a chapter (newest first), without recommendations.
export function useChapterReviews(chapterId, enabled = true) {
	return useQuery({
		queryKey: AI_REVIEW_KEYS.byChapter(chapterId),
		queryFn: () => aiApi.listChapterReviews(chapterId),
		enabled: !!chapterId && enabled,
	})
}

// A single review WITH its recommendations.
export function useAiReview(reviewId, enabled = true) {
	return useQuery({
		queryKey: AI_REVIEW_KEYS.detail(reviewId),
		queryFn: () => aiApi.getReview(reviewId),
		enabled: !!reviewId && enabled,
	})
}

export function useRunChapterReview() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ chapterId, credentialId, model }) =>
			aiApi.runChapterReview(chapterId, { credentialId: credentialId ?? null, model: model ?? null }),
		onSuccess: (review, { chapterId }) => {
			qc.invalidateQueries({ queryKey: AI_REVIEW_KEYS.byChapter(chapterId) })
			// The run returns the full review (with recommendations) — seed its
			// detail cache so the results render immediately without a refetch.
			if (review?.id) qc.setQueryData(AI_REVIEW_KEYS.detail(review.id), review)
		},
	})
}

export function useSetRecommendationStatus() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ reviewId, recId, status }) =>
			aiApi.setRecommendationStatus(reviewId, recId, status),
		onSuccess: (review, { chapterId }) => {
			// Backend returns the updated review (with recommendations).
			if (review?.id) qc.setQueryData(AI_REVIEW_KEYS.detail(review.id), review)
			if (chapterId) qc.invalidateQueries({ queryKey: AI_REVIEW_KEYS.byChapter(chapterId) })
		},
	})
}

export function usePromoteRecommendation() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ reviewId, recId }) =>
			aiApi.promoteRecommendation(reviewId, recId),
		onSuccess: (review, { chapterId }) => {
			// Returns the updated review with the recommendation now marked promoted.
			if (review?.id) qc.setQueryData(AI_REVIEW_KEYS.detail(review.id), review)
			if (chapterId) qc.invalidateQueries({ queryKey: AI_REVIEW_KEYS.byChapter(chapterId) })
			// A new codex entry was created — refresh codex queries so it appears.
			qc.invalidateQueries({ queryKey: ['codex'] })
		},
	})
}
