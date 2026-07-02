import client from './client'

const dataOrNull = (response) => response.status === 204 ? null : response.data

// Per-chapter editorials — an author-facing AI artifact family, separate from
// memory documents (api/chapterMemory.js) and summaries (api/summary.js).
//
// An editorial is a short editorial reading of a chapter (tone, genre drift,
// character arcs, storyline evolution) — the AI's overall "what do you think?".
// There is at most one per chapter; (re)generating overwrites it, and editing
// marks it EDITED. Unlike a memory document, an editorial is never consumed by
// any other AI function; it exists purely for the author.
export const editorialApi = {
	// The chapter's current editorial, or null when none exists (204).
	get: (chapterId) =>
		client.get(`/ai/editorial/chapters/${chapterId}`)
			.then(dataOrNull),
	// (Re)generates via the provider, using the same prior/reference context a
	// chapter review uses.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	// userGuidance is a one-time author note for this generation only.
	generate: (chapterId, body) =>
		client.post(`/ai/editorial/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit (marks the editorial EDITED).
	save: (chapterId, content) =>
		client.put(`/ai/editorial/chapters/${chapterId}`, { content }).then(r => r.data),
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	remove: (chapterId) =>
		client.delete(`/ai/editorial/chapters/${chapterId}`)
			.then(dataOrNull),
}
