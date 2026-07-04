import client from './client'

const dataOrNull = (response) => response.status === 204 ? null : response.data
const providerParams = (provider) => provider ? { params: { provider } } : undefined

// Chapter summaries and book summaries — a separate AI artifact family from
// memory documents (api/chapterMemory.js).
//
// A chapter summary is one human-readable paragraph for a chapter. A book
// summary is a whole-book synopsis (<= ~1000 words) generated entirely from the
// chapter summaries. Since the provider-variants work there is at most one of
// each per (parent, provider). Passing `provider` targets that provider's
// variant exactly; omitting it returns the PREFERRED variant (default provider's,
// else most-recently-updated). (Re)generating overwrites the generating
// provider's document; editing marks it EDITED.
export const summaryApi = {
	// ── Chapter summary ───────────────────────────────────────────────────────
	// The chapter's summary for `provider` exactly, or (with no provider) the
	// preferred variant; null when none exists (204).
	getChapter: (chapterId, provider) =>
		client.get(`/ai/summary/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),
	// Every provider variant of the chapter's summary (newest first).
	chapterVariants: (chapterId) =>
		client.get(`/ai/summary/chapters/${chapterId}/variants`).then(r => r.data),
	// (Re)generates via the provider (variant determined by the credential's provider).
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	generateChapter: (chapterId, body) =>
		client.post(`/ai/summary/chapters/${chapterId}`, body ?? {}).then(r => r.data),
	// Saves an author edit to the given provider's variant (marks it EDITED).
	saveChapter: (chapterId, content, provider) =>
		client.put(`/ai/summary/chapters/${chapterId}`, { content, provider: provider ?? null }).then(r => r.data),
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	removeChapter: (chapterId, provider) =>
		client.delete(`/ai/summary/chapters/${chapterId}`, providerParams(provider))
			.then(dataOrNull),

	// ── Book-wide aggregated chapter summaries (read-only view + coverage) ─────
	// Every manuscript chapter in book order, each with its summary text and a
	// staleness state (OK | MISSING | STALE_CONTENT), resolved for the given
	// provider (or the user's default provider when omitted).
	bookChapterSummaries: (bookId, provider) =>
		client.get(`/books/${bookId}/chapter-summaries`, providerParams(provider)).then(r => r.data),

	// ── Book summary ──────────────────────────────────────────────────────────
	// The book's summary for `provider` exactly, or (with no provider) the
	// preferred variant; null when none exists (204).
	getBook: (bookId, provider) =>
		client.get(`/ai/summary/books/${bookId}`, providerParams(provider))
			.then(dataOrNull),
	// Every provider variant of the book's summary (newest first).
	bookVariants: (bookId) =>
		client.get(`/ai/summary/books/${bookId}/variants`).then(r => r.data),
	// (Re)generates the book summary from the chapter summaries (variant determined
	// by the credential's provider).
	// body: { credentialId?: uuid|null, model?: string|null, userGuidance?: string|null }
	generateBook: (bookId, body) =>
		client.post(`/ai/summary/books/${bookId}`, body ?? {}).then(r => r.data),
	// Saves an author edit to the given provider's variant (marks it EDITED; re-counts words).
	saveBook: (bookId, content, provider) =>
		client.put(`/ai/summary/books/${bookId}`, { content, provider: provider ?? null }).then(r => r.data),
	// Idempotent: 200 = existed and was cleared; 204 = already absent.
	removeBook: (bookId, provider) =>
		client.delete(`/ai/summary/books/${bookId}`, providerParams(provider))
			.then(dataOrNull),
	// Book-summary card status + chapter-summary coverage counts, for the given
	// provider (or the user's default provider when omitted).
	bookStatus: (bookId, provider) =>
		client.get(`/books/${bookId}/book-summary-status`, providerParams(provider)).then(r => r.data),
}
