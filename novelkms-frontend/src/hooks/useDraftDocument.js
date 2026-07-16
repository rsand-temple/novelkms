import { useQuery } from '@tanstack/react-query'
import { partsApi } from '../api/parts'
import { chaptersApi } from '../api/chapters'
import { scenesApi } from '../api/scenes'

async function withScenes(chapter) {
  const scenes = await scenesApi.getByChapter(chapter.id)
  return { ...chapter, scenes: scenes || [] }
}

async function loadPart(partId) {
  const [part, chapters] = await Promise.all([
    partsApi.getById(partId),
    partsApi.getChapters(partId),
  ])
  const hydrated = await Promise.all((chapters || []).map(withScenes))
  return {
    scope: 'part',
    groups: [{ part, chapters: hydrated }],
  }
}

async function loadBook(bookId) {
  const [parts, directChapters] = await Promise.all([
    partsApi.getByBook(bookId),
    chaptersApi.getByBook(bookId),
  ])

  const partGroups = await Promise.all((parts || []).map(async (part) => {
    const chapters = await partsApi.getChapters(part.id)
    return {
      displayOrder: part.displayOrder,
      part,
      chapters: await Promise.all((chapters || []).map(withScenes)),
    }
  }))

  const direct = await Promise.all((directChapters || []).map(withScenes))
  // Each direct-book chapter is its own group so it can slot in at its own
  // position — a prologue's group has to be able to land before every part
  // group, an epilogue's after every part group, and (once mid-book direct
  // chapters are supported) anything else between two of them.
  const directGroups = direct.map(chapter => ({
    displayOrder: chapter.displayOrder,
    part: null,
    chapters: [chapter],
  }))

  // Parts and direct-book chapters share one display_order sequence (V40) —
  // merging them here mirrors ExportService.bookOutline / EpubExportService's
  // mergeOutline. Before this merge, every part group was emitted first and
  // every direct chapter afterwards, which is why a prologue dragged above
  // Part I still rendered after Part I's last chapter in the full-book draft:
  // this function never read the shared ordering, only "which array it came
  // from."
  const groups = [...partGroups, ...directGroups].sort((a, b) => a.displayOrder - b.displayOrder)

  return {
    scope: 'book',
    groups,
  }
}

export function useDraftDocument({ bookId, partId, enabled }) {
  const scope = partId ? 'part' : 'book'
  const id = partId || bookId
  return useQuery({
    queryKey: ['draft-document', scope, id],
    queryFn: () => partId ? loadPart(partId) : loadBook(bookId),
    enabled: !!enabled && !!id,
    staleTime: 30_000,
  })
}

export function flattenDraftScenes(draft) {
  if (!draft?.groups) return []
  return draft.groups.flatMap(group =>
    group.chapters.flatMap(chapter =>
      (chapter.scenes || []).map(scene => ({
        ...scene,
        chapterId: chapter.id,
        chapterTitle: chapter.title,
        chapterNumber: chapter.chapterNumber,
        chapterSubtitle: chapter.subtitle,
        partId: group.part?.id ?? null,
        partTitle: group.part?.title ?? null,
        partNumber: group.part?.partNumber ?? null,
        partSubtitle: group.part?.subtitle ?? null,
      }))
    )
  )
}
