import { PART_KEYS }    from '../hooks/useParts'
import { CHAPTER_KEYS } from '../hooks/useChapters'
import { SCENE_KEYS }   from '../hooks/useScenes'

// ── Container ID builders ─────────────────────────────────────────────────────
// These string values are used as SortableContext `id` props and as
// useDroppable `id` props in ChapterListZone. They must be consistent
// across all components that read or write them.
//
// `outline` replaced the former `parts` and `chaptersBook` containers. A book's
// parts and its direct-book chapters now share ONE display_order sequence on the
// backend, so they must also share one SortableContext: they interleave, and a
// prologue that can never be dragged above Part I is exactly the bug the two
// separate containers used to enforce.
//
// Chapters INSIDE a part keep their own container — that sequence is genuinely
// separate, scoped to the part.

export const containerIds = {
	outline:      (bookId)    => `outline-${bookId}`,
	chaptersPart: (partId)    => `chapters-part-${partId}`,
	scenes:       (chapterId) => `scenes-${chapterId}`,
}

// ── Parse a container ID back to its type and owning ID ───────────────────────

export function parseContainerId(id) {
	if (!id) return null
	const s = String(id)
	if (s.startsWith('outline-'))        return { type: 'outline',       bookId:    s.slice(8)  }
	if (s.startsWith('chapters-part-'))  return { type: 'chapters-part', partId:    s.slice(14) }
	if (s.startsWith('scenes-'))         return { type: 'scenes',        chapterId: s.slice(7)  }
	return null
}

export function isContainerId(id) {
	return !!parseContainerId(id)
}

// ── Map a container ID to its TanStack Query cache key(s) ────────────────────
// Must match the queryKey used in each useQuery call in the hook files:
//   parts list      → PART_KEYS.byBook    → ['parts', 'byBook', bookId]
//   direct chapters → CHAPTER_KEYS.byBook → ['chapters', 'byBook', bookId]
//   part chapters   → PART_KEYS.chapters  → ['parts', partId, 'chapters']
//   scenes          → SCENE_KEYS.byChapter→ ['scenes', 'byChapter', chapterId]
//
// The outline is the one container backed by TWO caches, because it is the one
// container backed by two tables. Anything that reads or invalidates it has to
// touch both or it will see half the list.

// ── Read a container's current contents out of the query cache ───────────────
// Returns a flat, ordered array of { id, type, displayOrder } — the shape the
// drag handlers reason about, and one `type` field away from the typed payload
// the outline/move endpoints expect.
//
// For the outline this merges the two caches on displayOrder. Both lists arrive
// already sorted, so a straight merge is enough; the sort here is a cheap guard
// against a partially-invalidated cache handing back a stale ordering.

export function readContainerItems(queryClient, containerId) {
	const p = parseContainerId(containerId)
	if (!p) return []

	if (p.type === 'outline') {
		const parts    = queryClient.getQueryData(PART_KEYS.byBook(p.bookId))    ?? []
		const chapters = queryClient.getQueryData(CHAPTER_KEYS.byBook(p.bookId)) ?? []
		return [
			...parts.map(x    => ({ id: x.id, type: 'part',    displayOrder: x.displayOrder })),
			...chapters.map(x => ({ id: x.id, type: 'chapter', displayOrder: x.displayOrder })),
		].sort((a, b) => a.displayOrder - b.displayOrder)
	}

	if (p.type === 'chapters-part') {
		const chapters = queryClient.getQueryData(PART_KEYS.chapters(p.partId)) ?? []
		return chapters.map(x => ({ id: x.id, type: 'chapter', displayOrder: x.displayOrder }))
	}

	const scenes = queryClient.getQueryData(SCENE_KEYS.byChapter(p.chapterId)) ?? []
	return scenes.map(x => ({ id: x.id, type: 'scene', displayOrder: x.displayOrder }))
}

// ── Convert to the wire format the outline/move endpoints expect ─────────────
// The backend keys off the type to decide which table to UPDATE, so the case
// matters: OutlineItemType is a Java enum (PART / CHAPTER).

export function toOutlineRefs(items) {
	return items.map(i => ({
		type: i.type === 'part' ? 'PART' : 'CHAPTER',
		id:   i.id,
	}))
}
