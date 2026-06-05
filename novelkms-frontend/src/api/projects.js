import client from './client'

export const projectsApi = {

	getAll: async () => {
		const response = await client.get('/projects')
		return response.data
	},

	getById: async (id) => {
		const response = await client.get(`/projects/${id}`)
		return response.data
	},

	create: async (data) => {
		const response = await client.post('/projects', data)
		return response.data
	},

	update: async (id, data) => {
		const response = await client.put(`/projects/${id}`, data)
		return response.data
	},

	delete: async (id) => {
		await client.delete(`/projects/${id}`)
	},

}