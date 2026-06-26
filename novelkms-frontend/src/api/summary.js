import client from './client'

// Chapter summaries and book summaries — a separate AI artifact family from
// memory documents (api/chapterMemory.js).
//
// A chapter summary is one human-readable paragraph for a chapter. A book
// summary is a whole-book synopsis (<= ~1000 words) generated entirely from the
// chapter summaries. There is at most one of each per parent; (re)generating
// overwrites it, and editing marks it EDITED.
export const summaryApi = {
	// ── Chapter summary ───────────────────────────────────────────────────────
	// The chapter's current summary, or null when none exists (404).
	getChapter: (chapterId) =>
		client.get(`/ai/summary/chapters/${chapterId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),
	// (Re)generates via the provider.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	// userGuidance is a one-time author note for this generation only.
	generateChapter: (chapterId, body) =>
		client.post(`/ai/summary/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit (marks the summary EDITED).
	saveChapter: (chapterId, content) =>
		client.put(`/ai/summary/chapters/${chapterId}`, { content }).then(r => r.data),
	// Idempotent: a missing summary (404) is treated as already-cleared.
	removeChapter: (chapterId) =>
		client.delete(`/ai/summary/chapters/${chapterId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),

	// ── Book-wide aggregated chapter summaries (read-only view + coverage) ─────
	// Every manuscript chapter in book order, each with its summary text and a
	// staleness state (OK | MISSING | STALE_CONTENT).
	bookChapterSummaries: (bookId) =>
		client.get(`/books/${bookId}/chapter-summaries`).then(r => r.data),

	// ── Book summary ──────────────────────────────────────────────────────────
	// The book's current summary, or null when none exists (404).
	getBook: (bookId) =>
		client.get(`/ai/summary/books/${bookId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),
	// (Re)generates the book summary from the chapter summaries.
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	generateBook: (bookId, body) =>
		client.post(`/ai/summary/books/${bookId}`, body ?? {}).then(r => r.data),
	// Saves an author edit (marks the summary EDITED; re-counts words).
	saveBook: (bookId, content) =>
		client.put(`/ai/summary/books/${bookId}`, { content }).then(r => r.data),
	// Idempotent: a missing summary (404) is treated as already-cleared.
	removeBook: (bookId) =>
		client.delete(`/ai/summary/books/${bookId}`)
			.then(r => r.data)
			.catch(err => { if (err?.response?.status === 404) return null; throw err }),
	// Book-summary card status + chapter-summary coverage counts.
	bookStatus: (bookId) =>
		client.get(`/books/${bookId}/book-summary-status`).then(r => r.data),
}
