import { preferencesApi } from '../api/preferences'

// Delete-confirmation preference.
//
// Previously stored in localStorage. It is now a per-user server preference
// (key 'skipDeleteConfirm'), but the nav/menu delete handlers read it
// synchronously at click time, so we keep a synchronous in-memory mirror here.
// App hydrates the mirror once on startup from the loaded preferences map via
// hydrateSkipDeleteConfirm(), and every write keeps the mirror in step.
//
// Pre-hydration default is `false` (safer — the confirmation dialog shows until
// we know otherwise).

const PREF_KEY = 'skipDeleteConfirm'

let cachedSkip = false

/** Sync the in-memory mirror. Accepts a boolean or the stored 'true'/'false' string. */
export function hydrateSkipDeleteConfirm(value) {
	cachedSkip = value === true || value === 'true'
}

/** Read synchronously (used by delete handlers at click time). */
export function shouldSkipDeleteConfirm() {
	return cachedSkip
}

/**
 * Persist the "don't ask again" choice. Matches the original contract: only ever
 * sets the flag to true (the DeleteConfirmDialog checkbox). Clearing the flag is
 * done from Settings → Other. Write-through updates the mirror immediately so the
 * very next delete respects it without waiting on the network.
 */
export function saveSkipDeleteConfirm(skip) {
	if (skip) {
		cachedSkip = true
		preferencesApi.set(PREF_KEY, 'true').catch(() => {})
	}
}
