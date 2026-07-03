---
id: ai.guidance
title: One-Time Guidance
section: ai
order: 70
---
# One-Time Guidance

Every generation flow — review, memory document, chapter summary, book summary, and [editorial](#help:ai.editorial) — has an optional **Guidance for this run** field above its action button.

Use it to steer or correct a single run without permanently changing anything:

> "The letter in this chapter is canonically a forgery — don't flag it as a continuity error."

Guidance applies only to that one generation and is recorded on the resulting artifact as provenance. The field pre-fills from whatever guidance produced the current artifact and is **not** cleared after running, so you can re-run, refine, or clear it as you iterate.

For a *persistent* change to the editorial persona, use [form instructions](#help:ai.form-instructions) instead.

**Note:** The sequential batch-regenerate flows (such as "regenerate flagged memory docs and then review") do not surface a guidance field — a single note doesn't map cleanly onto a multi-chapter batch run.
