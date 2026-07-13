import client from './client'

// Public identity in the human-review network.
//
// `me` returns 204 (no body) when the signed-in user has not claimed a handle
// yet — that is the normal state, not an error — so it is normalized to null
// here and every caller can treat "no profile" as a falsy value.
export const reviewProfileApi = {
	me:          ()       => client.get('/review/profile/me').then(r => (r.status === 204 ? null : r.data)),
	create:      (body)   => client.post('/review/profile', body).then(r => r.data),
	update:      (body)   => client.put('/review/profile', body).then(r => r.data),
	checkHandle: (handle) => client.get(`/review/handles/${encodeURIComponent(handle)}/available`).then(r => r.data),
	byHandle:    (handle) => client.get(`/review/profiles/${encodeURIComponent(handle)}`).then(r => r.data),
}
