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

	// ── Cover image ───────────────────────────────────────────────────────────

	/**
	 * Returns the URL for the book's cover image, suitable for use in <img src>.
	 * Append a cache-busting query param (e.g. ?t={book.updatedAt}) when the
	 * image may have changed since the last render.
	 */
	getCoverImageUrl: (id) => `/api/books/${id}/cover-image`,

	/**
	 * Reads a File object, base64-encodes it, and uploads it to the server.
	 * The server stores the raw bytes and MIME type; updated_at is bumped so
	 * cache-busted image URLs automatically invalidate in the browser.
	 */
	uploadCoverImage: async (id, file) => {
		return new Promise((resolve, reject) => {
			const reader = new FileReader()
			reader.onload = async () => {
				try {
					// FileReader gives "data:<mime>;base64,<data>" — strip the prefix.
					const base64 = reader.result.split(',')[1]
					await client.put(`/books/${id}/cover-image`, {
						imageData: base64,
						mimeType:  file.type,
					})
					resolve()
				} catch (err) {
					reject(err)
				}
			}
			reader.onerror = () => reject(new Error('Failed to read image file'))
			reader.readAsDataURL(file)
		})
	},

	deleteCoverImage: async (id) => {
		await client.delete(`/books/${id}/cover-image`)
	},

}
