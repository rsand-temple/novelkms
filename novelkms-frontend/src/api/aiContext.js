import client from './client'

/**
 * "Share Codex entries with the AI" endpoints. A pinned Codex entry is fed into
 * chapter/scene review prompts as reference context. Nothing is shared by
 * default; the author opts entries in per-entry or per-category.
 */
export const aiContextApi = {
    // Toggle one Codex entry (a scene). Returns the updated scene.
    setScenePinned:    (sceneId, pinned)   => client.put(`/scenes/${sceneId}/ai-context-pin`, { pinned }).then(r => r.data),

    // Toggle every entry under one Codex category (a chapter). Returns { updated }.
    setCategoryPinned: (chapterId, pinned) => client.put(`/chapters/${chapterId}/ai-context-pin`, { pinned }).then(r => r.data),

    // All entries in a codex with their pinned flag, for the Manage dialog.
    // Returns { pinnedCount, pinnedWords, entries: [...] }.
    listForCodex:      (codexId)           => client.get(`/codex/${codexId}/ai-context`).then(r => r.data),

    // Pinned entry count + total words for a book's review scope (book + project
    // codex), for the review rail. Returns { entryCount, wordCount }.
    bookSummary:       (bookId)            => client.get(`/books/${bookId}/ai-context-summary`).then(r => r.data),
}
