import client from './client'

/**
 * API wrappers for the CodexEntryResource endpoints.
 *
 * All paths resolve under the shared /api base URL configured in the axios
 * client, so they omit the leading /api prefix.
 */
export const codexEntryApi = {
	/**
	 * Returns every manuscript chapter in the codex entry's scope (book or
	 * project), each carrying summary status fields used by the fill dialog:
	 *
	 *   { chapterId, seq, chapterNumber, title, bookId, bookTitle,
	 *     hasSummary, isStale, provider }
	 *
	 * bookTitle is non-null only for project-scoped codexes with more than one
	 * book; the dialog uses it to render book-group headings.
	 *
	 * @param {string} sceneId - UUID of the codex entry scene
	 */
	getCodexChapters: (sceneId) =>
		client.get(`/scenes/${sceneId}/codex-chapters`).then((r) => r.data),

	/**
	 * Streams a DOCX export of the given codex entry. The caller must pass
	 * responseType: 'blob' so axios captures the binary response correctly.
	 * Returns the raw Blob, which the hook converts into a browser download.
	 */
	exportDocx: (sceneId) =>
		client
			.get(`/scenes/${sceneId}/codex-docx`, { responseType: 'blob' })
			.then((r) => r.data),

	/**
	 * Uploads a DOCX file for a codex entry, parses it according to the
	 * round-trip contract, saves the result, and returns the updated Scene.
	 *
	 * @param {string} sceneId - UUID of the target codex entry scene
	 * @param {File}   file    - the .docx File from a file input or drop event
	 */
	importDocx: (sceneId, file) => {
		const form = new FormData()
		form.append('file', file)
		return client
			.post(`/scenes/${sceneId}/codex-docx`, form, {
				headers: { 'Content-Type': 'multipart/form-data' },
			})
			.then((r) => r.data)
	},

	/**
	 * Requests AI-generated field values and body text for a codex entry.
	 * Returns a FillResponse { fields, body, promptVersion } without saving —
	 * the caller merges the result into the form.
	 *
	 * @param {string}        sceneId            - UUID of the codex entry scene
	 * @param {string|null}   credentialId       - specific AI credential UUID, or null for default
	 * @param {string|null}   userGuidance       - optional one-time author note
	 * @param {string[]|null} selectedChapterIds - chapter UUIDs to include as context;
	 *                                             null/empty = use all available summaries
	 */
	fillWithAi: (sceneId, {
		credentialId       = null,
		userGuidance       = null,
		selectedChapterIds = null,
	} = {}) =>
		client
			.post(`/scenes/${sceneId}/codex-fill`, {
				credentialId,
				userGuidance,
				selectedChapterIds,
			})
			.then((r) => r.data),
}
