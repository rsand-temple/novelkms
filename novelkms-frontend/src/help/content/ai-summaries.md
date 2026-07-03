---
id: ai.summaries
title: Chapter & Book Summaries
section: ai
order: 60
---
# Chapter & Book Summaries

Summaries are **reader-facing synopses**, separate from [memory documents](#help:ai.memory) (which are continuity context for the AI).

## Chapter summaries

A chapter summary is one readable paragraph generated from the chapter's prose. One per chapter; regenerating overwrites it.

- Click the **Summary** leaf beneath a chapter in the [navigation tree](#help:manuscript.nav-tree) to open it in the editor, where you can Generate, Regenerate, or hand-edit it.
- Or right-click a chapter → **Generate chapter summary** / **Edit chapter summary…** from the context menu.

Hand-editing marks the summary as edited so you know which are AI-generated and which you have touched.

## Book summaries

A book summary is a synopsis of up to ~1000 words, built **entirely from the chapter summaries** in order — never the raw manuscript, which is too large to summarize reliably in one pass.

Right-click a book → **View chapter summaries…** to see all chapter summaries with staleness chips, and to generate or regenerate the book summary. If coverage is missing or stale, NovelKMS offers to fill the gaps first.

## Staleness

A chapter summary is **stale** if the chapter's prose has been modified since the summary was generated. The staleness chip in the editor toolbar and the "View chapter summaries" dialog both show this. The book summary goes stale if any chapter summary is missing, stale, or newer than the book summary itself.

Both chapter and book summaries support [one-time guidance](#help:ai.guidance).
