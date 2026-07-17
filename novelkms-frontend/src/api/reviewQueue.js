import client from './client'

// Reviewer side of the human-review network (slice 1C): the public queue, one
// package's metadata, and its frozen snapshot. All read-only — writing a review is
// slice 1D. Authorization is enforced server-side in ReviewAccessService: 404 for
// anything you may not see, 409 until you've claimed a handle. The shared Axios
// client already prefixes /api, so paths here do not.
export const reviewQueueApi = {
	queue:    (params) => client.get('/review/queue', { params: cleanParams(params) }).then(r => r.data),
	package:  (id)     => client.get(`/review/packages/${id}`).then(r => r.data),
	snapshot: (id)     => client.get(`/review/packages/${id}/snapshot`).then(r => r.data),
}

// Drop empty/nullish filters so the query string carries only what the reviewer
// actually set, and so blank inputs don't turn into `genre=` on the wire.
function cleanParams(params) {
	const out = {}
	for (const [key, value] of Object.entries(params ?? {})) {
		if (value !== null && value !== undefined && value !== '') {
			out[key] = value
		}
	}
	return out
}
