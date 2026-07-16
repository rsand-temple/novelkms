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

	/**
	 * data may carry { partId, anchorId, before }.
	 *
	 * anchorId inserts the new chapter relative to an existing sibling instead of
	 * appending. For a direct-book chapter the anchor is an OUTLINE item and may
	 * be a part or another chapter — "insert before Part I" is how a prologue
	 * gets made.
	 */
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

	// reorderInBook is gone. A book's direct chapters share one display_order
	// sequence with its parts, so renumbering them alone would land them on top
	// of the parts interleaved among them — see outlineApi.reorder.

	/**
	 * Moves a chapter between containers.
	 *
	 * body: { partId, sourcePartId, sourceItems, targetItems }
	 *
	 * Both containers are named, and the item lists are TYPED ([{type,id}], not
	 * bare UUIDs): either end may be the book outline, which spans the part and
	 * chapter tables, and the server cannot tell a part row from a chapter row
	 * without being told. partId/sourcePartId are null when that end is the
	 * outline rather than a part.
	 */
	moveChapter: (id, body) => client.put(`/chapters/${id}/move`, body),
}
