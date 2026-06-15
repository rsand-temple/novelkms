import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { SearchContext } from './SearchContext'
import { partsApi } from '../api/parts'
import { chaptersApi } from '../api/chapters'
import { scenesApi } from '../api/scenes'
import { countHtmlOccurrences } from './searchUtils'

function scopeFromSelection(selection) {
	if (selection.sceneId) return { type: 'scene', id: selection.sceneId }
	if (selection.chapterId) return { type: 'chapter', id: selection.chapterId }
	if (selection.partId) return { type: 'part', id: selection.partId }
	if (selection.bookId) return { type: 'book', id: selection.bookId }
	return { type: 'none', id: null }
}

async function loadChapterScenes(chapter, partId = null, bookId = null) {
	const scenes = await scenesApi.getByChapter(chapter.id)
	return scenes.map(scene => ({
		...scene,
		chapterId: chapter.id,
		partId: partId ?? chapter.partId ?? null,
		bookId: bookId ?? chapter.bookId ?? null,
	}))
}

async function loadScopeScenes(selection) {
	if (selection.sceneId) {
		const scene = await scenesApi.getById(selection.sceneId)
		return [{
			...scene,
			chapterId: selection.chapterId ?? scene.chapterId,
			partId: selection.partId ?? null,
			bookId: selection.bookId ?? null,
		}]
	}

	if (selection.chapterId) {
		const chapter = await chaptersApi.getById(selection.chapterId)
		return loadChapterScenes(chapter, selection.partId, selection.bookId)
	}

	if (selection.partId) {
		const chapters = await partsApi.getChapters(selection.partId)
		const nested = await Promise.all(
			chapters.map(ch => loadChapterScenes(ch, selection.partId, selection.bookId))
		)
		return nested.flat()
	}

	if (selection.bookId) {
		const [parts, directChapters] = await Promise.all([
			partsApi.getByBook(selection.bookId),
			chaptersApi.getByBook(selection.bookId),
		])
		const partChapterGroups = await Promise.all(
			parts.map(async part => ({ part, chapters: await partsApi.getChapters(part.id) }))
		)
		const nested = []
		for (const { part, chapters } of partChapterGroups) {
			const sceneGroups = await Promise.all(
				chapters.map(ch => loadChapterScenes(ch, part.id, selection.bookId))
			)
			nested.push(...sceneGroups.flat())
		}
		const directGroups = await Promise.all(
			directChapters.map(ch => loadChapterScenes(ch, null, selection.bookId))
		)
		return [...nested, ...directGroups.flat()]
	}

	return []
}

export function SearchProvider({ selection, children }) {
	const [open, setOpen] = useState(false)
	const [query, setQuery] = useState('')
	const [matchCase, setMatchCase] = useState(false)
	const [replaceText, setReplaceText] = useState('')
	const [replaceOpen, setReplaceOpen] = useState(false)
	const [sceneResults, setSceneResults] = useState([])
	const [activeIndex, setActiveIndex] = useState(-1)
	const [loading, setLoading] = useState(false)
	const [scopeSelection, setScopeSelection] = useState(null)
	const editorActionsRef = useRef(null)
	const requestSeq = useRef(0)

	const effectiveSelection = scopeSelection ?? selection
	const scope = useMemo(() => scopeFromSelection(effectiveSelection), [effectiveSelection])

	useEffect(() => {
		if (!open) {
			setScopeSelection(null)
			return
		}
		setScopeSelection(selection)
	}, [open, selection])

	useEffect(() => {
		const onKeyDown = (event) => {
			if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'f') {
				event.preventDefault()
				setOpen(true)
				requestAnimationFrame(() => document.querySelector('[data-nkms-search-input]')?.focus())
			}
		}
		document.addEventListener('keydown', onKeyDown)
		return () => document.removeEventListener('keydown', onKeyDown)
	}, [])

	useEffect(() => {
		if (!open || !query || scope.type === 'none') {
			setSceneResults([])
			setActiveIndex(-1)
			setLoading(false)
			return
		}

		const seq = ++requestSeq.current
		setLoading(true)
		const timer = setTimeout(async () => {
			try {
				const scenes = await loadScopeScenes(effectiveSelection)
				if (seq !== requestSeq.current) return
				const results = scenes
					.map(scene => ({
						sceneId: scene.id,
						chapterId: scene.chapterId,
						partId: scene.partId ?? null,
						bookId: scene.bookId ?? effectiveSelection.bookId ?? null,
						title: scene.title || 'Untitled Scene',
						count: countHtmlOccurrences(scene.content, query, matchCase),
					}))
					.filter(item => item.count > 0)
				setSceneResults(results)
				const total = results.reduce((sum, item) => sum + item.count, 0)
				setActiveIndex(total > 0 ? 0 : -1)
			} catch (error) {
				if (seq === requestSeq.current) {
					console.error('[SearchProvider] Search failed:', error)
					setSceneResults([])
					setActiveIndex(-1)
				}
			} finally {
				if (seq === requestSeq.current) setLoading(false)
			}
		}, 80)
		return () => clearTimeout(timer)
	}, [open, query, matchCase, scope.type, scope.id, effectiveSelection])

	const totalCount = useMemo(
		() => sceneResults.reduce((sum, item) => sum + item.count, 0),
		[sceneResults]
	)

	const flattenedMatches = useMemo(() => {
		const out = []
		sceneResults.forEach(result => {
			for (let i = 0; i < result.count; i += 1) out.push({ ...result, localIndex: i })
		})
		return out
	}, [sceneResults])

	const activeMatch = activeIndex >= 0 ? flattenedMatches[activeIndex] ?? null : null


	const move = useCallback((delta) => {
		if (!totalCount) return
		setActiveIndex(current => {
			const base = current < 0 ? 0 : current
			return (base + delta + totalCount) % totalCount
		})
	}, [totalCount])

	const close = useCallback(() => {
		setOpen(false)
		setScopeSelection(null)
		setSceneResults([])
		setActiveIndex(-1)
	}, [])

	const updateLiveSceneCount = useCallback((sceneId, count, metadata = {}) => {
		if (!open || !query || !sceneId) return
		setSceneResults(current => {
			const index = current.findIndex(item => item.sceneId === sceneId)
			if (count <= 0) return current.filter(item => item.sceneId !== sceneId)
			const nextItem = {
				...(index >= 0 ? current[index] : {}),
				...metadata,
				sceneId,
				count,
			}
			if (index < 0) return [...current, nextItem]
			const next = [...current]
			next[index] = nextItem
			return next
		})
	}, [open, query])

	const registerEditorActions = useCallback((actions) => {
		editorActionsRef.current = actions
		return () => {
			if (editorActionsRef.current === actions) editorActionsRef.current = null
		}
	}, [])

	const replaceCurrent = useCallback(() => editorActionsRef.current?.replaceCurrent?.(replaceText), [replaceText])
	const replaceAll = useCallback(() => editorActionsRef.current?.replaceAll?.(replaceText), [replaceText])

	const counts = useMemo(() => {
		const scene = {}
		const chapter = {}
		const part = {}
		const book = {}
		for (const item of sceneResults) {
			scene[item.sceneId] = item.count
			if (item.chapterId) chapter[item.chapterId] = (chapter[item.chapterId] ?? 0) + item.count
			if (item.partId) part[item.partId] = (part[item.partId] ?? 0) + item.count
			if (item.bookId) book[item.bookId] = (book[item.bookId] ?? 0) + item.count
		}
		return { scene, chapter, part, book }
	}, [sceneResults])

	const value = {
		open, setOpen, close,
		query, setQuery,
		matchCase, setMatchCase,
		replaceText, setReplaceText,
		replaceOpen, setReplaceOpen,
		loading, scope,
		totalCount,
		activeIndex,
		activeMatch,
		previous: () => move(-1),
		next: () => move(1),
		counts,
		updateLiveSceneCount,
		registerEditorActions,
		replaceCurrent,
		replaceAll,
	}

	return <SearchContext.Provider value={value}>{children}</SearchContext.Provider>
}
