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
    return { part, chapters: await Promise.all((chapters || []).map(withScenes)) }
  }))

  const direct = await Promise.all((directChapters || []).map(withScenes))
  return {
    scope: 'book',
    groups: [
      ...partGroups,
      ...(direct.length ? [{ part: null, chapters: direct }] : []),
    ],
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
