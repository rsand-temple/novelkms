import { createContext, useContext } from 'react'

/**
 * DndStateContext — carries live cross-container drag state to leaf nodes
 * without prop-drilling through the full nav tree hierarchy.
 *
 * Provided by NavPanel (which owns the DndContext) and consumed by:
 *   - SceneItem       — renders the before/after drop indicator line
 *   - PartItem        — springs open while a chapter hovers its header
 *   - ChapterListZone — grows into a real drop target during a chapter drag
 *
 * Value shape:
 *   null | {
 *     activeType:  'part' | 'chapter' | 'scene',
 *     overId:      string | null,
 *     insertBefore: boolean,
 *     hoverPartId: string | null,   // part whose header a chapter is hovering
 *   }
 */
export const DndStateContext = createContext(null)

export const useDndState = () => useContext(DndStateContext)
