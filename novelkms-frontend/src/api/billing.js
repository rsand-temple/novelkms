import client from './client'

export const billingApi = {
	status: () =>
		client.get('/billing/status').then(r => r.data),

	checkout: () =>
		client.post('/billing/checkout', {}).then(r => r.data),

	portal: () =>
		client.post('/billing/portal', {}).then(r => r.data),
}