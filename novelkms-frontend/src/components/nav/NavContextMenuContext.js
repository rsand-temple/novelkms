import { createContext, useContext } from 'react'

export const NavContextMenuContext = createContext(null)

export function useNavContextMenu() {
	return useContext(NavContextMenuContext)
}
