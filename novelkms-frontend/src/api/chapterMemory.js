import client from './client'

const dataOrNull = (response) => response.status === 204 ? null : response.data
const providerParams = (provider) => provider ? { params: { provider } } : undefined

// Per-chapter memory documents and book-wide memory status.
//
// A memory document is a standardized chapter summary used as "story so far"
// context for later chapter reviews. Since the provider-variants work there is
// at most one per (chapter, provider): each configured provider keeps its own
// document. Passing `provider` targets that provider's variant exactly; omitting
// it lets the backend return the PREFERRED variant (the default provider's, else
// the most-recently-updated one) — the backward-compatible default. (Re)generating
// overwrites the generating provider's document; editing marks it EDITED.
export const chapterMemoryApi = {
	// The chapter's memory document for `provider` exactly, or (with no provider)
	// the preferred variant; null when none exists (204).
	get: (chapterId, provider) =>
		client.get(`/ai/memory/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),
	// Every provider variant of the chapter's memory document (newest first).
	// Drives the per-document provider selector.
	variants: (chapterId) =>
		client.get(`/ai/memory/chapters/${chapterId}/variants`).then(r => r.data),
	// (Re)generates via the provider. The generated variant is determined by the
	// credential's provider, so pass a credentialId to target a specific provider.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	// userGuidance is a one-time author note for this generation only — not a
	// persistent override (compare api/aiFormInstructions.js / api/memoryTemplate.js).
	generate: (chapterId, body) =>
		client.post(`/ai/memory/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit to the given provider's variant (marks it EDITED). With
	// no provider the backend edits the user's default-provider variant.
	save: (chapterId, content, provider) =>
		client.put(`/ai/memory/chapters/${chapterId}`, { content, provider: provider ?? null }).then(r => r.data),
	// Clears the given provider's variant (or the default-provider one when omitted).
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	remove: (chapterId, provider) =>
		client.delete(`/ai/memory/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),
	// Per-chapter staleness for a whole book, in linear book order, evaluated for
	// the given provider (or the user's default provider when omitted).
	bookStatus: (bookId, provider) =>
		client.get(`/books/${bookId}/memory-status`, providerParams(provider)).then(r => r.data),
}
