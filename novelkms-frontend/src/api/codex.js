import client from './client'

export const codexApi = {
    getCategories:    ()                => client.get('/codex/categories').then(r => r.data),

    // Per-instance Codex Type (category chapter + its own active field set).
    // typeId is the category chapter id. Source of truth for a codex entry's
    // form schema; the global categories list is now only for seeding + AI
    // promotion mapping.
    getType:          (typeId)          => client.get(`/codex/types/${typeId}`).then(r => r.data),

    // ── Type-editor write path (E4 endpoints) ─────────────────────────────────
    // A "Type" is a category chapter row; typeId is that chapter id. Author
    // types are created with codex_category NULL. Field identity in the write
    // API is the immutable field key (server-generated on add), never the row
    // id — so rename/reorder never disturb stored entry values. Field write
    // bodies use { label, inputType, options, help, feedsAi }; reorder uses a
    // flat { fieldKeys } array.
    createType:       (codexId, data)   => client.post(`/codex/${codexId}/types`, data).then(r => r.data),
    updateType:       (typeId, data)    => client.put(`/codex/types/${typeId}`, data).then(r => r.data),
    addField:         (typeId, data)    => client.post(`/codex/types/${typeId}/fields`, data).then(r => r.data),
    updateField:      (typeId, key, d)  => client.put(`/codex/types/${typeId}/fields/${encodeURIComponent(key)}`, d).then(r => r.data),
    reorderFields:    (typeId, keys)    => client.put(`/codex/types/${typeId}/fields/order`, { fieldKeys: keys }).then(r => r.data),

    // ── Field soft-remove / restore / usage (E6 endpoints) ────────────────────
    // Removal is non-destructive: the field drops off the entry form but its
    // stored values survive in structured_data and can be restored. `usage`
    // returns every field (active and removed) with a `removed` flag and an
    // `entryCount` (how many entries hold a value for it) — it drives the
    // "Removed fields" area and the pre-removal warning.
    removeField:      (typeId, key)     => client.delete(`/codex/types/${typeId}/fields/${encodeURIComponent(key)}`).then(r => r.data),
    restoreField:     (typeId, key)     => client.post(`/codex/types/${typeId}/fields/${encodeURIComponent(key)}/restore`, {}).then(r => r.data),
    getFieldUsage:    (typeId)          => client.get(`/codex/types/${typeId}/fields/usage`).then(r => r.data),

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
