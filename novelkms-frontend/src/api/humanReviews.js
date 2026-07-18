import client from './client'

// Writing and receiving reviews (slice 1D). The reviewer's review is a sub-resource
// of the package it is against, so the write endpoints hang off
// /review/packages/{requestId}/review — a more specific path than the 1C package
// reads, owned by a separate resource without collision. The two list reads live
// under /review/reviews. Authorization is enforced server-side (404 for anything
// cross-user you may not touch, 409 until you've claimed a handle). The shared Axios
// client already prefixes /api, so paths here do not.
export const humanReviewApi = {
	// myReview returns 204 when the caller has not started a review yet; normalize
	// that to null so callers branch on presence rather than on a status code.
	myReview:  (requestId)       => client.get(`/review/packages/${requestId}/review`)
		.then(r => (r.status === 204 ? null : r.data)),
	saveDraft: (requestId, body) => client.put(`/review/packages/${requestId}/review`, body).then(r => r.data),
	submit:    (requestId, body) => client.post(`/review/packages/${requestId}/review/submit`, body).then(r => r.data),
	withdraw:  (requestId)       => client.post(`/review/packages/${requestId}/review/withdraw`).then(r => r.data),

	writing:   ()         => client.get('/review/reviews/writing').then(r => r.data),
	received:  ()         => client.get('/review/reviews/received').then(r => r.data),
	unread:    ()         => client.get('/review/reviews/received/unread').then(r => r.data),
	markRead:  (reviewId) => client.post(`/review/reviews/received/${reviewId}/read`).then(r => r.data),
}
