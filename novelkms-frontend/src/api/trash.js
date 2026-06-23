import client from './client'

export const trashApi = {
	list:    ()        => client.get('/trash').then(r => r.data),
	restore: (batchId) => client.post(`/trash/${batchId}/restore`).then(r => r.data),
	purge:   (batchId) => client.delete(`/trash/${batchId}`).then(r => r.data),
	empty:   ()        => client.post('/trash/empty').then(r => r.data),
}
