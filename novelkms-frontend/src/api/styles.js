import client from './client'

// key is one of the fixed roster (see utils/styles.js / backend StyleDefaults).
export const stylesApi = {
	// Global (editable defaults spanning all projects)
	getAllGlobal: ()                    => client.get('/styles/global').then(r => r.data),
	getGlobal:    (key)                 => client.get(`/styles/global/${key}`).then(r => r.data),
	updateGlobal: (key, definition)     => client.put(`/styles/global/${key}`, { definition }).then(r => r.data),
	resetGlobal:  (key)                 => client.post(`/styles/global/${key}/reset`).then(r => r.data),

	// Project overrides + resolved project stylesheet (PROJECT -> GLOBAL)
	getProjectSheet: (projectId)                 => client.get(`/projects/${projectId}/styles`).then(r => r.data),
	getProjectStyle: (projectId, key)            => client.get(`/projects/${projectId}/styles/${key}`).then(r => r.data),
	upsertProject:   (projectId, key, definition) => client.put(`/projects/${projectId}/styles/${key}`, { definition }).then(r => r.data),
	deleteProject:   (projectId, key)            => client.delete(`/projects/${projectId}/styles/${key}`).then(r => r.data),

	// Book overrides + resolved book stylesheet (BOOK -> PROJECT -> GLOBAL)
	getBookSheet: (bookId)                 => client.get(`/books/${bookId}/styles`).then(r => r.data),
	getBookStyle: (bookId, key)            => client.get(`/books/${bookId}/styles/${key}`).then(r => r.data),
	upsertBook:   (bookId, key, definition) => client.put(`/books/${bookId}/styles/${key}`, { definition }).then(r => r.data),
	deleteBook:   (bookId, key)            => client.delete(`/books/${bookId}/styles/${key}`).then(r => r.data),
}
