import { useState, useRef } from 'react'
import { Collapse, InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import BookmarksIcon from '@mui/icons-material/Bookmarks'
import { useSortable, SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { usePartChapters, useUpdatePart } from '../../hooks/useParts'
import ChapterItem from './ChapterItem'
import ChapterListZone from './ChapterListZone'
import { containerIds } from '../../dnd/dndUtils'
import { useDndState } from '../../dnd/DndStateContext'
import { useNavContextMenu } from './NavContextMenuContext'
import { useSearch } from '../../search/SearchContext'

// ── Roman numeral helper ─────────────────────────────────────────────────────
// Converts a positive integer to a Roman numeral string (I, II, III, …).
// Used when a part has no custom title so the nav tree shows "Part I/II/III".
const toRoman = (n) => {
	if (!n || n < 1) return '?'
	const vals = [1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1]
	const syms = ['M', 'CM', 'D', 'CD', 'C', 'XC', 'L', 'XL', 'X', 'IX', 'V', 'IV', 'I']
	let r = ''
	for (let i = 0; i < vals.length; i++) {
		while (n >= vals[i]) { r += syms[i]; n -= vals[i] }
	}
	return r
}

/**
 * PartItem — nav tree node for a Part.
 *
 * When part.title is blank the row displays "Part I / II / III …" using
 * part.partNumber (computed by the backend via ROW_NUMBER). The title input
 * in PropertiesPanel remains empty in that case, letting the author give the
 * part a real name later without the auto-label getting in the way.
 *
 * The part row is sortable within the BOOK OUTLINE — the single sequence it
 * shares with the book's direct chapters — so dragging a part past a prologue,
 * or a prologue past a part, is one ordinary reorder.
 *
 * That makes the part header a POSITIONAL target, not a nesting target: dropping
 * a chapter on it places the chapter before/after the part. To nest a chapter
 * INSIDE the part you drop it into the part's own chapter zone, which is a
 * separate container. So that stays reachable mid-drag, the part springs open
 * while a chapter hovers over it (see hoverPartId in NavPanel's drag state).
 *
 * DnD listeners are disabled while the inline rename InputBase is active.
 */
export default function PartItem({ part, bookId, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const search = useSearch()
	const matchCount = search.counts.part[part.id] ?? 0

	// Spring-loaded open: while a chapter is dragged over this part's header, the
	// part opens so its chapter zone — the only way to nest into it now that the
	// header is a positional target — comes within reach without dropping first.
	const dragState = useDndState()
	const springOpen = dragState?.hoverPartId === String(part.id)
	const expanded = open || springOpen

	// Still lazy: a collapsed part fetches nothing. Springing open enables the
	// query, so the zone fills in as it expands.
	const { data: chapters } = usePartChapters(expanded ? part.id : null)

	const isSelected = selection.partId === part.id && !selection.chapterId

	// Display title: custom title if set, otherwise "Part I/II/III"
	const displayTitle = part.title?.trim()
		? part.title
		: `Part ${toRoman(part.partNumber)}`

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(part.id)
	const renameInputRef = useRef(null)
	const { mutate: updatePart } = useUpdatePart()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		if (newTitle !== (part.title ?? '')) {
			updatePart({
				id: part.id,
				data: {
					title: newTitle,
					subtitle: part.subtitle,
					notes: part.notes,
				},
			})
		}
		endRename()
	}

	const handleRenameKeyDown = (e) => {
		e.stopPropagation()
		if (e.key === 'Enter') handleRenameCommit()
		if (e.key === 'Escape') endRename()
	}

	// ── DnD ───────────────────────────────────────────────────────────────────
	const chapterContainerId = containerIds.chaptersPart(String(part.id))
	const chapterIds = (chapters ?? []).map(c => String(c.id))

	const {
		attributes,
		listeners,
		setNodeRef,
		transform,
		transition,
		isDragging,
	} = useSortable({
		id: String(part.id),
		data: {
			type: 'part',
			title: displayTitle,
			containerId: containerIds.outline(String(bookId)),
			bookId: String(bookId),
		},
	})

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		// Always set bookId explicitly — the user may have expanded the book node
		// via the arrow without clicking the row, leaving prev.bookId unset.
		setSelection((prev) => ({
			...prev,
			bookId,
			partId: part.id,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
		}))
	}

	const handleContextMenu = (e) => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId: part.id,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
		}))
		openContextMenu(e, 'part', {
			id: part.id,
			title: part.title,
			bookId,
			projectId: selection.projectId,
		})
	}

	return (
		<div
			ref={setNodeRef}
			style={{
				transform: CSS.Transform.toString(transform),
				transition,
				opacity: isDragging ? 0.4 : 1,
			}}
			{...attributes}
		>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
				sx={{ pl: 7, ...(matchCount > 0 && { bgcolor: 'warning.light' }) }}
				{...(isRenaming ? {} : listeners)}
			>
				<ListItemIcon
					sx={{ minWidth: 28, cursor: 'pointer' }}
					onClick={handleExpandToggle}
				>
					{expanded ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<BookmarksIcon fontSize="small" />
				</ListItemIcon>

				{isRenaming ? (
					<InputBase
						inputRef={renameInputRef}
						defaultValue={part.title ?? ''}
						onBlur={handleRenameCommit}
						onKeyDown={handleRenameKeyDown}
						onClick={e => e.stopPropagation()}
						autoFocus
						fullWidth
						sx={{
							fontSize: '0.875rem',
							fontStyle: 'italic',
							borderBottom: '1px solid',
							borderColor: 'primary.main',
							'& .MuiInputBase-input': { p: 0 },
						}}
					/>
				) : (
					<ListItemText
						primary={displayTitle}
						secondary={matchCount > 0 ? `${matchCount} matches` : null}
						slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic' } } }}
					/>
				)}
			</ListItemButton>

			<Collapse in={expanded} unmountOnExit>
				<SortableContext
					id={chapterContainerId}
					items={chapterIds}
					strategy={verticalListSortingStrategy}
				>
					<ChapterListZone containerId={chapterContainerId}>
						{(chapters ?? []).map((chapter) => (
							<ChapterItem
								key={chapter.id}
								chapter={chapter}
								bookId={bookId}
								partId={part.id}
								depth={1}
								selection={selection}
								setSelection={setSelection}
							/>
						))}
					</ChapterListZone>
				</SortableContext>
			</Collapse>
		</div>
	)
}