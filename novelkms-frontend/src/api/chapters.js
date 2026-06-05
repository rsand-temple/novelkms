import client from './client'

export const chaptersApi = {

	getByBook: async (bookId) => {
		const response = await client.get(`/books/${bookId}/chapters`)
		return response.data
	},

	getById: async (id) => {
		const response = await client.get(`/chapters/${id}`)
		return response.data
	},

	create: async (bookId, data) => {
		const response = await client.post(`/books/${bookId}/chapters`, data)
		return response.data
	},

	update: async (id, data) => {
		const response = await client.put(`/chapters/${id}`, data)
		return response.data
	},

	delete: async (id) => {
		await client.delete(`/chapters/${id}`)
	},

}