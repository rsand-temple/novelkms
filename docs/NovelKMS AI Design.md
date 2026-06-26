# NovelKMS AI Design

## Purpose

AI in NovelKMS is an editorial-review system, not a manuscript-generation system. The AI layer should critique, analyze, organize, and help triage revision work while keeping the manuscript under the author's control.

AI output is stored as first-class NovelKMS data. It is not treated as a transient chat transcript.

## Current AI model

- Bring-your-own-key model.
- OpenAI provider implemented first.
- Provider abstraction exists so additional providers can be added later.
- Per-user AI credentials are encrypted at rest.
- Review artifacts and recommendations are persisted.
- No automatic manuscript modification.
- No app-native general chat UI.

## Credential model

Credentials are per user and may support multiple providers/labels over time.

Current design:

- `ai_credential` stores provider, label, encrypted API key, key-last-four hint, default model, default flag, and status.
- API keys are write-only from the frontend perspective.
- Only masked key hints are returned.
- `SecretCipher` encrypts keys with AES-GCM using `NOVELKMS_ENCRYPTION_KEY` in real deployments.
- A development fallback key may exist for local use but must not be used for production credentials.

## Provider abstraction

The backend should remain provider-agnostic outside provider implementation classes.

Conceptual interface:

```java
public interface AiProvider {
    String providerKey();
    String defaultModel();
    ReviewResult reviewChapter(ChapterReviewRequest request) throws AiProviderException;
}
```

Current provider:

- `OpenAiProvider`

Possible future providers:

- Anthropic
- Google Gemini
- Azure OpenAI
- OpenRouter
- Local LLMs

## Review artifacts

A review run creates an immutable `ai_review` row. Running the same chapter again creates a new review. Provider failures should be captured as failed review artifacts where possible so the user sees the failure in history.

A review recommendation is individually triageable.

| Status      | Meaning                                                  | Working-tab placement |
| ----------- | -------------------------------------------------------- | --------------------- |
| `OPEN`      | New / undecided finding                                  | Active                |
| `DEFERRED`  | Valid, but not now (unfinished by definition)            | Active                |
| `DONE`      | Acted on, or the manuscript now addresses it             | Resolved              |
| `DISMISSED` | Disagree / false positive / stylistic / not applicable   | Resolved              |
| `PROMOTED`  | Converted into a Codex entry (inert; auditable)          | Resolved              |
| `DELETED`   | Legacy / admin cleanup only; not set from the finding UI | Hidden                |

The review rail triages findings bug-tracker style across three tabs: **Active** (OPEN + DEFERRED), **Resolved** (DONE + DISMISSED + PROMOTED), and **History** (the review runs themselves; focus one or move it to Trash). Per-finding hard delete was removed — Dismiss covers "make it go away," and whole-review deletion goes through Trash. The migration V23 renamed the prior ACCEPTED/REJECTED/FUTURE values in place. No prompt-version bump: model output shape is unchanged.

The desired workflow is to empty the immediate review queue.

## Chapter review workflow

In the workflow regarding AI Review findings, the goal is to completely clear all findings. Ultimately I'm treating these like bug reports - fixed, won't fix, move to future version

I don't often refer back to an old review, unless there's unfinished business.

For button labels:

```
OPEN
DONE
DISMISSED
DEFERRED
```

`OPEN` means unfinished business.

`DONE` means “I acted on this, or I reviewed it and concluded the manuscript now addresses it.” This is what your current Accept button probably wants to become. It should remove the finding from the active review list, but not delete it.

`DISMISSED` means “I disagree, not useful, false positive, stylistic preference, or not applicable.” This is what your current Reject button should become. It should also disappear from the active list, but remain in history.

`DEFERRED` means “valid, but not now.” That is your “move to future version” state. This one should remain visible somewhere, because it is unfinished by definition.

Hard deletion is avoided except as an admin cleanup action or maybe “Delete review run.” 

The UI behavior should be:

```
Active tab:
  OPEN + DEFERRED

Resolved tab:
  DONE + DISMISSED

History:
  Review runs, collapsed by date/model/prompt version
```

The default review rail or dialog should show only active items. Once you mark something `DONE` or `DISMISSED`, it should vanish from the active list immediately.

Use:

```
Mark Done
Dismiss
Defer
Reopen
```

For a newly generated finding:

```
[Mark Done] [Dismiss] [Defer]
```

For a resolved finding:

```
[Reopen]
```

For a deferred finding:

```
[Mark Done] [Dismiss] [Reopen]
```

## Review rail UX

The AI review surface belongs next to the editor, not inside the Properties panel. Review mode is a visual layer on normal chapter editing.

Current rail behavior:

- Collapsible right-side editor rail.
- Review history selector.
- Run-review controls.
- Recommendation cards.
- Accept / Reject / Defer / Copy actions visible on cards.
- Overflow actions for Add to Codex and Delete.
- Open-findings badge when collapsed.

## Anchor text and click-to-scroll

Prompt version `chapter-review-v2` asks the model for a short verbatim `anchorText` quote from the chapter. When present, the recommendation location can be clicked to locate and temporarily highlight the passage in the editor.

Important behavior:

- Reviews created before anchor text support may have null `anchorText`.
- Matching should search the loaded ProseMirror document text, not raw HTML.
- Highlighting is transient and must not be serialized into stored scene HTML.

## Codex promotion

Recommendations may include suggested Codex metadata:

- `codexCategory`
- `codexTitle`

Supported categories:

- `CHARACTER`
- `VOICE`
- `PLOT`
- `WORLD`
- `TIMELINE`
- `CANON`
- `NOTES`

Promotion behavior:

1. Use author override category/title if supplied.
2. Otherwise use AI-suggested category/title.
3. Otherwise fall back safely to `NOTES` and a truncated recommendation or `Untitled`.
4. Create the Codex scene/entry.
5. Store promoted target ID on the recommendation.
6. Mark recommendation `PROMOTED` so it leaves the active list.

A future Codex draft/suggestion layer should be considered before AI findings become canonical knowledge automatically.

## Prompt/versioning policy

Every meaningful behavior change in prompt output should bump the prompt version.

Known versions:

| Version              | Purpose                                                      |
| -------------------- | ------------------------------------------------------------ |
| `chapter-review-v1`  | Initial structured chapter review recommendations.           |
| `chapter-review-v2`  | Added `anchorText` for click-to-scroll.                      |
| `chapter-review-v3`  | Scope-aware wording (chapter/scene), category list substitution. |
| `chapter-review-v4`  | Form/functional split: editorial persona externalized as author-editable form block; constant functional block owns the JSON output contract. Form overridable at book → project → user → system scopes. Review provenance (`form_scope`, `form_instructions`) stamped on each `ai_review`. |
| `chapter-summary-v1` | One-paragraph chapter summary (free text).                   |
| book-summary-v1      | Whole-book synopsis built from chapter summaries, ≤1000 words |



## Form/functional architecture

The system prompt is assembled as `form + "\n\n" + functional`:

- **Form** — editorial persona and behavioral constraints. Author-editable at four scopes with single-block selection (no inheritance): `book → project → user global → system default`. The system default is `AiFormInstructionsDefaults.SYSTEM_DEFAULT` (a Java constant, uneditable by construction). Each review records the exact form text and its source scope as immutable provenance.
- **Functional** — the JSON output contract NovelKMS consumes (field requirements, severity/codex enums, anchorText spec, response shape, empty-array fallback). Scope-aware via `%unit%`/`%categories%` substitution. Non-editable at any scope; lives as `functionalBlock()` in `OpenAiProvider`.

The form block is scope-agnostic by design: it never names "chapter" or "scene." All scope-awareness belongs to the functional block, so the author can edit form text freely without breaking parsing.

Resolution runs in `AiReviewService.execute()` via `AiFormInstructionsDao.resolveForReview(userId, projectId, bookId)`.

## Book Review Workflow

Part of the approach to AI review of the whole book is the judicious use of memory documents. The models struggle and use excessive resources if the entire book is reviewed at once. For each chapter, we use a memory document to summarize previous chapters to provide context for the current review. The memory document is aggregated into a single artifact to provide as input with the current chapter (or scene in chapter) to review. We need to automate this workflow in NovelKMS. There is a memory document template to standardize the summary of each chapter. My thinking is that generating the memory document for a chapter is an explicit activity, it does not rely on any other AI review artifacts. When a chapter (or a scene in a chapter) is selected for AI Review, NovelKMS will aggregate all previous chapter memory docs into a single sequential doc, and feed it to the AI along with the chapter/scene to be reviewed. I want the AI to warn on the timestamps of the memory documents - they should be sequential. For example, I may have run a review on chapters 1-5, then a month later restarted the review cycle, done chapter 1-3, and inadvertently skipped 4. If I select to review chapter 5, the app should pop up a warning that the ch4 review is outdated.

## Chapter & book summaries

A separate AI artifact family from memory documents. Memory documents are structured continuity context fed into a chapter review ("story so far"); summaries are reader-facing synopses. Regenerating one never touches the other.

- **Chapter summary** — one human-readable paragraph per chapter, generated from the chapter prose (`chapter-summary-v1`), optionally hand-edited (`EDITED`). One per chapter, overwrite on regenerate. Created/edited/cleared from the chapter nav context menu.
- **Book summary** — one synopsis per book, ≤ ~1000 words (`book-summary-v1`), generated **entirely from the chapter summaries** in book order, never the manuscript prose (a full book is too large to summarize reliably in one bite). One per book, overwrite on regenerate, optionally hand-edited.
- **Aggregated view** — right-click a book → "View chapter summaries…" shows the read-only chapter summaries in book order, each with a staleness chip, above the book-summary panel. Per-chapter state is MISSING / STALE_CONTENT / OK (no OUT_OF_SEQUENCE — summaries are independent).
- **Coverage gate** — generating the book summary warns first (`PreBookSummaryDialog`) if any chapter summary is missing or stale, offering to generate the flagged ones in book order before proceeding. The backend proceeds on partial coverage and hard-fails only when no chapter has a summary.
- **Prompts are free-text** (no JSON contract) and **fixed** for v1 — the four-scope template mechanism used by memory/form instructions was deferred.

## Memory documents — UI surface

- **api/chapterMemory.js** — get (404 → null), generate, save (edit), remove,
  bookStatus. **api/memoryTemplate.js** — get/save/remove for global/project/book.
- **hooks/useChapterMemory.js** — useChapterMemory(chapterId),
  useChapterMemoryStatus(bookId), and generate/save/delete mutations keyed by
  `{ chapterId, bookId }`. **hooks/useMemoryTemplate.js** — clone of
  useAiFormInstructions.
- **components/ai/ChapterMemoryEditor.jsx** — the single shared editor (rail tab
  + nav dialog). **MemoryDocDialog.jsx** — nav entry wrapper.
  **PreReviewMemoryDialog.jsx** — flagged-preceding warning + sequential
  regenerate. **memoryStatus.js** — MEMORY_STATE, isFlagged, stateColor/Label/
  Explanation, formatTime. **MemoryTemplateEditor.jsx** — template editor.

The Memory tab and nav dialog both read at most one chapter's document; only the
pre-review path consumes the book-wide status list, which the backend returns in
linear book order so "preceding" is a simple array slice.

## One-time author guidance (V26)

All four generation flows — chapter/scene review, memory-document generation,
chapter-summary generation, and book-summary generation — accept an optional
free-text **guidance** note supplied at the moment of generation. It exists for
the case where the author needs to steer or correct a single run ("the letter
in this chapter is canonically a forgery") without permanently changing the
editorial form instructions or a memory/summary template.

Deliberately **not** the same mechanism as:

- The four-scope form/template override cascades (`ai_form_instructions`,
  `memory_template`) — those are persistent personas/structures; guidance is a
  one-off addendum to a single call.
- The dormant `ReviewRequest.referenceContext` path reserved for pinned
  Codex canon/voice entries — that remains a separate, later increment for
  durable canonical facts the author wants surfaced automatically. Guidance is
  for steering a single generation, not for recording canon.

**Storage and provenance.** A nullable `user_guidance TEXT` column was added to
`ai_review`, `chapter_memory`, `chapter_summary`, and `book_summary` (V26,
identical in both dialects). Whatever guidance produced an artifact is stamped
on it, mirroring how `ai_review` already stamps `form_scope`/`form_instructions`.
Hand-edits (`source = 'EDITED'`) do not touch the column — it reflects the last
*generation*, not the current text.

**Prompt placement.** Guidance is appended to the *user* message (never the
system prompt), as a clearly fenced block — "Additional guidance from the
author for this generation only — follow it, but it is not material to
review/summarize" — positioned closest to the content it concerns: after any
reference/prior-context blocks in a review, after the chapter/book label and
before the prose/summaries being processed for the other three. This keeps the
form/functional and template system prompts byte-identical regardless of
whether guidance is present, and keeps guidance from being mistaken for
reviewable material.

**Frontend behavior.** Each of the four generation surfaces (`ReviewRail`,
`ChapterMemoryEditor`, `ChapterSummaryEditor`, `BookSummaryDialog`) shows a
"Guidance for this generation/run (optional)" field directly above its
generate/run action. The field pre-fills from the guidance that produced the
*current* artifact (or the most recent review, for the review flow, since
reviews are an append-only history rather than one-per-parent) and is **not**
cleared after a successful run — the author can re-run with the same note,
tweak it, or clear it explicitly. This supports the iterative "run, read,
clarify, re-run" loop without retyping.

**Out of scope for V26 (by design):** the sequential batch-regenerate paths
(`PreReviewMemoryDialog`'s "regenerate flagged & review", `PreBookSummaryDialog`'s
"fill gaps") and the nav context menu's one-click quick-generate actions do not
surface a guidance field — a single free-text note doesn't map cleanly onto a
multi-chapter batch run.

**Prompt versions bumped** (output JSON contract unchanged for review; the
other three remain free-text): `chapter-review-v6`, `memory-v2`,
`chapter-summary-v2`, `book-summary-v2`.

## Memory/summary editing surface (V27)

Editing moved from modal dialogs into the document's own nav-tree leaf, opened in EditorPanel for full rich-text editing (headings, lists, blockquotes — the same markup scenes support). Each manuscript chapter has two fixed bottom leaves, *Memory* and *Summary*; each book has one, *Summary*. None are draggable, exported, or counted toward word totals. Content storage moved from plain text to authored HTML; `AiReviewService` strips it back to plain text before it reaches a prompt, so review/book-summary generation is unaffected.

Regenerating warns first whenever content already exists; a first-ever Generate skips straight to the existing continuity (`PreReviewMemoryDialog`) / coverage (`PreBookSummaryDialog`) gates. One-time guidance and the staleness chip live in EditorPanel's toolbar in this mode.

The ReviewRail Memory tab and the book "View chapter summaries…" dialog remain as read-only peek surfaces (Generate/Regenerate/guidance kept, inline editing replaced by an "Edit in document" link). The standalone nav-triggered `MemoryDocDialog`/`ChapterSummaryDialog`/`ChapterSummaryEditor` modals are removed.

## Non-goals for the current AI slice

- General chat interface.
- Automatic manuscript rewriting.
- AI-generated prose insertion.
- Streaming chat UX.
- Autonomous agents.
- Multi-chapter/book orchestration without async execution.
- Provider billing/subscription model.
- Cost reporting, except as a possible future enhancement.

## Near-term AI priorities

1. Add a deliberate Future/deferred findings view.
2. Add selected Codex/character/canon/timeline context to the prompt (Phase C per-chapter memory artifact).
3. Consider a Codex draft/suggestion layer.
4. Move long-running review workflows to async execution before part/book reviews.
5. Preserve prompt versioning for every output-shape change.
