import { PART_KEYS }    from '../hooks/useParts'
import { CHAPTER_KEYS } from '../hooks/useChapters'
import { SCENE_KEYS }   from '../hooks/useScenes'

// ── Container ID builders ─────────────────────────────────────────────────────
// These string values are used as SortableContext `id` props and as
// useDroppable `id` props in ChapterListZone. They must be consistent
// across all components that read or write them.

export const containerIds = {
	parts:        (bookId)    => `parts-${bookId}`,
	chaptersBook: (bookId)    => `chapters-book-${bookId}`,
	chaptersPart: (partId)    => `chapters-part-${partId}`,
	scenes:       (chapterId) => `scenes-${chapterId}`,
}

// ── Parse a container ID back to its type and owning ID ───────────────────────

export function parseContainerId(id) {
	if (!id) return null
	const s = String(id)
	if (s.startsWith('parts-'))          return { type: 'parts',         bookId:    s.slice(6)  }
	if (s.startsWith('chapters-book-'))  return { type: 'chapters-book', bookId:    s.slice(14) }
	if (s.startsWith('chapters-part-'))  return { type: 'chapters-part', partId:    s.slice(14) }
	if (s.startsWith('scenes-'))         return { type: 'scenes',        chapterId: s.slice(7)  }
	return null
}

export function isContainerId(id) {
	return !!parseContainerId(id)
}

// ── Map a container ID to the TanStack Query cache key ───────────────────────
// Must match the queryKey used in each useQuery call in the hook files:
//   parts list      → PART_KEYS.byBook    → ['parts', 'byBook', bookId]
//   direct chapters → CHAPTER_KEYS.byBook → ['chapters', 'byBook', bookId]
//   part chapters   → PART_KEYS.chapters  → ['parts', partId, 'chapters']
//   scenes          → SCENE_KEYS.byChapter→ ['scenes', 'byChapter', chapterId]

export function getQueryKey(containerId) {
	const p = parseContainerId(containerId)
	if (!p) return null
	switch (p.type) {
		case 'parts':         return PART_KEYS.byBook(p.bookId)
		case 'chapters-book': return CHAPTER_KEYS.byBook(p.bookId)
		case 'chapters-part': return PART_KEYS.chapters(p.partId)   // usePartChapters uses PART_KEYS.chapters
		case 'scenes':        return SCENE_KEYS.byChapter(p.chapterId)
		default:              return null
	}
}