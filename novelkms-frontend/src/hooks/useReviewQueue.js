import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { reviewQueueApi } from '../api/reviewQueue'

export const REVIEW_QUEUE_KEYS = {
	all:      ['reviewQueue'],
	list:     (filters) => ['reviewQueue', 'list', filters],
	package:  (id) => ['reviewQueue', 'package', id],
	snapshot: (id) => ['reviewQueue', 'snapshot', id],
}

export const QUEUE_PAGE_SIZE = 20

// A 404 package or the 409 profile gate will not fix itself on retry; a 5xx might.
function retryServerErrorsOnly(failureCount, error) {
	const status = error?.response?.status ?? 500
	return status >= 500 && failureCount < 2
}

/**
 * The public queue, paginated by offset. A full page implies there may be more, so
 * the next offset is (pages so far) × page size; a short page ends the list. The
 * filter object is part of the query key, so changing filters starts a fresh query
 * rather than appending to a stale one.
 */
export function useReviewQueue(filters) {
	return useInfiniteQuery({
		queryKey: REVIEW_QUEUE_KEYS.list(filters),
		queryFn: ({ pageParam }) => reviewQueueApi.queue({
			...filters,
			limit: QUEUE_PAGE_SIZE,
			offset: pageParam,
		}),
		initialPageParam: 0,
		getNextPageParam: (lastPage, allPages) =>
			lastPage.length === QUEUE_PAGE_SIZE ? allPages.length * QUEUE_PAGE_SIZE : undefined,
		retry: retryServerErrorsOnly,
	})
}

/** One package's metadata. The manuscript text is fetched separately, on demand. */
export function useReviewPackage(id, enabled = true) {
	return useQuery({
		queryKey: REVIEW_QUEUE_KEYS.package(id),
		queryFn:  () => reviewQueueApi.package(id),
		enabled:  !!id && enabled,
		retry:    retryServerErrorsOnly,
	})
}

/** The frozen snapshot (content_html) — fetched only when the reviewer opens the reader. */
export function usePackageSnapshot(id, enabled = true) {
	return useQuery({
		queryKey: REVIEW_QUEUE_KEYS.snapshot(id),
		queryFn:  () => reviewQueueApi.snapshot(id),
		enabled:  !!id && enabled,
		retry:    retryServerErrorsOnly,
	})
}
