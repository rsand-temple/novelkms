import client from './client'

export const partsApi = {
    getByBook:       (bookId)        => client.get(`/books/${bookId}/parts`).then(r => r.data),
    getById:         (id)            => client.get(`/parts/${id}`).then(r => r.data),
    // data may carry { anchorId, before } to insert relative to an existing
    // outline item — which may be another part OR a direct-book chapter.
    create:          (bookId, data)  => client.post(`/books/${bookId}/parts`, data).then(r => r.data),
    update:          (id, data)      => client.put(`/parts/${id}`, data).then(r => r.data),
    delete:          (id)            => client.delete(`/parts/${id}`).then(r => r.data),

    // reorderInBook is gone. Parts share one display_order sequence with the
    // book's direct chapters, so they can only be reordered together — see
    // outlineApi.reorder.

    getChapters:     (partId)        => client.get(`/parts/${partId}/chapters`).then(r => r.data),
    createChapter:   (partId, data)  => client.post(`/parts/${partId}/chapters`, data).then(r => r.data),
    reorderChapters: (partId, ids)   => client.put(`/parts/${partId}/chapters/reorder`, { ids }).then(r => r.data),
}
