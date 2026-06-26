import client from './client'

// Per-chapter memory documents and book-wide memory status.
//
// A memory document is a standardized chapter summary used as "story so far"
// context for later chapter reviews. There is at most one per chapter; (re)generating
// overwrites it, and editing it marks it EDITED.
export const chapterMemoryApi = {
	// The chapter's current memory document, or null when none exists (404).
	get: (chapterId) =>
		client.get(`/ai/memory/chapters/${chapterId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),
	// (Re)generates via the provider.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	// userGuidance is a one-time author note for this generation only — not a
	// persistent override (compare api/aiFormInstructions.js / api/memoryTemplate.js).
	generate: (chapterId, body) =>
		client.post(`/ai/memory/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit (marks the document EDITED).
	save: (chapterId, content) =>
		client.put(`/ai/memory/chapters/${chapterId}`, { content }).then(r => r.data),
	// Idempotent: a missing document (404) is treated as already-cleared.
	remove: (chapterId) =>
		client.delete(`/ai/memory/chapters/${chapterId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),
	// Per-chapter staleness for a whole book, in linear book order.
	bookStatus: (bookId) =>
		client.get(`/books/${bookId}/memory-status`).then(r => r.data),
}
