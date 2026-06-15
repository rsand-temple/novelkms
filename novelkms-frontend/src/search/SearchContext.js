import { createContext, useContext } from 'react'

export const SearchContext = createContext(null)

export function useSearch() {
	const value = useContext(SearchContext)
	if (!value) throw new Error('useSearch must be used inside SearchProvider')
	return value
}
