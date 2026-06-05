import client from './client'

export const booksApi = {

	getByProject: async (projectId) => {
		const response = await client.get(`/projects/${projectId}/books`)
		return response.data
	},

	getById: async (id) => {
		const response = await client.get(`/books/${id}`)
		return response.data
	},

	create: async (projectId, data) => {
		const response = await client.post(`/projects/${projectId}/books`, data)
		return response.data
	},

	update: async (id, data) => {
		const response = await client.put(`/books/${id}`, data)
		return response.data
	},

	delete: async (id) => {
		await client.delete(`/books/${id}`)
	},

}