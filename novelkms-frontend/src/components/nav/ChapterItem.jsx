import { useState } from 'react'
import { Collapse, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ArticleIcon      from '@mui/icons-material/Article'
import { useSortable, SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useScenes } from '../../hooks/useScenes'
import SceneItem from './SceneItem'
import { containerIds } from '../../dnd/dndUtils'

/**
 * ChapterItem — nav tree node for a Chapter.
 *
 * The chapter row is sortable within its parent container (either a part or
 * the book-direct list) and can be dragged across containers to reparent it.
 *
 * bookId + partId are required to build the correct container ID so that
 * NavPanel's drag handler can look up the right query-cache entry.
 *
 * partId is also forwarded to each SceneItem so that clicking a scene sets
 * the full { partId, chapterId, sceneId } context in one step — necessary
 * for NavToolbar's ↑↓ arrows to resolve the correct sibling list.
 */
export default function ChapterItem({ chapter, bookId, partId, selection, setSelection, depth = 0 }) {
	const [open, setOpen] = useState(false)
	const { data: scenes } = useScenes(open ? chapter.id : null)

	const isSelected = selection.chapterId === chapter.id && !selection.sceneId

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
			title:	chapter.title?.trim() ? chapter.title : `Chapter ${chapter.chapterNumber}`,
			containerId: partId
				? containerIds.chaptersPart(String(partId))
				: containerIds.chaptersBook(String(bookId)),
			bookId:      String(bookId),
			partId:      partId ? String(partId) : null,
		},
	})

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
				sx={{ pl: 7 + depth * 3 }}
				{...listeners}
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
				<ListItemText
					primary={chapter.title}
					slotProps={{ primary: { variant: 'body2' } }}
				/>
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
