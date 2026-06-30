/**
 * Human-readable byte size: 0 B, 512 B, 12 KB, 3.4 MB, 1.2 GB. Whole numbers
 * for bytes and for values ≥ 10 in a unit; one decimal otherwise.
 */
export function formatBytes(n) {
	if (n == null) return '—'
	if (n === 0) return '0 B'
	const units = ['B', 'KB', 'MB', 'GB', 'TB']
	const i = Math.min(units.length - 1, Math.floor(Math.log(n) / Math.log(1024)))
	const v = n / Math.pow(1024, i)
	const text = (i === 0 || v >= 10) ? String(Math.round(v)) : v.toFixed(1)
	return `${text} ${units[i]}`
}
