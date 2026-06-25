import client from './client'

// Memory-document template — the section structure the AI fills in when it
// generates a chapter memory document. Same single-block selection model as AI
// form instructions (no inheritance, no concatenation):
//   book -> project -> user global -> system default.
//
// Each GET returns { scope, content, hasOwnOverride }. PUT requires non-blank
// { content }. DELETE clears the override at this scope and returns the value it
// now falls back to.
const path = (scope, id) => {
	switch (scope) {
		case 'global':  return '/memory-template/global'
		case 'project': return `/projects/${id}/memory-template`
		case 'book':    return `/books/${id}/memory-template`
		default: throw new Error(`Unknown memory-template scope: ${scope}`)
	}
}

export const memoryTemplateApi = {
	get:    (scope, id)          => client.get(path(scope, id)).then(r => r.data),
	save:   (scope, id, content) => client.put(path(scope, id), { content }).then(r => r.data),
	remove: (scope, id)          => client.delete(path(scope, id)).then(r => r.data),
}
