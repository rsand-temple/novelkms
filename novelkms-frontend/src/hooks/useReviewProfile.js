import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewProfileApi } from '../api/reviewProfile'

export const REVIEW_PROFILE_KEYS = {
	me:      ['reviewProfile', 'me'],
	handle:  (handle) => ['reviewProfile', 'handle', handle],
	byHandle: (handle) => ['reviewProfile', 'byHandle', handle],
}

/** The signed-in user's profile, or null if they have not claimed a handle. */
export function useMyReviewProfile() {
	return useQuery({
		queryKey: REVIEW_PROFILE_KEYS.me,
		queryFn:  reviewProfileApi.me,
	})
}

/**
 * Live availability for the claim form.
 *
 * The backend runs the exact same rule set here that it runs on write, so the
 * form can never disagree with the write path about what is claimable. It also
 * excludes the caller's own current handle, so re-saving an unchanged profile
 * never reports a conflict against itself.
 */
export function useHandleAvailability(handle, enabled) {
	return useQuery({
		queryKey: REVIEW_PROFILE_KEYS.handle(handle),
		queryFn:  () => reviewProfileApi.checkHandle(handle),
		enabled:  !!enabled && !!handle,
		staleTime: 30_000,
	})
}

/** Another user's public profile. 404s for handles that are absent, hidden, or suspended. */
export function useReviewProfileByHandle(handle) {
	return useQuery({
		queryKey: REVIEW_PROFILE_KEYS.byHandle(handle),
		queryFn:  () => reviewProfileApi.byHandle(handle),
		enabled:  !!handle,
	})
}

export function useCreateReviewProfile() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: reviewProfileApi.create,
		onSuccess: (profile) => {
			qc.setQueryData(REVIEW_PROFILE_KEYS.me, profile)
			// A newly claimed handle changes what every availability check would
			// answer, so the whole namespace goes stale — not just this one handle.
			qc.invalidateQueries({ queryKey: ['reviewProfile'] })
		},
	})
}

export function useUpdateReviewProfile() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: reviewProfileApi.update,
		onSuccess: (profile) => {
			qc.setQueryData(REVIEW_PROFILE_KEYS.me, profile)
			qc.invalidateQueries({ queryKey: ['reviewProfile'] })
		},
	})
}
