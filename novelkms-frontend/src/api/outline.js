import client from './client'

/**
 * The book outline — the single ordered sequence of a book's parts AND its
 * direct-book chapters, which share one display_order range on the backend.
 *
 * Reordering is one operation over both tables. It replaced the old
 * `/books/{id}/parts/reorder` and `/books/{id}/chapters/reorder` endpoints:
 * renumbering one type 0..n-1 in isolation now collides head-on with the other
 * type interleaved among it.
 *
 * `items` is an ordered array of typed refs — a bare list of UUIDs can't say
 * whether an entry is a part row or a chapter row, and the server has to know
 * which table to write:
 *
 *   [ { type: 'CHAPTER', id: '…' },   // Prologue
 *     { type: 'PART',    id: '…' },   // Part I
 *     { type: 'CHAPTER', id: '…' } ]  // Epilogue
 */
export const outlineApi = {
	get:     (bookId)        => client.get(`/books/${bookId}/outline`).then(r => r.data),
	reorder: (bookId, items) => client.put(`/books/${bookId}/outline/reorder`, { items }).then(r => r.data),
}
