import api from './client'

export async function getAccount() {
	const { data } = await api.get('/account')
	return data
}

export async function updateAccount(payload) {
	const { data } = await api.put('/account', payload)
	return data
}

export async function logout() {
	await api.post('/auth/logout')
}