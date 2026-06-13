import client from './client'

/**
 * Uploads a .docx file for import into a project.
 *
 * @param {string} projectId  - UUID of the target project
 * @param {string} bookTitle  - Optional title override; backend falls back to doc title or filename
 * @param {File}   file       - The .docx File object from an <input type="file">
 * @returns {Promise<ImportResult>}
 *
 * ImportResult shape:
 *   { bookId, bookTitle, partCount, chapterCount, sceneCount, wordCount, warnings: string[] }
 */
export const importDocx = (projectId, bookTitle, file) => {
  const form = new FormData()
  form.append('projectId', projectId)
  if (bookTitle && bookTitle.trim()) {
    form.append('bookTitle', bookTitle.trim())
  }
  form.append('file', file, file.name)

  return client
    .post('/import/docx', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((res) => res.data)
}

export const importApi = { importDocx }
