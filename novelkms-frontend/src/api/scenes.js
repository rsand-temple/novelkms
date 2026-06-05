import client from './client'

export const scenesApi = {

	getByChapter: async (chapterId) => {
		const response = await client.get(`/chapters/${chapterId}/scenes`)
		return response.data
	},

	getById: async (id) => {
		const response = await client.get(`/scenes/${id}`)
		return response.data
	},

	create: async (chapterId, data) => {
		const response = await client.post(`/chapters/${chapterId}/scenes`, data)
		return response.data
	},

	update: async (id, data) => {
		const response = await client.put(`/scenes/${id}`, data)
		return response.data
	},

	updateContent: async (id, content) => {
		const response = await client.put(`/scenes/${id}/content`, { content })
		return response.data
	},

	delete: async (id) => {
		await client.delete(`/scenes/${id}`)
	},

}