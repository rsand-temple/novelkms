import client from './client'

// Trust & safety surface of the human-review network (slice 1F): the blocks a user
// keeps, and the reports they file. Both are gated server-side on an active profile
// (409 profile_required / 403 suspended), and every identity on the wire is a handle,
// never a user id. The shared Axios client already prefixes /api, so paths here do not.
//
// Blocking is idempotent in both directions: POST an existing block is a no-op, and
// DELETE of a handle that isn't blocked still returns cleanly. Reporting is
// file-and-forget — the reporter gets back only {id, status} and cannot list their
// own reports in Phase 1.
export const reviewSafetyApi = {
	blocks:  ()       => client.get('/review/blocks').then(r => r.data),
	block:   (handle) => client.post('/review/blocks', { handle }).then(r => r.data),
	unblock: (handle) => client.delete(`/review/blocks/${encodeURIComponent(handle)}`).then(r => r.data),

	// body: { targetType, targetId | targetHandle, reason, detail }
	// targetType ∈ REQUEST | REVIEW | PROFILE (PROFILE resolves by targetHandle
	// server-side; there is no bare USER target). reason ∈ SPAM | HARASSMENT |
	// COPYRIGHT | HATE | EXPLICIT | OTHER. detail is capped at 2000 chars server-side.
	report:  (body)   => client.post('/review/reports', body).then(r => r.data),
}
