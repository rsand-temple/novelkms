import { useMemo, useState } from 'react'
import {
	Box,
	FormControl,
	IconButton,
	ListItemText,
	Menu,
	MenuItem,
	Select,
	Tooltip,
} from '@mui/material'
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined'
import DeleteIcon from '@mui/icons-material/Delete'
import { AI_PROVIDERS, providerLabel } from './aiProviders'

// Per-document provider selector shown in the editor toolbar in AI-doc mode.
//
// Options are the union of the providers the user holds a credential for (so a
// variant can be generated) and the providers that already have a variant for
// this document (so an existing one can always be viewed, even if its credential
// was later removed) — presented in the roster order from aiProviders.js. A
// provider with no variant yet is annotated "not generated"; a variant-only
// provider with no credential is annotated "no key" and cannot be regenerated
// (the toolbar disables Generate for it).
//
// The kebab overflow carries the per-provider "Clear this document" action, so a
// multi-provider author can remove one variant without the nav's
// default-provider-only clear.
export default function AiDocProviderSelect({
	value,
	onChange,
	variants = [],
	credentialProviders = [],
	defaultProvider = null,
	docTypeLabel = 'document',
	onClearProvider,
	disabled = false,
}) {
	const [menuAnchor, setMenuAnchor] = useState(null)

	const variantProviders = useMemo(
		() => new Set(variants.map(v => v.provider).filter(Boolean)),
		[variants],
	)
	const credentialSet = useMemo(() => new Set(credentialProviders), [credentialProviders])

	// Roster order first, then any unknown/legacy provider keys that still have a
	// variant (e.g. a provider removed from the roster) so they remain viewable.
	const options = useMemo(() => {
		const known = AI_PROVIDERS
			.map(p => p.key)
			.filter(key => variantProviders.has(key) || credentialSet.has(key))
		const extras = [...variantProviders].filter(key => !AI_PROVIDERS.some(p => p.key === key))
		const keys = [...known, ...extras]
		// Guarantee the current value is always selectable even if it somehow falls
		// outside the union (defensive; keeps the Select controlled).
		if (value && !keys.includes(value)) keys.unshift(value)
		return keys.map(key => ({
			key,
			hasVariant: variantProviders.has(key),
			hasCredential: credentialSet.has(key),
		}))
	}, [variantProviders, credentialSet, value])

	const selectedHasVariant = !!value && variantProviders.has(value)

	if (options.length === 0) {
		// No credentials and no variants — nothing to choose. The toolbar's
		// Generate button (disabled, with its own tooltip) covers this case.
		return null
	}

	return (
		<Box sx={{ display: 'inline-flex', alignItems: 'center' }}>
			<FormControl size="small" variant="standard" sx={{ minWidth: 132 }} disabled={disabled}>
				<Select
					value={options.some(o => o.key === value) ? value : ''}
					onChange={(e) => onChange?.(e.target.value)}
					displayEmpty
					renderValue={(key) => key ? providerLabel(key) : 'Provider'}
					sx={{ fontSize: '0.8125rem' }}
				>
					{options.map(({ key, hasVariant, hasCredential }) => {
						const note = !hasVariant ? 'not generated' : (!hasCredential ? 'no key' : null)
						const isDefault = key === defaultProvider
						return (
							<MenuItem key={key} value={key} sx={{ fontSize: '0.8125rem' }}>
								<ListItemText
									primary={providerLabel(key)}
									secondary={[isDefault ? 'default' : null, note].filter(Boolean).join(' · ') || null}
									slotProps={{
										primary: { sx: { fontSize: '0.8125rem' } },
										secondary: { sx: { fontSize: '0.6875rem' } },
									}}
								/>
							</MenuItem>
						)
					})}
				</Select>
			</FormControl>

			<Tooltip title="More document actions">
				<span>
					<IconButton
						size="small"
						disabled={disabled}
						onClick={(e) => setMenuAnchor(e.currentTarget)}
						sx={{ ml: 0.25 }}
					>
						<TuneOutlinedIcon fontSize="small" />
					</IconButton>
				</span>
			</Tooltip>
			<Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={() => setMenuAnchor(null)}>
				<MenuItem
					disabled={!selectedHasVariant}
					onClick={() => { setMenuAnchor(null); onClearProvider?.() }}
				>
					<DeleteIcon fontSize="small" sx={{ mr: 1 }} />
					{`Clear ${providerLabel(value)} ${docTypeLabel}`}
				</MenuItem>
			</Menu>
		</Box>
	)
}
