import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import PsychologyOutlinedIcon from '@mui/icons-material/PsychologyOutlined'
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined'
import RateReviewOutlinedIcon from '@mui/icons-material/RateReviewOutlined'

const ICONS = {
	memory: PsychologyOutlinedIcon,
	chapterSummary: SummarizeOutlinedIcon,
	editorial: RateReviewOutlinedIcon,
}

const LABELS = {
	memory: 'Memory',
	chapterSummary: 'Summary',
	editorial: 'Editorial',
}

/**
 * ChapterAiDocItem — fixed bottom leaf under a chapter for its Memory document,
 * Summary, or Editorial (selection.aiDocType = 'memory' | 'chapterSummary' |
 * 'editorial'). Opens the document for rich-text editing in EditorPanel, the
 * same way a scene opens.
 *
 * Deliberately NOT draggable/sortable and not a member of the chapter's scene
 * SortableContext — these aren't manuscript content, just fixed slots rendered
 * after the real scenes. Italic title + an icon distinct from the scene icon
 * keep them visually separate at a glance. No rename, no context menu of its
 * own: Generate/Regenerate live in EditorPanel's toolbar once selected, and the
 * chapter's own right-click menu still has the quick Generate/Clear actions.
 *
 * Props:
 *   docType    {'memory'|'chapterSummary'|'editorial'}
 *   chapterId  {string}
 *   bookId     {string}
 *   partId     {string|null}
 *   selection, setSelection
 *   depth      {number}  matches the sibling SceneItem's depth for indent
 */
export default function ChapterAiDocItem({ docType, chapterId, bookId, partId, selection, setSelection, depth = 0 }) {
	const Icon = ICONS[docType]
	const isSelected = selection.chapterId === chapterId
		&& selection.aiDocType === docType
		&& !selection.sceneId

	const handleClick = () => {
		setSelection((prev) => ({
			...prev,
			bookId,
			partId: partId ?? null,
			chapterId,
			sceneId: null,
			codexId: null,
			codexCategory: null,
			aiDocType: docType,
		}))
	}

	return (
		<ListItemButton
			selected={isSelected}
			onClick={handleClick}
			sx={{ pl: 5 + depth * 3 }}
		>
			<ListItemIcon sx={{ minWidth: 28 }}>
				<Icon fontSize="small" sx={{ color: 'text.secondary' }} />
			</ListItemIcon>
			<ListItemText
				primary={LABELS[docType]}
				slotProps={{ primary: { variant: 'body2', sx: { fontStyle: 'italic', color: 'text.secondary' } } }}
			/>
		</ListItemButton>
	)
}
