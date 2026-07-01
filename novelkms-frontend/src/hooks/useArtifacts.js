import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { artifactsApi } from '../api/artifacts'

// ── Query key factory ─────────────────────────────────────────────────────────
// The whole project tree is one cached list; the nav folders and the Explorer
// both derive their children from it by parentId (single source of truth, the
// same approach the manuscript DnD uses). Usage is a separate per-user number,
// keyed by project only because the endpoint is project-rooted.

export const ARTIFACT_KEYS = {
	root:  () => ['artifacts'],
	tree:  (projectId) => ['artifacts', 'tree', projectId],
	usage: (projectId) => ['artifacts', 'usage', projectId],
}

// ── Queries ───────────────────────────────────────────────────────────────────

export function useArtifactTree(projectId) {
	return useQuery({
		queryKey: ARTIFACT_KEYS.tree(projectId),
		queryFn:  () => artifactsApi.tree(projectId).then(d => d.nodes ?? []),
		enabled:  !!projectId,
	})
}

export function useArtifactUsage(projectId) {
	return useQuery({
		queryKey: ARTIFACT_KEYS.usage(projectId),
		queryFn:  () => artifactsApi.usage(projectId),
		enabled:  !!projectId,
	})
}

// ── Mutations ─────────────────────────────────────────────────────────────────
// Each invalidates the project tree; uploads/trash also touch usage. Errors are
// re-thrown (not swallowed) so the calling surface can show the structured
// file_too_large / storage_quota_exceeded / name_conflict messages.

export function useCreateArtifactFolder() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId, parentId, name }) => artifactsApi.createFolder(projectId, parentId, name),
		onSuccess:  (_node, { projectId }) =>
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) }),
	})
}

export function useUploadArtifactFile() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ projectId, parentId, file }) => artifactsApi.uploadFile(projectId, parentId, file),
		onSuccess:  (_node, { projectId }) => {
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) })
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.usage(projectId) })
		},
	})
}

export function useRenameArtifactNode() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ nodeId, name }) => artifactsApi.rename(nodeId, name),
		onSuccess:  (_node, { projectId }) =>
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) }),
	})
}

export function useMoveArtifactNode() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ nodeId, parentId }) => artifactsApi.move(nodeId, parentId),
		onSuccess:  (_node, { projectId }) =>
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) }),
	})
}

export function useTrashArtifactNode() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ nodeId }) => artifactsApi.trash(nodeId),
		onSuccess:  (_res, { projectId }) => {
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) })
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.usage(projectId) })
			qc.invalidateQueries({ queryKey: ['trash'] })
		},
	})
}

// ── Text editing ──────────────────────────────────────────────────────────────

export function useArtifactText(nodeId) {
	return useQuery({
		queryKey: ['artifacts', 'text', nodeId],
		queryFn:  () => artifactsApi.readText(nodeId),
		enabled:  !!nodeId,
		// Don't refetch on window focus — the user may have unsaved edits.
		refetchOnWindowFocus: false,
	})
}

export function useSaveArtifactText() {
	const qc = useQueryClient()
	return useMutation({
		mutationFn: ({ nodeId, text }) => artifactsApi.writeText(nodeId, text),
		onSuccess:  (updatedNode, { nodeId, text, projectId }) => {
			// Update the cached text so the editor's "changed" detection resets.
			qc.setQueryData(['artifacts', 'text', nodeId], text)
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.tree(projectId) })
			qc.invalidateQueries({ queryKey: ARTIFACT_KEYS.usage(projectId) })
		},
	})
}

// ── Error helper ──────────────────────────────────────────────────────────────
// Maps a backend artifact error body to a friendly message for snackbars.

export function artifactErrorMessage(error) {
	const data = error?.response?.data
	if (!data) return 'Something went wrong. Please try again.'
	switch (data.error) {
		case 'file_too_large':
			return `That file is too large. The maximum is ${mb(data.maxBytes)}.`
		case 'storage_quota_exceeded':
			return `Not enough storage: this file needs ${mb(data.fileBytes)} but only `
				+ `${mb((data.quotaBytes ?? 0) - (data.usedBytes ?? 0))} is free.`
		case 'name_conflict':
			return data.message || 'An item with that name already exists here.'
		case 'invalid_name':
		case 'invalid_parent':
		case 'invalid_move':
			return data.message || 'That action is not allowed.'
		default:
			return data.message || 'Something went wrong. Please try again.'
	}
}

function mb(bytes) {
	if (bytes == null) return '—'
	const mbVal = bytes / (1024 * 1024)
	return mbVal >= 10 ? `${Math.round(mbVal)} MB` : `${mbVal.toFixed(1)} MB`
}
