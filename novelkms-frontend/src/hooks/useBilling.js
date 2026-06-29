import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingApi } from '../api/billing'

export const BILLING_KEYS = {
	all: ['billing'],
	status: ['billing', 'status'],
}

export function useBillingStatus(enabled = true) {
	return useQuery({
		queryKey: BILLING_KEYS.status,
		queryFn: billingApi.status,
		enabled,
	})
}

export function useCheckout() {
	return useMutation({
		mutationFn: () => billingApi.checkout(),
		onSuccess: (result) => {
			if (result?.url) {
				window.location.href = result.url
			}
		},
	})
}

export function useStartTrial() {
	const qc = useQueryClient()

	return useMutation({
		mutationFn: () => billingApi.startTrial(),
		onSuccess: (result) => {
			qc.setQueryData(BILLING_KEYS.status, result)

			// Starting a trial changes global entitlement state. Any query that
			// previously failed with 402 subscription_required needs another chance.
			// In particular, the nav tree's ['projects'] query may currently be
			// cached in an error state and should refetch to show the empty project
			// state for a new user.
			qc.invalidateQueries({
				predicate: (query) => query.queryKey[0] !== 'billing',
			})

			qc.invalidateQueries({ queryKey: BILLING_KEYS.status })
		},
	})
}

export function useBillingPortal() {
	const qc = useQueryClient()

	return useMutation({
		mutationFn: () => billingApi.portal(),
		onSuccess: (result) => {
			qc.invalidateQueries({ queryKey: BILLING_KEYS.status })

			if (result?.url) {
				window.location.href = result.url
			}
		},
	})
}