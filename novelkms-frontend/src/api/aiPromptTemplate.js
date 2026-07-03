import client from './client'

// Author-editable AI generation prompt templates for chapter summary, book
// summary, and editorial. All three use the same single-block selection model
// as memory templates (no inheritance, no concatenation):
//   book -> project -> user global -> system default.
//
// templateType: 'chapterSummary' | 'bookSummary' | 'editorial'
//
// Each GET returns { scope, content, hasOwnOverride }:
//   scope          BOOK | PROJECT | USER | SYSTEM — where the returned text came from
//   content        the template text to pre-populate the editor with
//   hasOwnOverride whether THIS scope holds its own override (enables "Remove override")
//
// PUT requires non-blank { content }. DELETE clears the override at this scope
// and returns the value it now falls back to (so the editor can refresh).

const basePath = {
	chapterSummary: 'chapter-summary-template',
	bookSummary:    'book-summary-template',
	editorial:      'editorial-template',
}

const path = (templateType, scope, id) => {
	const base = basePath[templateType]
	if (!base) throw new Error(`Unknown AI prompt template type: ${templateType}`)
	switch (scope) {
		case 'global':  return `/${base}/global`
		case 'project': return `/projects/${id}/${base}`
		case 'book':    return `/books/${id}/${base}`
		default: throw new Error(`Unknown AI prompt template scope: ${scope}`)
	}
}

export const aiPromptTemplateApi = {
	get:    (type, scope, id)          => client.get(path(type, scope, id)).then(r => r.data),
	save:   (type, scope, id, content) => client.put(path(type, scope, id), { content }).then(r => r.data),
	remove: (type, scope, id)          => client.delete(path(type, scope, id)).then(r => r.data),
}
