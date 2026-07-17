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

	/**
	 * Extends (or starts) a user's local trial. The body must carry exactly one of
	 * `trialEndsAt` (ISO-8601 UTC instant) or `extendDays` (positive integer),
	 * plus an optional `reason` and `note`.
	 */
	extendTrial: async (userId, body) => (
		await client.post(`/admin/billing/users/${encodeURIComponent(userId)}/extend-trial`, body)
	).data,

	/**
	 * Permanently deletes a user and all their data. Irreversible.
	 * Returns nothing on success (204 No Content).
	 */
	hardDeleteUser: async (userId, reason) => {
		await client.post(`/admin/users/${encodeURIComponent(userId)}/hard-delete`, { reason })
	},

	getUserAudit: async (userId, limit = 25) => (
		await client.get(`/admin/audit/users/${encodeURIComponent(userId)}`, { params: { limit } })
	).data,

	getRecentAudit: async (limit = 25) => (
		await client.get('/admin/audit/recent', { params: { limit } })
	).data,

	getOverviewMetrics: async () => (
		await client.get('/admin/metrics/overview')
	).data,
}
