const STORAGE_KEY = 'novelkms.skipDeleteConfirm'

export function shouldSkipDeleteConfirm() {
	return window.localStorage.getItem(STORAGE_KEY) === 'true'
}

export function saveSkipDeleteConfirm(skip) {
	if (skip) {
		window.localStorage.setItem(STORAGE_KEY, 'true')
	}
}