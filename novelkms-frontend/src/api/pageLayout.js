import client from './client'

// Scoped page layout (export/preview only). GET returns the resolved layout with
// `scope` (BOOK | PROJECT | SYSTEM) so the dialog can tell an own override from
// an inherited value. PUT body is the value fields; DELETE clears the override.
const path = (scope, id) =>
	scope === 'project' ? `/projects/${id}/page-layout` : `/books/${id}/page-layout`

export const pageLayoutApi = {
	get:    (scope, id)       => client.get(path(scope, id)).then(r => r.data),
	save:   (scope, id, body) => client.put(path(scope, id), body).then(r => r.data),
	remove: (scope, id)       => client.delete(path(scope, id)).then(r => r.data),
}
