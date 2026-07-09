import { Box } from '@mui/material'

/**
 * Wrappers around the commissioned brand assets in public/brand/. Each asset is a
 * flattened image with its own background baked in (cream or navy) rather than a
 * transparent cutout - pick the variant that matches the surface it's sitting on
 * (`onDark`) so there's no visible seam around the image.
 */

export function LogoMark({ size = 32, onDark = true, sx, ...rest }) {
	return (
		<Box
			component="img"
			src={`${import.meta.env.BASE_URL}brand/${onDark ? 'novelkms-icon-dark.png' : 'novelkms-icon.png'}`}
			alt="NovelKMS"
			width={size}
			height={size}
			sx={{ flexShrink: 0, display: 'block', borderRadius: '20%', ...sx }}
			{...rest}
		/>
	)
}

/** Full vertical lockup (mark + wordmark + tagline) - cream background only; use on paper-colored surfaces. */
export function LogoLockup({ width = 280, sx, ...rest }) {
	return (
		<Box
			component="img"
			src={`${import.meta.env.BASE_URL}brand/novelkms-logo.png`}
			alt="NovelKMS — Write your story, remember your world"
			sx={{ width, height: 'auto', display: 'block', ...sx }}
			{...rest}
		/>
	)
}

/** Horizontal lockup (mark + wordmark + tagline side by side) - cream background only. */
export function LogoBanner({ width = 420, sx, ...rest }) {
	return (
		<Box
			component="img"
			src={`${import.meta.env.BASE_URL}brand/novelkms-banner.png`}
			alt="NovelKMS — Write your story, remember your world"
			sx={{ width, height: 'auto', display: 'block', ...sx }}
			{...rest}
		/>
	)
}
