export function generateDefaultSceneTitle() {
	const uuid = globalThis.crypto?.randomUUID?.()
	const suffix = uuid
		? uuid.substring(0, 4)
		: Math.floor(Math.random() * 0x10000)
			.toString(16)
			.padStart(4, '0')

	return `New Scene [${suffix}]`
}

export function isDefaultSceneTitle(title) {
	return /^New Scene \[[0-9a-f]{4}\]$/i.test(title ?? '')
}
