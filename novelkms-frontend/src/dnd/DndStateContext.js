import { createContext, useContext } from 'react'

/**
 * DndStateContext — carries live cross-container drag state to leaf nodes
 * without prop-drilling through the full nav tree hierarchy.
 *
 * Provided by NavPanel (which owns the DndContext) and consumed by SceneItem
 * to render the before/after drop indicator line.
 *
 * Value shape: null | { activeType: string, overId: string, insertBefore: boolean }
 */
export const DndStateContext = createContext(null)

export const useDndState = () => useContext(DndStateContext)
