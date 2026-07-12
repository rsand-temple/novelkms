/**
 * Export API helpers.
 *
 * Export endpoints return binary files via HTTP GET, so there is no Axios call —
 * we just construct the URL and trigger a standard browser download by clicking
 * a temporary <a> element.
 */
export const exportApi = {

	bookDocxUrl:    (bookId)    => `/api/export/books/${bookId}/docx`,
	bookEpubUrl:    (bookId)    => `/api/export/books/${bookId}/epub`,
	bookPdfUrl:     (bookId)    => `/api/export/books/${bookId}/pdf`,
	partDocxUrl:    (partId)    => `/api/export/parts/${partId}/docx`,
	partPdfUrl:     (partId)    => `/api/export/parts/${partId}/pdf`,
	chapterDocxUrl: (chapterId) => `/api/export/chapters/${chapterId}/docx`,
	chapterPdfUrl:  (chapterId) => `/api/export/chapters/${chapterId}/pdf`,
	sceneDocxUrl:   (sceneId)   => `/api/export/scenes/${sceneId}/docx`,
	scenePdfUrl:    (sceneId)   => `/api/export/scenes/${sceneId}/pdf`,

	/**
	 * Triggers a file download in the browser without any page navigation.
	 * Works for same-origin URLs that return Content-Disposition: attachment.
	 */
	download(url) {
		const a = document.createElement('a')
		a.href = url
		a.rel  = 'noopener'
		document.body.appendChild(a)
		a.click()
		document.body.removeChild(a)
	},
}
