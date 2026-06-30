import client from './client'

/**
 * Artifacts: a per-project store for non-manuscript files (query letters,
 * research, cover-art sources). Files are download-only — no in-app rendering.
 *
 * Project-scoped paths are authorized by the tenant filter on the
 * projects/{id} segment; node/file paths carry only their own UUID and are
 * authorized inside the resource. The shared axios client mounts /api, so paths
 * here omit it — except downloadUrl, which is a real browser navigation target
 * and therefore includes /api.
 */
export const artifactsApi = {
	tree:  (projectId) => client.get(`/projects/${projectId}/artifacts`).then(r => r.data),
	usage: (projectId) => client.get(`/projects/${projectId}/artifacts/usage`).then(r => r.data),

	createFolder: (projectId, parentId, name) =>
		client.post(`/projects/${projectId}/artifacts/folders`, { parentId: parentId ?? null, name })
			.then(r => r.data),

	uploadFile: (projectId, parentId, file) => {
		const form = new FormData()
		if (parentId) form.append('parentId', parentId)
		form.append('file', file, file.name)
		return client
			.post(`/projects/${projectId}/artifacts/files`, form, {
				headers: { 'Content-Type': 'multipart/form-data' },
			})
			.then(r => r.data)
	},

	rename: (nodeId, name)     => client.put(`/artifacts/nodes/${nodeId}/rename`, { name }).then(r => r.data),
	move:   (nodeId, parentId) => client.put(`/artifacts/nodes/${nodeId}/move`, { parentId: parentId ?? null }).then(r => r.data),
	trash:  (nodeId)           => client.delete(`/artifacts/nodes/${nodeId}`).then(r => r.data),

	// Direct browser download target (session cookie is sent automatically).
	downloadUrl: (nodeId) => `/api/artifacts/files/${nodeId}/content`,
}
