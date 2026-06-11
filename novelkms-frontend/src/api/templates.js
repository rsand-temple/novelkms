import client from './client'

// type is 'cover' | 'part' (the backend normalizes case-insensitively).
export const templatesApi = {
	// Global (editable defaults spanning all projects)
	getGlobal:    (type)              => client.get(`/templates/global/${type}`).then(r => r.data),
	updateGlobal: (type, content)     => client.put(`/templates/global/${type}`, { content }).then(r => r.data),
	resetGlobal:  (type)              => client.post(`/templates/global/${type}/reset`).then(r => r.data),

	// Per-book (resolved value + override management)
	getForBook:   (bookId, type)          => client.get(`/books/${bookId}/templates/${type}`).then(r => r.data),
	upsertBook:   (bookId, type, content) => client.put(`/books/${bookId}/templates/${type}`, { content }).then(r => r.data),
	deleteBook:   (bookId, type)          => client.delete(`/books/${bookId}/templates/${type}`).then(r => r.data),
}
