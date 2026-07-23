import client from './client'

/**
 * The Scratchpad is a per-book holding pen for scenes that are not part of the
 * manuscript — parked drafts, cut scenes, alternate versions. It is stored as a
 * chapter row whose book_id is NULL and whose scratchpad_book_id names the book,
 * which is what keeps it out of every book-rooted read on the server: numbering,
 * outline order, word counts, exports, search, and every AI workflow.
 *
 * There is no create call and no delete call. A book's Scratchpad is a fixture,
 * not something the author adds, so GET is get-or-create: the row is written the
 * first time it is asked for. Scenes inside it are created, renamed, reordered,
 * moved, and trashed through the ordinary scene endpoints — the Scratchpad needs
 * none of its own.
 */
export const scratchpadApi = {

	/** Get-or-create the book's Scratchpad chapter. */
	getByBook: async (bookId) => {
		const response = await client.get(`/books/${bookId}/scratchpad`)
		return response.data
	},
}
