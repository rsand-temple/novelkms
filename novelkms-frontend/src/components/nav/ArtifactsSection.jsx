import { useState } from 'react'
import { Box, Collapse, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import FolderSpecialIcon from '@mui/icons-material/FolderSpecial'
import { useArtifactTree } from '../../hooks/useArtifacts'
import ArtifactFolderItem from './ArtifactFolderItem'

/**
 * The "Artifacts" root node for a project — a per-project store for
 * non-manuscript files (query letters, research, cover-art sources). Rendered
 * last under a project, after the Codex section.
 *
 * Selecting this row (or any folder under it) opens the Windows-Explorer-style
 * Artifacts panel in the center pane. Sub-folders appear as nodes underneath,
 * just like a conventional file tree; files are shown only in the Explorer's
 * details pane, never in the nav tree. The node is fixed and non-draggable.
 */
export default function ArtifactsSection({ projectId, open, selection, setSelection }) {
	const [sectionOpen, setSectionOpen] = useState(false)
	const { data: tree } = useArtifactTree(open && sectionOpen ? projectId : null)

	if (!open) return null

	const topFolders = (tree ?? [])
		.filter(n => n.parentId == null && n.type === 'FOLDER')
		.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name))

	const isSelected = selection.artifactFolderId === 'root' && selection.projectId === projectId

	const handleExpandToggle = (e) => {
		e.stopPropagation()
		setSectionOpen(o => !o)
	}

	const handleClick = () => {
		if (!sectionOpen) setSectionOpen(true)
		setSelection((prev) => ({
			...prev,
			projectId,
			bookId: null,
			partId: null,
			chapterId: null,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			artifactFolderId: 'root',
		}))
	}

	return (
		<Box>
			<ListItemButton selected={isSelected} onClick={handleClick} sx={{ pl: 4 }}>
				<ListItemIcon sx={{ minWidth: 28, cursor: 'pointer' }} onClick={handleExpandToggle}>
					{sectionOpen ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
				</ListItemIcon>
				<ListItemIcon sx={{ minWidth: 28 }}>
					<FolderSpecialIcon fontSize="small" sx={{ color: 'secondary.main' }} />
				</ListItemIcon>
				<ListItemText
					primary="Artifacts"
					slotProps={{ primary: {
						variant: 'body2',
						sx: { fontWeight: 650, fontSize: '0.74rem', textTransform: 'uppercase', letterSpacing: '0.055em', color: 'text.secondary' },
					} }}
				/>
			</ListItemButton>

			<Collapse in={sectionOpen} unmountOnExit>
				<Box>
					{topFolders.map((folder) => (
						<ArtifactFolderItem
							key={folder.id}
							folder={folder}
							tree={tree ?? []}
							depth={1}
							projectId={projectId}
							selection={selection}
							setSelection={setSelection}
						/>
					))}
				</Box>
			</Collapse>
		</Box>
	)
}
