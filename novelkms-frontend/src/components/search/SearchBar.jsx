import { useEffect, useRef } from 'react'
import {
	Box, Checkbox, FormControlLabel, IconButton, TextField, Tooltip, Typography, Button,
} from '@mui/material'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import CloseIcon from '@mui/icons-material/Close'
import FindReplaceIcon from '@mui/icons-material/FindReplace'
import { useSearch } from '../../search/SearchContext'

export default function SearchBar() {
	const search = useSearch()
	const inputRef = useRef(null)

	useEffect(() => {
		if (search.open) requestAnimationFrame(() => inputRef.current?.focus())
	}, [search.open])

	if (!search.open) return null

	const position = search.totalCount > 0 ? search.activeIndex + 1 : 0
	const scopeLabel = search.scope.type === 'none' ? 'No scope' : search.scope.type

	return (
		<Box sx={{ borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper', px: 1.5, py: 1 }}>
			<Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center', flexWrap: 'wrap' }}>
				<TextField
					inputRef={inputRef}
					size="small"
					slotProps={{
						htmlInput: { 'data-nkms-search-input': true },
					}}
					placeholder={`Find in ${scopeLabel}`}
					value={search.query}
					onChange={e => search.setQuery(e.target.value)}
					onKeyDown={e => {
						if (e.key === 'Enter') e.shiftKey ? search.previous() : search.next()
						if (e.key === 'Escape') search.close()
					}}
					sx={{ minWidth: 260, flex: '1 1 280px' }}
				/>

				<Typography variant="body2" sx={{ minWidth: 64, textAlign: 'center', color: search.totalCount ? 'text.primary' : 'text.disabled' }}>
					{search.loading ? '…' : `${position} / ${search.totalCount}`}
				</Typography>

				<Tooltip title="Previous match"><span><IconButton size="small" disabled={!search.totalCount} onClick={search.previous}><KeyboardArrowUpIcon /></IconButton></span></Tooltip>
				<Tooltip title="Next match"><span><IconButton size="small" disabled={!search.totalCount} onClick={search.next}><KeyboardArrowDownIcon /></IconButton></span></Tooltip>

				<FormControlLabel
					control={<Checkbox size="small" checked={search.matchCase} onChange={e => search.setMatchCase(e.target.checked)} />}
					label="Match case"
					sx={{ mr: 0, '& .MuiFormControlLabel-label': { fontSize: '0.8rem' } }}
				/>

				<Tooltip title={search.replaceOpen ? 'Hide replace' : 'Show replace'}>
					<IconButton size="small" onClick={() => search.setReplaceOpen(v => !v)} color={search.replaceOpen ? 'primary' : 'default'}>
						<FindReplaceIcon />
					</IconButton>
				</Tooltip>
				<Tooltip title="Close search"><IconButton size="small" onClick={search.close}><CloseIcon /></IconButton></Tooltip>
			</Box>

			{search.replaceOpen && (
				<Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center', mt: 1, pl: 0 }}>
					<TextField
						size="small"
						placeholder="Replace with"
						value={search.replaceText}
						onChange={e => search.setReplaceText(e.target.value)}
						onKeyDown={e => { if (e.key === 'Escape') search.close() }}
						sx={{ minWidth: 260, flex: '1 1 280px' }}
					/>
					<Button size="small" variant="outlined" disabled={!search.totalCount} onClick={search.replaceCurrent}>Replace</Button>
					<Button size="small" variant="outlined" disabled={!search.totalCount} onClick={search.replaceAll}>Replace all in open section</Button>
				</Box>
			)}
		</Box>
	)
}
