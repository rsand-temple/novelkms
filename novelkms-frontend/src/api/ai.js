import client from './client'

// AI provider credentials (BYOK) and review artifacts.
// The API key is write-only: it is sent on create/update but never returned —
// responses carry only a masked keyLast4.
export const aiApi = {
	// ── Credentials ──────────────────────────────────────────────────────────
	listCredentials: () => client.get('/ai/credentials').then(r => r.data),
	createCredential: (data) => client.post('/ai/credentials', data).then(r => r.data),
	updateCredential: (id, data) => client.put(`/ai/credentials/${id}`, data).then(r => r.data),
	deleteCredential: (id) => client.delete(`/ai/credentials/${id}`).then(r => r.data),
	setDefaultCredential: (id) => client.post(`/ai/credentials/${id}/default`).then(r => r.data),

	// ── Reviews ──────────────────────────────────────────────────────────────
	// A chapter review and a scene review are the same artifact, differing only
	// in scope. A scene review is filed under the scene's parent chapter, so it
	// appears in that chapter's review history.
	// body: { credentialId?: uuid|null, model?: string|null }
	runChapterReview: (chapterId, body) => client.post(`/ai/reviews/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	runSceneReview: (sceneId, body) => client.post(`/ai/reviews/scenes/${sceneId}`, body ?? {}).then(r => r.data),
	getReview: (reviewId) => client.get(`/ai/reviews/${reviewId}`).then(r => r.data),
	deleteReview: (reviewId) => client.delete(`/ai/reviews/${reviewId}`).then(r => r.data),
	listChapterReviews: (chapterId) => client.get(`/chapters/${chapterId}/reviews`).then(r => r.data),
	setRecommendationStatus: (reviewId, recId, status) =>
		client.put(`/ai/reviews/${reviewId}/recommendations/${recId}`, { status }).then(r => r.data),
	promoteRecommendation: (reviewId, recId, body = {}) =>
		client.post(`/ai/reviews/${reviewId}/recommendations/${recId}/promote`, body).then(r => r.data),
}
