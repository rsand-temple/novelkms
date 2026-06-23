import { createContext, useContext } from 'react'

export const ReviewContext = createContext(null)

export function useReview() {
	const value = useContext(ReviewContext)
	if (!value) throw new Error('useReview must be used inside ReviewProvider')
	return value
}
