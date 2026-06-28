import axios from 'axios'

const client = axios.create({
	baseURL: '/api',
	withCredentials: true,
	headers: { 'Content-Type': 'application/json' },
})

client.interceptors.response.use(
	(response) => response,
	(error) => {
		const status = error.response?.status
		const url = error.config?.url
		const payload = error.response?.data

		if (status === 402 && payload?.error === 'subscription_required') {
			window.dispatchEvent(new CustomEvent('novelkms:subscription-required', {
				detail: {
					status: payload.status ?? 'none',
					message: payload.message ?? 'An active NovelKMS subscription is required.',
					url,
				},
			}))
		} else if (status >= 500) {
			console.error(`[NovelKMS] Server error (${status}): ${url}`)
		} else if (!error.response) {
			console.error('[NovelKMS] Network error — is Server running?')
		}

		return Promise.reject(error)
	}
)

export default client