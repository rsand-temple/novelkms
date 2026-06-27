import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as accountApi from '../api/account'

export const ACCOUNT_KEYS = {
	account: ['account'],
}

export function useAccount() {
	return useQuery({
		queryKey: ACCOUNT_KEYS.account,
		queryFn: accountApi.getAccount,
	})
}

export function useUpdateAccount() {
	const queryClient = useQueryClient()

	return useMutation({
		mutationFn: accountApi.updateAccount,
		onSuccess: (data) => {
			queryClient.setQueryData(ACCOUNT_KEYS.account, data)
		},
	})
}

export function useLogout() {
	const queryClient = useQueryClient()

	return useMutation({
		mutationFn: accountApi.logout,
		onSuccess: () => {
			queryClient.clear()
		},
	})
}