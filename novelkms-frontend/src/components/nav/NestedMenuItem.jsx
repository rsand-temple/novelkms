import { useState, useRef, useCallback } from 'react'
import { MenuItem, ListItemIcon, ListItemText, Menu } from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'

/**
 * NestedMenuItem
 *
 * A MenuItem that opens a nested Menu of its own children, anchored to
 * itself. Renders as a single flat MenuItem so it can sit directly among a
 * parent Menu's other MenuItem children — same rule as the top-level nav
 * context menu (no Fragment wrappers; MUI indexes direct children).
 *
 * Opens on hover (after a short delay to avoid flicker while scanning the
 * list) or on click/Enter/ArrowRight; closes on mouse-out (short delay,
 * cancelled if the pointer lands on the submenu itself) or Escape/ArrowLeft.
 *
 * children must themselves be flat MenuItem elements — the same rule
 * applies one level down.
 */
export default function NestedMenuItem({ label, icon, disabled = false, children }) {
	const [anchorEl, setAnchorEl] = useState(null)
	const [open, setOpen] = useState(false)
	const closeTimer = useRef(null)
	const openTimer = useRef(null)

	const cancelTimers = useCallback(() => {
		if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null }
		if (openTimer.current) { clearTimeout(openTimer.current); openTimer.current = null }
	}, [])

	const scheduleOpen = useCallback(() => {
		cancelTimers()
		openTimer.current = setTimeout(() => setOpen(true), 100)
	}, [cancelTimers])

	const scheduleClose = useCallback(() => {
		cancelTimers()
		closeTimer.current = setTimeout(() => setOpen(false), 200)
	}, [cancelTimers])

	const handleKeyDown = (e) => {
		if (disabled) return
		if (e.key === 'ArrowRight' || e.key === 'Enter') {
			e.preventDefault()
			e.stopPropagation()
			cancelTimers()
			setOpen(true)
		} else if (e.key === 'ArrowLeft' || e.key === 'Escape') {
			cancelTimers()
			setOpen(false)
		}
	}

	return (
		<MenuItem
			dense
			disabled={disabled}
			ref={setAnchorEl}
			selected={open}
			onMouseEnter={disabled ? undefined : scheduleOpen}
			onMouseLeave={disabled ? undefined : scheduleClose}
			onClick={(e) => { if (disabled) return; e.stopPropagation(); cancelTimers(); setOpen(true) }}
			onKeyDown={handleKeyDown}
		>
			{icon && <ListItemIcon>{icon}</ListItemIcon>}
			<ListItemText>{label}</ListItemText>
			<ChevronRightIcon fontSize="small" sx={{ ml: 1, opacity: 0.6 }} />

			{!disabled && (
				<Menu
					open={open}
					anchorEl={anchorEl}
					onClose={() => setOpen(false)}
					anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
					transformOrigin={{ vertical: 'top', horizontal: 'left' }}
					disableAutoFocus
					disableEnforceFocus
					disableRestoreFocus
					hideBackdrop
					slotProps={{
						root: { style: { pointerEvents: 'none' } },
						paper: { style: { pointerEvents: 'auto' } },
						list: {
							dense: true,
							onMouseEnter: cancelTimers,
							onMouseLeave: scheduleClose,
						},
					}}
				>
					{children}
				</Menu>
			)}
		</MenuItem>
	)
}
