import client from './client'

export const authApi = {
  status: async () => (await client.get('/auth/status')).data,
  providers: async () => (await client.get('/auth/providers')).data,
  register: async (data) => (await client.post('/auth/register', data)).data,
  logout: async () => client.post('/auth/logout'),
  startUrl: (provider, returnTo = '/') => `/api/auth/${provider}/start?returnTo=${encodeURIComponent(returnTo)}`,
}
