import { useState, useRef } from 'react'
import { Box, Collapse, InputBase, ListItemButton, ListItemText, ListItemIcon } from '@mui/material'
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderIcon       from '@mui/icons-material/Folder'
import { useBooks }          from '../../hooks/useBooks'
import { useUpdateProject }  from '../../hooks/useProjects'
import BookItem              from './BookItem'
import { useNavContextMenu } from './NavContextMenu'

export default function ProjectItem({ project, selection, setSelection }) {
	const [open, setOpen] = useState(false)
	const { data: books } = useBooks(open ? project.id : null)

	const isSelected = selection.projectId === project.id && !selection.bookId

	// ── Context menu & rename ─────────────────────────────────────────────────
	const { openContextMenu, renamingId, endRename } = useNavContextMenu()
	const isRenaming = renamingId === String(project.id)
	// Uncontrolled input: defaultValue initialises when InputBase mounts
	// (i.e. when isRenaming first becomes true). Read via ref at commit time.
	const renameInputRef = useRef(null)
	const { mutate: updateProject } = useUpdateProject()

	const handleRenameCommit = () => {
		const newTitle = (renameInputRef.current?.value ?? '').trim()
		if (newTitle && newTitle !== project.title) {
			updateProject({
				id:              project.id,
				title:           newTitle,
				description:     project.description     ?? '',
				authorFirstName: project.authorFirstName ?? '',
				authorLastName:  project.authorLastName  ?? '',
			})
		}
		endRename()
	}

	const handleRenameKeyDown = (e) => {
		e.stopPropagation()
		if (e.key === 'Enter')  handleRenameCommit()
		if (e.key === 'Escape') endRename()
	}

	// ── Nav handlers ──────────────────────────────────────────────────────────
	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen((prev) => !prev)
	}

	const handleClick = () => {
		if (!open) setOpen(true)
		setSelection({ projectId: project.id, bookId: null, partId: null, chapterId: null, sceneId: null })
	}

	const handleContextMenu = (e) => {
		setSelection({ projectId: project.id, bookId: null, partId: null, chapterId: null, sceneId: null })
		openContextMenu(e, 'project', {
			id:    project.id,
			title: project.title,
		})
	}

	return (
		<Box>
			<ListItemButton
				selected={isSelected}
				onClick={handleClick}
				onContextMenu={handleContextMenu}
				sx={{ pl: 1 }}
			>
				<ListItemIcon
					sx={{ minWidth: 28, cursor: 'pointer' }}
					onClick={handleExpandToggle}
				>
					{open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<FolderIcon fontSize="small" />
				</ListItemIcon>

				{isRenaming ? (
					<InputBase
						inputRef={renameInputRef}
						defaultValue={project.title ?? ''}
						onBlur={handleRenameCommit}
						onKeyDown={handleRenameKeyDown}
						onClick={e => e.stopPropagation()}
						autoFocus
						fullWidth
						sx={{
							fontSize: '0.875rem',
							fontWeight: 600,
							borderBottom: '1px solid',
							borderColor: 'primary.main',
							'& .MuiInputBase-input': { p: 0 },
						}}
					/>
				) : (
					<ListItemText
						primary={project.title}
						slotProps={{ primary: { variant: 'body2', sx: { fontWeight: 600 } } }}
					/>
				)}
			</ListItemButton>

			<Collapse in={open} unmountOnExit>
				<Box>
					{books?.map((book) => (
						<BookItem
							key={book.id}
							book={book}
							selection={selection}
							setSelection={setSelection}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}
