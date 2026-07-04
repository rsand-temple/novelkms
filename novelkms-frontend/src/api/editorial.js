import client from './client'

const dataOrNull = (response) => response.status === 204 ? null : response.data
const providerParams = (provider) => provider ? { params: { provider } } : undefined

// Per-chapter editorials — an author-facing AI artifact family, separate from
// memory documents (api/chapterMemory.js) and summaries (api/summary.js).
//
// An editorial is a short editorial reading of a chapter (tone, genre drift,
// character arcs, storyline evolution) — the AI's overall "what do you think?".
// Since the provider-variants work there is at most one per (chapter, provider).
// Passing `provider` targets that provider's variant exactly; omitting it returns
// the PREFERRED variant (default provider's, else most-recently-updated).
// (Re)generating overwrites the generating provider's document; editing marks it
// EDITED. Unlike a memory document, an editorial is never consumed by any other
// AI function; it exists purely for the author.
export const editorialApi = {
	// The chapter's editorial for `provider` exactly, or (with no provider) the
	// preferred variant; null when none exists (204).
	get: (chapterId, provider) =>
		client.get(`/ai/editorial/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),
	// Every provider variant of the chapter's editorial (newest first).
	variants: (chapterId) =>
		client.get(`/ai/editorial/chapters/${chapterId}/variants`).then(r => r.data),
	// (Re)generates via the provider, using the same prior/reference context a
	// chapter review uses (variant determined by the credential's provider).
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	generate: (chapterId, body) =>
		client.post(`/ai/editorial/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit to the given provider's variant (marks it EDITED).
	save: (chapterId, content, provider) =>
		client.put(`/ai/editorial/chapters/${chapterId}`, { content, provider: provider ?? null }).then(r => r.data),
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	remove: (chapterId, provider) =>
		client.delete(`/ai/editorial/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),
}
