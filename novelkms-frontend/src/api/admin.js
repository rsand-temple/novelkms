import client from './client'

export const adminApi = {
	searchUsers: async (query = '', limit = 25) => {
		const params = { limit }
		if (query?.trim()) params.query = query.trim()

		return (await client.get('/admin/users', { params })).data
	},

	getUser: async (userId) => (
		await client.get(`/admin/users/${encodeURIComponent(userId)}`)
	).data,

	getBilling: async (userId) => (
		await client.get(`/admin/billing/users/${encodeURIComponent(userId)}`)
	).data,

	grantFamilyAccess: async (userId, body) => (
		await client.post(`/admin/billing/users/${encodeURIComponent(userId)}/family-access`, body)
	).data,

	getUserAudit: async (userId, limit = 25) => (
		await client.get(`/admin/audit/users/${encodeURIComponent(userId)}`, { params: { limit } })
	).data,

	getRecentAudit: async (limit = 25) => (
		await client.get('/admin/audit/recent', { params: { limit } })
	).data,
}