---
id: ai.memory
title: Memory Documents
section: ai
order: 50
---
# Memory Documents

A **memory document** is a short, structured "story so far" summary for a chapter. When you review a later chapter, NovelKMS assembles the preceding chapters' memory documents into continuity context for the model — so it understands what happened earlier without you pasting the whole book.

## Generating and editing

- Click the **Memory** leaf beneath a chapter in the [navigation tree](#help:manuscript.nav-tree) to open the memory document in the editor. From there you can Generate, Regenerate, or hand-edit it. The toolbar also carries the [one-time guidance](#help:ai.guidance) field.
- Or right-click a chapter → **Generate memory document** for a quick one-click generation.

There is one memory document per chapter. Regenerating overwrites the previous one; hand-editing marks it as edited.

## Staleness warnings

Memory documents are timestamped. When you run a chapter review, NovelKMS checks whether any **preceding** chapter's memory document is missing, stale, or out of sequence. If so, it warns you first and offers to regenerate the flagged ones in book order before proceeding with the review.

This keeps your "story so far" context accurate even if you revised earlier chapters since you last generated their memories.

## Memory templates

The structure of a generated memory document follows a template, editable at global, project, and book scopes under **Settings → AI**. This lets you tell the model which elements you want captured (character positions, open plot threads, etc.).

Memory documents are continuity context, distinct from reader-facing [summaries](#help:ai.summaries).
