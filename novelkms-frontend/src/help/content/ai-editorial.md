---
id: ai.editorial
title: Editorials
section: ai
order: 65
---
# Editorials

An **editorial** is a short, impressionistic reading of a chapter written for your eyes only — the model's overall take on tone, genre drift, character arcs, and how the storyline is evolving in that chapter.

It is **not** a review. There are no discrete findings, no severity ratings, and no Codex promotion. The model is acting as a thoughtful reader reflecting on what it just read, not as a copy-editor marking problems. It deliberately skips spelling, grammar, and line-level issues (unless something is egregious) and keeps its remarks to about half a page — less is more.

## How to generate an editorial

- Right-click a chapter in the [navigation tree](#help:manuscript.nav-tree) → **Generate editorial** (quick, no gate).
- Or click the **Editorial** leaf beneath a chapter to open it in the editor, then use the **Generate** / **Regenerate** button in the toolbar.

Like memory documents and summaries, there is one editorial per chapter. Regenerating overwrites the previous one; you can hand-edit it (which marks it as edited).

## Context inputs

When generating, the model receives the same context a chapter review does — preceding chapters' [memory documents](#help:ai.memory) as "story so far," plus any pinned Codex reference entries. This gives it enough background to comment meaningfully on arc and drift without you having to paste the whole manuscript.

## What it is not used for

An editorial is never read back by any other AI function. It does not feed into reviews, memory generation, or book summaries. It exists purely for you.

## One-time guidance

The [Guidance for this run](#help:ai.guidance) field is available, so you can steer a single generation ("focus on pacing and the relationship between Mara and the detective") without changing any persistent settings.

## Related

- [AI Review Rail](#help:ai.review.rail) — findings to triage.
- [Memory Documents](#help:ai.memory) — continuity context.
- [Chapter & Book Summaries](#help:ai.summaries) — reader-facing synopses.
