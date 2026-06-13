import { useMutation, useQueryClient } from '@tanstack/react-query'
import { importApi } from '../api/import'
import { BOOK_KEYS } from './useBooks'

/**
 * Mutation hook for importing a .docx file into a project.
 *
 * Usage:
 *   const { mutate: importDocx, isPending } = useImportDocx()
 *   importDocx({ projectId, bookTitle, file }, {
 *     onSuccess: (result) => { ... result.bookId, result.bookTitle, etc. }
 *   })
 *
 * On success, invalidates the book list for the target project so the nav
 * tree refreshes automatically.
 */
export const useImportDocx = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ projectId, bookTitle, file }) =>
      importApi.importDocx(projectId, bookTitle, file),

    onSuccess: (result, { projectId }) => {
      // Refresh the book list so the imported book appears in the nav tree
      queryClient.invalidateQueries({ queryKey: BOOK_KEYS.byProject(projectId) })
    },
  })
}
