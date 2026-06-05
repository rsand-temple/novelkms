import axios from 'axios'

const client = axios.create({
	baseURL: '/api',
	headers: {
		'Content-Type': 'application/json',
	},
})

client.interceptors.response.use(
	(response) => response,
	(error) => {
		const status = error.response?.status
		const url = error.config?.url

		if (status >= 500) {
			console.error(`[NovelKMS] Server error (${status}): ${url}`)
		} else if (!error.response) {
			console.error('[NovelKMS] Network error — is Dropwizard running?')
		}

		return Promise.reject(error)
	}
)

export default client