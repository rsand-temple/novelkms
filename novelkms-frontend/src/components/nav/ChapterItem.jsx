import { useState, useRef } from 'react'
import { Collapse, InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ArticleIcon      from '@mui/icons-material/Article'
import { useSortable, SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useScenes }         from '../../hooks/useScenes'
import { useUpdateChapter }  from '../../hooks/useChapters'
import SceneItem             from './SceneItem'
import { containerIds }      from '../../dnd/dndUtils'
import { useNavContextMenu } from './NavContextMenuContext'
import { useSearch } from '../../search/SearchContext'

/**
 * ChapterItem — nav tree node for a Chapter.
 *
 * Sortable within its parent container (book-direct or part); can be dragged
 * across containers to reparent. DnD listeners are disabled while the inline
 * rename InputBase is active.
 *
 * ListItemText falls back to "Chapter N" when the stored title is blank,
 * matching the editor-heading behaviour (fixes a pre-existing display gap).
 */
export default function ChapterItem({ chapter, bookId, partId, selection, setSelection, depth = 0 }) {
	const [open, setOpen] = useState(false)
	const search = useSearch()
	const matchCount = search.counts.chapter[chapter.id] ?? 0
	const { data: scenes } = useScenes(open ? chapter.id : null)

	const isSelected = selection.chapterId === chapter.id && !selection.sceneId

	// Computed display title used both for ListItemText and the DnD overlay.
	const displayTitle = chapter.title?.trim() ? chapter.title : `Chapter ${chapter.chapterNumber}`

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(chapter.id)
	// Uncontrolled input: defaultValue initialises when InputBase mounts.
	const renameInputRef = useRef(null)
	const { mutate: updateChapter } = useUpdateChapter()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		// Allow saving an empty title — display will fall back to "Chapter N".
		if (newTitle !== (chapter.title ?? '')) {
			// useUpdateChapter expects { id, data }; data.bookId drives list invalidation.
			// resetsNumbering must be passed through unchanged — the backend requires
			// it on every update and has no way to know the prior value otherwise.
			updateChapter({
				id:   chapter.id,
				data: {
					bookId,
					title:    newTitle,
					subtitle: chapter.subtitle,
					notes:    chapter.notes,
					resetsNumbering: chapter.resetsNumbering ?? false,
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

	// ── DnD ───────────────────────────────────────────────────────────────────
	const sceneContainerId = containerIds.scenes(String(chapter.id))
	const sceneIds = (scenes ?? []).map(s => String(s.id))

	const {
		attributes,
		listeners,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: String(chapter.id),
		data: {
			type:        'chapter',
			title:       displayTitle,
			containerId: partId
				? containerIds.chaptersPart(String(partId))
				: containerIds.chaptersBook(String(bookId)),
			bookId: String(bookId),
			partId: partId ? String(partId) : null,
		},
	})

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection((prev) => ({
			...prev,
			partId:    chapter.partId ?? null,
			chapterId: chapter.id,
			sceneId:   null,
		}))
	}

	const handleContextMenu = (e) => {
		setSelection((prev) => ({
			...prev,
			partId:    chapter.partId ?? null,
			chapterId: chapter.id,
			sceneId:   null,
		}))
		openContextMenu(e, 'chapter', {
			id:        chapter.id,
			title:     displayTitle,
			partId:    chapter.partId ?? null,
			bookId,
			projectId: selection.projectId,
		})
	}

	return (
		<div
			ref={setNodeRef}
			style={{
				transform:  CSS.Transform.toString(transform),
				transition,
				opacity:    isDragging ? 0.4 : 1,
			}}
			{...attributes}
		>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
				sx={{ pl: 7 + depth * 3, ...(matchCount > 0 && { bgcolor: 'warning.light' }) }}
				// Gate DnD listeners off during rename to prevent drag initiation
				// while the user is typing in the InputBase.
				{...(isRenaming ? {} : listeners)}
			>
				<ListItemIcon
					sx={{ minWidth: 28, cursor: 'pointer' }}
					onClick={handleExpandToggle}
				>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<ArticleIcon fontSize="small" />
				</ListItemIcon>

				{isRenaming ? (
					<InputBase
						inputRef={renameInputRef}
						defaultValue={chapter.title ?? ''}
						// Placeholder shows the fallback when the stored title is blank,
						// so the user knows what the nav will display if they leave it empty.
						placeholder={`Chapter ${chapter.chapterNumber}`}
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
						primary={displayTitle}
						secondary={matchCount > 0 ? `${matchCount} matches` : null}
						slotProps={{ primary: { variant: 'body2' } }}
					/>
				)}
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<SortableContext
					id={sceneContainerId}
					items={sceneIds}
					strategy={verticalListSortingStrategy}
				>
					{(scenes ?? []).map((scene) => (
						<SceneItem
							key={scene.id}
							scene={scene}
							chapterId={chapter.id}
							partId={partId}
							selection={selection}
							setSelection={setSelection}
							depth={depth}
						/>
					))}
				</SortableContext>
			</Collapse>
		</div>
	)
}
