import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderIcon from '@mui/icons-material/Folder'
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined'

/**
 * One folder in the Artifacts nav tree. Navigation + selection only — selecting
 * it sets selection.artifactFolderId and swaps the center pane into the
 * Explorer for that folder. Files are intentionally NOT shown in the nav tree
 * (folders only, Windows tree-pane model); all file operations live in the
 * Explorer. Folder nodes are deliberately non-draggable in v1.
 *
 * Children are derived from the single cached project tree by parentId, so the
 * whole hierarchy renders from one query.
 */
export default function ArtifactFolderItem({ folder, tree, depth, projectId, selection, setSelection }) {
	const [open, setOpen] = useState(false)

	const childFolders = tree
		.filter(n => n.parentId === folder.id && n.type === 'FOLDER')
		.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name))

	const hasChildren = childFolders.length > 0
	const isSelected = selection.artifactFolderId === folder.id
	const pl = 4 + depth * 2

	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setOpen(o => !o)
	}

	const handleClick = () => {
		if (hasChildren && !open) setOpen(true)
		setSelection((prev) => ({
			...prev,
			projectId,
			bookId: null,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			artifactFolderId: folder.id,
		}))
	}

	return (
		<Box>
			<ListItemButton selected={isSelected} onClick={handleClick} sx={{ pl }}>
				<ListItemIcon sx={{ minWidth: 28, cursor: hasChildren ? 'pointer' : 'default' }} onClick={handleExpandToggle}>
					{hasChildren
						? (open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />)
						: null}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					{open
						? <FolderOpenOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
						: <FolderIcon fontSize="small" sx={{ color: 'text.secondary' }} />}
				</ListItemIcon>
				<ListItemText
					primary={folder.name}
					slotProps={{ primary: { variant: 'body2', noWrap: true } }}
				/>
			</ListItemButton>

			{hasChildren && (
				<Collapse in={open} unmountOnExit>
					<Box>
						{childFolders.map((child) => (
							<ArtifactFolderItem
								key={child.id}
								folder={child}
								tree={tree}
								depth={depth + 1}
								selection={selection}
								setSelection={setSelection}
							/>
						))}
					</Box>
				</Collapse>
			)}
		</Box>
	)
}
