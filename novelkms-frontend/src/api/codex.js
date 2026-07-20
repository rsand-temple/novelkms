import client from './client'

export const codexApi = {
    getCategories:    ()                => client.get('/codex/categories').then(r => r.data),

    // Per-instance Codex Type (category chapter + its own active field set).
    // typeId is the category chapter id. Source of truth for a codex entry's
    // form schema; the global categories list is now only for seeding + AI
    // promotion mapping.
    getType:          (typeId)          => client.get(`/codex/types/${typeId}`).then(r => r.data),

    getByProject:     (projectId)       => client.get(`/projects/${projectId}/codex`).then(r => r.data),
    createForProject: (projectId, data) => client.post(`/projects/${projectId}/codex`, data ?? {}).then(r => r.data),

    getByBook:        (bookId)          => client.get(`/books/${bookId}/codex`).then(r => r.data),
    createForBook:    (bookId, data)    => client.post(`/books/${bookId}/codex`, data ?? {}).then(r => r.data),

    getById:          (id)              => client.get(`/codex/${id}`).then(r => r.data),
    delete:           (id)              => client.delete(`/codex/${id}`).then(r => r.data),

    getChapters:      (codexId)         => client.get(`/codex/${codexId}/chapters`).then(r => r.data),
    createChapter:    (codexId, data)   => client.post(`/codex/${codexId}/chapters`, data).then(r => r.data),
    reorderChapters:  (codexId, ids)    => client.put(`/codex/${codexId}/chapters/reorder`, { ids }).then(r => r.data),
}
