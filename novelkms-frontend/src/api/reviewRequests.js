import client from './client'

// Author side of the human-review network: publishing a chapter as an immutable
// snapshot + request, and managing that request's lifecycle.
//
// Publishing hangs off the chapter path on purpose. TenantAuthorizationFilter
// authorizes the {chapterId} that follows a `chapters` segment, so the backend
// gets chapter-ownership checking for free; a chapterId carried in the body would
// be unauthorized. Everything else lives under /review/requests, where ownership
// is enforced in the service (404, never 403) rather than by the tenant filter.
//
// The shared Axios client already prefixes /api, so paths here do not.
export const reviewRequestApi = {
	mine:     ()                => client.get('/review/requests').then(r => r.data),
	one:      (id)              => client.get(`/review/requests/${id}`).then(r => r.data),
	snapshot: (id)              => client.get(`/review/requests/${id}/snapshot`).then(r => r.data),
	publish:  (chapterId, body) => client.post(`/chapters/${chapterId}/review-requests`, body).then(r => r.data),
	update:   (id, body)        => client.put(`/review/requests/${id}`, body).then(r => r.data),
	pause:    (id)              => client.post(`/review/requests/${id}/pause`).then(r => r.data),
	resume:   (id)              => client.post(`/review/requests/${id}/resume`).then(r => r.data),
	close:    (id)              => client.post(`/review/requests/${id}/close`).then(r => r.data),
	withdraw: (id)              => client.post(`/review/requests/${id}/withdraw`).then(r => r.data),
}
