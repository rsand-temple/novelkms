import api from './client'

export const kmsArchiveApi = {
	projectArchiveUrl: (projectId) => `/api/export/projects/${projectId}/kms`,

	downloadProject(projectId) {
		const a = document.createElement('a')
		a.href = this.projectArchiveUrl(projectId)
		a.rel = 'noopener'
		document.body.appendChild(a)
		a.click()
		document.body.removeChild(a)
	},

	async validate(file) {
		const form = new FormData()
		form.append('file', file)
		const { data } = await api.post('/import/kms/validate', form, {
			headers: { 'Content-Type': 'multipart/form-data' },
		})
		return data
	},

	async importAsNewProjects(file) {
		const form = new FormData()
		form.append('file', file)
		const { data } = await api.post('/import/kms', form, {
			headers: { 'Content-Type': 'multipart/form-data' },
		})
		return data
	},
}
