import client from './client'

// Flat per-user UI preferences. GET returns a { key: value } map; values are
// opaque strings (the meaning of each key lives in the frontend).
export const preferencesApi = {
	getAll: ()           => client.get('/preferences').then(r => r.data),
	set:    (key, value) => client.put(`/preferences/${encodeURIComponent(key)}`, { value }).then(r => r.data),
	remove: (key)        => client.delete(`/preferences/${encodeURIComponent(key)}`).then(r => r.data),
}
