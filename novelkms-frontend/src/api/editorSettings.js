import client from './client'

// Cascading "document settings" bundle: PROJECT override -> USER default -> SYSTEM.
// Each GET returns a row { id, scope, projectId, definition, ... }; inspect
// `scope` to know whether a project is overriding ('PROJECT') or inheriting
// ('USER' / 'SYSTEM'). PUT/POST/DELETE bodies use { definition }.
export const editorSettingsApi = {
	// User default (spans all projects)
	getUser:   ()           => client.get('/editor-settings/global').then(r => r.data),
	putUser:   (definition) => client.put('/editor-settings/global', { definition }).then(r => r.data),
	resetUser: ()           => client.post('/editor-settings/global/reset').then(r => r.data),

	// Per-project override + resolved project settings (PROJECT -> USER -> SYSTEM)
	getProject:    (projectId)             => client.get(`/projects/${projectId}/editor-settings`).then(r => r.data),
	upsertProject: (projectId, definition) => client.put(`/projects/${projectId}/editor-settings`, { definition }).then(r => r.data),
	deleteProject: (projectId)             => client.delete(`/projects/${projectId}/editor-settings`).then(r => r.data),

	// Per-book override + resolved book settings (BOOK -> PROJECT -> USER -> SYSTEM)
	getBook:    (bookId)             => client.get(`/books/${bookId}/editor-settings`).then(r => r.data),
	upsertBook: (bookId, definition) => client.put(`/books/${bookId}/editor-settings`, { definition }).then(r => r.data),
	deleteBook: (bookId)             => client.delete(`/books/${bookId}/editor-settings`).then(r => r.data),
}
