import client from './client'

const dataOrNull = (response) => response.status === 204 ? null : response.data

// Per-chapter memory documents and book-wide memory status.
//
// A memory document is a standardized chapter summary used as "story so far"
// context for later chapter reviews. There is at most one per chapter; (re)generating
// overwrites it, and editing it marks it EDITED.
export const chapterMemoryApi = {
	// The chapter's current memory document, or null when none exists (204).
	get: (chapterId) =>
		client.get(`/ai/memory/chapters/${chapterId}`)
			.then(dataOrNull),
	// (Re)generates via the provider.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	// userGuidance is a one-time author note for this generation only — not a
	// persistent override (compare api/aiFormInstructions.js / api/memoryTemplate.js).
	generate: (chapterId, body) =>
		client.post(`/ai/memory/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit (marks the document EDITED).
	save: (chapterId, content) =>
		client.put(`/ai/memory/chapters/${chapterId}`, { content }).then(r => r.data),
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	remove: (chapterId) =>
		client.delete(`/ai/memory/chapters/${chapterId}`)
			.then(dataOrNull),
	// Per-chapter staleness for a whole book, in linear book order.
	bookStatus: (bookId) =>
		client.get(`/books/${bookId}/memory-status`).then(r => r.data),
}
