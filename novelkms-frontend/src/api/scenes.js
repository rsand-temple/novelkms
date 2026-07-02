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

	/**
	 * Saves scene content and its word count.
	 * wordCount must be supplied by the caller — the server stores it verbatim.
	 * In single-scene mode the caller uses TipTap's CharacterCount.words();
	 * in multi-scene mode the caller counts words from each HTML chunk directly.
	 */
	updateContent: async (id, content, wordCount = 0) => {
		const response = await client.put(`/scenes/${id}/content`, { content, wordCount })
		return response.data
	},

	/**
	 * Saves a codex entry's structured field values.
	 * structuredData is a JSON string (an object keyed by the category schema's
	 * field keys). Independent of the content save path.
	 */
	updateStructured: async (id, structuredData) => {
		const response = await client.put(`/scenes/${id}/structured-data`, { structuredData })
		return response.data
	},

	delete: async (id) => {
		await client.delete(`/scenes/${id}`)
	},

	/**
	 * Reorders scenes within a chapter.
	 * ids — complete ordered array of scene UUIDs for this chapter.
	 */
	reorderInChapter: async (chapterId, ids) => {
		await client.put(`/chapters/${chapterId}/scenes/reorder`, { ids })
	},

	moveScene: (id, body) => client.put(`/scenes/${id}/move`, body),
}