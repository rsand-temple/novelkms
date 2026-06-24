import client from './client'

// AI review "form" instructions — the editable editorial persona/constraints.
// The constant "functional" JSON contract is not exposed and is not editable.
//
// Resolution is single-block selection (no inheritance, no concatenation):
//   book -> project -> user global -> system default.
//
// Each GET returns { scope, instructions, hasOwnOverride }:
//   scope          BOOK | PROJECT | USER | SYSTEM — where the returned text came from
//   instructions   the text to pre-populate the editor with
//   hasOwnOverride whether THIS scope holds its own override (enables "remove")
//
// PUT requires non-blank { instructions }. DELETE clears the override at this
// scope and returns the value it now falls back to (so the editor can refresh).
const path = (scope, id) => {
	switch (scope) {
		case 'global':  return '/ai-form-instructions/global'
		case 'project': return `/projects/${id}/ai-form-instructions`
		case 'book':    return `/books/${id}/ai-form-instructions`
		default: throw new Error(`Unknown form-instructions scope: ${scope}`)
	}
}

export const aiFormInstructionsApi = {
	get:    (scope, id)               => client.get(path(scope, id)).then(r => r.data),
	save:   (scope, id, instructions) => client.put(path(scope, id), { instructions }).then(r => r.data),
	remove: (scope, id)               => client.delete(path(scope, id)).then(r => r.data),
}
