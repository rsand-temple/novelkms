---
id: manuscript.trash
title: Trash & Soft Delete
section: manuscript
order: 40
---
# Trash & Soft Delete

Deleting a project, book, chapter, scene, Codex item, artifact file, or artifact folder does **not** destroy it. It is soft-deleted and moved to **Trash**, where it can be restored.

- **Restore** returns an item to the end of its original parent.
- **Purge** permanently removes a single item.
- **Empty Trash** permanently removes everything in Trash.

Only purge and empty-trash are irreversible.

## Special cases

- **Parts** are an exception to the Trash model: deleting a part promotes its chapters to the book and is immediate, not sent to Trash.
- **Artifact storage quota** is not freed by trashing — only by purging. If you need to reclaim storage space, you must purge the artifact from Trash.

## Restoring

Restoring an item appends it to the end of its original parent. If the parent itself was also deleted, you need to restore the parent first. Restored items get a deduplicated name if a sibling with the same name already exists.
