import { useState, useCallback } from 'react'

// Project-level editor defaults.  Stored in localStorage keyed by projectId;
// easy to migrate to a backend project_settings table later.
export const PROJECT_SETTINGS_DEFAULTS = {
	fontFamily:             'Georgia, serif',
	fontSize:               '1.0625rem',
	lineHeight:             '1.9',
	firstLineIndent:        '1.5em',
	spacingAfter:           '0.9em',
	sceneBreakStyle:        '* * *',  // '* * *' | '#' | 'rule'
	sceneBreakSpacingAbove: '2em',
	sceneBreakSpacingBelow: '2em',
}

function storageKey(projectId) {
	return `novelkms:project:${projectId}:settings`
}

/**
 * Returns { settings, updateSettings }.
 * settings is always a fully-populated object (merged with defaults).
 * updateSettings(patch) deep-merges and persists immediately.
 */
export function useProjectSettings(projectId) {
	const [settings, setSettings] = useState(() => {
		if (!projectId) return { ...PROJECT_SETTINGS_DEFAULTS }
		try {
			const raw = localStorage.getItem(storageKey(projectId))
			return raw
				? { ...PROJECT_SETTINGS_DEFAULTS, ...JSON.parse(raw) }
				: { ...PROJECT_SETTINGS_DEFAULTS }
		} catch {
			return { ...PROJECT_SETTINGS_DEFAULTS }
		}
	})

	const updateSettings = useCallback((patch) => {
		setSettings(prev => {
			const next = { ...prev, ...patch }
			if (projectId) {
				try {
					localStorage.setItem(storageKey(projectId), JSON.stringify(next))
				} catch { /* quota exceeded — silently ignore */ }
			}
			return next
		})
	}, [projectId])

	return { settings, updateSettings }
}