import { Node, mergeAttributes } from '@tiptap/core'

/**
 * SceneBreak — a block-level atomic node that renders as <hr> and represents
 * the boundary between two scenes in the chapter editor.
 *
 * Each SceneBreak carries a `sceneId` attribute (stored as `data-scene-after`)
 * that identifies the scene whose content follows it.  This is what ties the
 * visual divider to a database record and a nav-tree entry.
 *
 * SceneBreak replaces StarterKit's HorizontalRule entirely.
 * StarterKit must be configured with `horizontalRule: false`.
 *
 * Backward compatibility: plain `<hr>` elements (no `data-scene-after`) are
 * also parsed as SceneBreak nodes with `sceneId: null`.  They will render
 * visually but will not correspond to a nav-tree entry until the chapter is
 * saved and the HR is replaced by a proper split via the scene break button.
 */
export const SceneBreak = Node.create({
	name: 'sceneBreak',
	group: 'block',
	atom: true, // Cannot be entered or split by the cursor
	selectable: false,
	draggable: false,

	addAttributes() {
		return {
			locked: {
				default: false,
				parseHTML: el => el.getAttribute('data-locked') === 'true',
				renderHTML: attrs => attrs.locked ? { 'data-locked': 'true' } : {},
			},
			sceneId: {
				default: null,
				parseHTML: el => el.getAttribute('data-scene-after') || null,
				renderHTML: attrs =>
					attrs.sceneId ? { 'data-scene-after': attrs.sceneId } : {},
			},
		}
	},

	parseHTML() {
		return [
			{
				tag: 'hr',
				getAttrs: el => ({
					sceneId: el.getAttribute('data-scene-after') || null,
					locked: el.getAttribute('data-locked') === 'true',
				}),
			},
		]
	},

	renderHTML({ HTMLAttributes }) {
		return ['hr', mergeAttributes(HTMLAttributes)]
	},

	addCommands() {
		return {
			/**
			 * Insert a SceneBreak with the given attrs.
			 * Normally called from EditorPanel after a scene is created in the DB:
			 *   editor.chain().focus().setSceneBreak({ sceneId: newScene.id }).run()
			 */
			setSceneBreak:
				(attrs) =>
				({ commands }) =>
					commands.insertContent({ type: this.name, attrs: attrs ?? {} }),
		}
	},
})
