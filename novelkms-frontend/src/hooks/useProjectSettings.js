import { useCallback } from 'react'
import {
	useUserEditorSettings,
	useProjectEditorSettings,
	useUpdateUserEditorSettings,
	useUpsertProjectEditorSettings,
} from './useEditorSettings'

// Factory defaults — kept as a fallback so the editor always renders a fully
// populated settings object, even before the resolved settings have loaded.
// These mirror the backend EditorSettingsDefaults (the SYSTEM row).
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

/**
 * Resolved document ("editor") settings for the current editing context.
 *
 * Returns { settings, updateSettings } — the same shape the previous
 * localStorage implementation exposed, so the editor, toolbar, and page
 * previews consume it unchanged.
 *
 * Settings are now read from the server through TanStack Query, keyed by
 * projectId. The query re-fetches automatically whenever projectId changes
 * (null -> A -> B), so the values can never go stale the way the old
 * useState(initializer) did — that stale initializer was the cause of settings
 * appearing to "reset" every session.
 *
 * Resolution is the SYSTEM -> USER -> PROJECT cascade:
 *   - With a project selected, settings resolve PROJECT override -> USER default.
 *   - updateSettings(patch) writes the merged definition to the PROJECT override
 *     (copy-on-write), so edits made from the editor's gear apply to this
 *     project. When no project is selected it writes the USER default instead.
 *   - The user default and "reset to default" controls live in the global
 *     Settings dialog (Document tab).
 */
export function useProjectSettings(projectId) {
	const hasProject = !!projectId

	const { data: projectRow } = useProjectEditorSettings(projectId, hasProject)
	const { data: userRow }    = useUserEditorSettings(!hasProject)

	const resolved = hasProject ? projectRow : userRow
	const settings = { ...PROJECT_SETTINGS_DEFAULTS, ...(resolved?.definition ?? {}) }

	const { mutate: upsertProject } = useUpsertProjectEditorSettings()
	const { mutate: updateUser }    = useUpdateUserEditorSettings()

	const updateSettings = useCallback((patch) => {
		// Build the full definition (the API stores the whole bundle) by merging
		// the patch onto the current resolved settings.
		const next = { ...PROJECT_SETTINGS_DEFAULTS, ...(resolved?.definition ?? {}), ...patch }
		if (hasProject) {
			upsertProject({ projectId, definition: next })
		} else {
			updateUser(next)
		}
	}, [hasProject, projectId, resolved, upsertProject, updateUser])

	return { settings, updateSettings }
}
