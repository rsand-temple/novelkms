import { useRef } from 'react'
import { InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import TheatersIcon from '@mui/icons-material/Theaters'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useUpdateScene }    from '../../hooks/useScenes'
import { containerIds }      from '../../dnd/dndUtils'
import { useDndState }       from '../../dnd/DndStateContext'
import { useNavContextMenu } from './NavContextMenuContext'
import { useSearch } from '../../search/SearchContext'

/**
 * SceneItem — nav tree leaf node for a Scene.
 *
 * Sortable within its chapter; supports cross-chapter drops with a before/after
 * indicator drawn via inset box-shadow (see DndStateContext).
 *
 * DnD listeners are disabled while the inline rename InputBase is active.
 *
 * handleClick sets bookId + partId + chapterId + sceneId atomically so
 * NavToolbar always has the correct sibling-list context for the ↑↓ arrows,
 * and so EditorPanel never falls back to a stale/unset prev.bookId. bookId
 * defaults to null for codex entries (which are not book-scoped); ChapterItem
 * passes its real bookId for manuscript scenes.
 */
export default function SceneItem({ scene, chapterId, partId, bookId = null, selection, setSelection, depth = 0 }) {
	const isSelected = selection.sceneId === scene.id
	const search = useSearch()
	const matchCount = search.counts.scene[scene.id] ?? 0

	// ── Drop indicator ────────────────────────────────────────────────────────
	const dragState      = useDndState()
	const isDropTarget   = dragState?.activeType === 'scene' && dragState.overId === String(scene.id)
	const showTopLine    = isDropTarget &&  dragState.insertBefore
	const showBottomLine = isDropTarget && !dragState.insertBefore

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(scene.id)
	// Uncontrolled input: defaultValue initialises when InputBase mounts.
	const renameInputRef = useRef(null)
	const { mutate: updateScene } = useUpdateScene()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		if (newTitle !== (scene.title ?? '')) {
			// useUpdateScene expects { id, data } — chapterId must be inside data
			// so onSuccess can invalidate SCENE_KEYS.byChapter(data.chapterId).
			updateScene({
				id:   scene.id,
				data: {
					chapterId,
					title:    newTitle,
					synopsis: scene.synopsis ?? '',
				},
			})
		}
		endRename()
	}

	const handleRenameKeyDown = (e) => {
		e.stopPropagation()
		if (e.key === 'Enter')  handleRenameCommit()
		if (e.key === 'Escape') endRename()
	}

	// ── Sortable ──────────────────────────────────────────────────────────────
	const {
		attributes,
		listeners,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: String(scene.id),
		data: {
			type:        'scene',
			title:       scene.title || 'Untitled Scene',
			containerId: containerIds.scenes(String(chapterId)),
			chapterId:   String(chapterId),
		},
	})

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleClick = () => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId:    partId ?? null,
			chapterId,
			sceneId:   scene.id,
		}))
	}

	const handleContextMenu = (e) => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId:    partId ?? null,
			chapterId,
			sceneId:   scene.id,
		}))
		openContextMenu(e, 'scene', {
			id:        scene.id,
			title:     scene.title,
			chapterId,
			partId:    partId ?? null,
			bookId,
			projectId: selection.projectId,
		})
	}

	return (
		<ListItemButton
			ref={setNodeRef}
			style={{
				transform:  CSS.Transform.toString(transform),
				transition,
				opacity:    isDragging ? 0.4 : 1,
			}}
			selected={isSelected}
			onClick={handleClick}
			onContextMenu={handleContextMenu}
			sx={{
				pl: 10 + depth * 3,
				...(matchCount > 0 && { bgcolor: 'warning.light' }),
				...(showTopLine    && { boxShadow: theme => `inset 0  2px 0 ${theme.palette.primary.main}` }),
				...(showBottomLine && { boxShadow: theme => `inset 0 -2px 0 ${theme.palette.primary.main}` }),
			}}
			{...attributes}
			// Gate DnD listeners off during rename to prevent drag initiation
			// while the user is typing in the InputBase.
			{...(isRenaming ? {} : listeners)}
		>
			<ListItemIcon sx={{ minWidth: 28 }}>
				<TheatersIcon fontSize="small" />
			</ListItemIcon>

			{isRenaming ? (
				<InputBase
					inputRef={renameInputRef}
					defaultValue={scene.title ?? ''}
					onBlur={handleRenameCommit}
					onKeyDown={handleRenameKeyDown}
					onClick={e => e.stopPropagation()}
					autoFocus
					fullWidth
					sx={{
						fontSize: '0.875rem',
						borderBottom: '1px solid',
						borderColor:  'primary.main',
						'& .MuiInputBase-input': { p: 0 },
					}}
				/>
			) : (
				<ListItemText
					primary={scene.title}
					secondary={matchCount > 0 ? `${matchCount} matches` : null}
					slotProps={{ primary: { variant: 'body2' } }}
				/>
			)}
		</ListItemButton>
	)
}
