# NovelKMS Architecture and Design Decisions by Phase

This document captures durable architectural decisions and key lessons. It is intentionally concise; it is not a transcript of every implementation session.

## Phase 0 — Product and architecture foundation

### Product vision

NovelKMS is a novel knowledge-management system with a manuscript editor attached. Manuscript text, project metadata, Codex entries, templates, styles, AI review history, and future character/canon/timeline entities are intended to be first-class project data.

### Initial architecture

- Maven multi-module project: backend, frontend, distro.
- Dropwizard backend, React frontend, shaded JAR distribution.
- H2 for local development; PostgreSQL for hosted deployment.
- Explicit JDBC DAO layer rather than JDBI/ORM.
- Scene-level persistence is the central manuscript design choice.

### Core hierarchy

```text
Project -> Book -> Part -> Chapter -> Scene
```

Scenes are the stable editing and persistence unit. Higher-level editing modes display aggregates while saving back into scene records.

## Phase 1 — Manuscript authoring foundation

### Ordering and navigation

- `display_order` drives ordering.
- Reorder endpoints support chapters and scenes.
- Later drag-and-drop generalized this to parts, chapters, and scenes, including cross-container moves.
- Nav tree selection must carry enough parent context (`projectId`, `bookId`, `partId`, `chapterId`, `sceneId`) so tools and properties panels operate on the correct scope.

### Parts and chapter numbering

- Parts are real manuscript containers.
- Direct-book chapters and part-contained chapters share one book-level order.
- Chapter and part numbers are computed dynamically and are never stored.
- Blank titles intentionally render as `Chapter N` or `Part I`.
- Chapter reset numbering uses a two-stage SQL window pattern: running reset group, then row number within that group.

### Single and aggregate editing

- Scene selected: edit one scene.
- Chapter selected: edit all scenes in the chapter.
- Part selected: edit all chapters/scenes in the part.
- Book selected: edit/display the full manuscript scope.

Aggregate editing preserves scene IDs and saves each scene independently. Protected structural nodes mark scene boundaries. Autosave fails closed if the expected scene boundary sequence is corrupted.

### Rich text editor decisions

- TipTap is the editor foundation.
- TipTap packages must remain version-aligned.
- Paragraph style keys are semantic and resolve through style definitions.
- Inline formatting is separate from paragraph styles.
- Base64 in-editor images were accepted for simplicity, with the known tradeoff that image-heavy scenes can become large.
- TipTap node views containing JSX must use `.jsx` files.

### Templates and page layout

- Cover and part templates use token atoms such as `TITLE`, `SUBTITLE`, `AUTHOR_FULL_NAME`, `WORDS`, `PART_NUMBER`, and related fields.
- Global templates are lazily seeded; book templates are copy-on-write overrides.
- Template preview substitutes token values but does not mutate stored template content.
- Page layout mode is a drafting preview, not a full pagination/typesetting engine.

### Import/export

- DOCX import parses Word structure into Book/Part/Chapter/Scene records.
- DOCX export builds Word documents from the stored manuscript hierarchy and templates.
- Import/export code must preserve scene boundaries and avoid flattening the manuscript into one monolithic blob.
- Markdown and ePub remain separate export/import tracks; ePub menu wiring needs current verification.

### Search

- Editor search is frontend/TipTap decoration-based.
- Search navigation must not mutate manuscript selection.
- Highlights are transient decorations, not stored marks.
- Search text nodes, not raw HTML.

### HTTP response code convention

NovelKMS now reserves `404 Not Found` for configuration/routing errors rather than ordinary data absence. If a valid endpoint is reached but the requested entity does not exist, resources should return a non-404 response.

General convention:

- `204 No Content` means the request was valid, but there is no entity/body to return.
- `400 Bad Request` is used when the client needs a response body describing an invalid request or data-domain problem.
- `404 Not Found` should indicate a misconfigured route, missing frontend/API wiring, or an endpoint that should not exist from the client’s perspective.

DELETE convention:

- `200 OK` means the target existed and the delete/soft-delete operation succeeded.
- `204 No Content` means the target did not exist, but the delete request is idempotently complete.

This keeps frontend behavior simple: missing data is not treated as an app/config failure, and DELETE calls can treat both `200` and `204` as successful terminal outcomes.

## Phase 1 — Key implementation lessons

- H2 and PostgreSQL migrations sometimes require dialect-specific SQL.
- H2 requires one `ALTER TABLE ... ADD COLUMN` per column in cases where PostgreSQL allows multi-column statements.
- PostgreSQL JDBC may return `NUMERIC`/`DECIMAL` as `BigDecimal`; avoid `rs.getObject(column, Double.class)` for portable numeric mapping.
- MUI disabled buttons inside `Tooltip` need a wrapper element.
- Avoid `useEffect(() => setState(...))` for derived UI state; derive during render or remount subtrees with a key.
- Update DTOs should use boxed types for required toggles so omitted values fail loudly instead of defaulting silently.
- Every update path must echo unchanged persisted fields or risk clobbering them.
- Cache invalidation must match the blast radius of the change, not just the edited entity.

## Phase 2 — Authentication, ownership, and multi-user readiness

The project evolved from single-user local tooling toward a hosted service model.

Design principles:

- User-owned entities must be scoped to the authenticated user.
- Paths covered by tenant/entity filters can use path-derived authorization.
- Resources whose path contains only their own UUID, such as credentials, reviews, and trash batches, must enforce ownership inside the resource/DAO.
- Secrets stay out of Git, out of the image, and out of JSON responses.

## Phase 2 — AI review framework

### Design principle

AI is an editorial assistant, not a manuscript generator. It critiques and organizes revision work; it does not modify the manuscript automatically.

### BYOK model

- Users bring provider credentials.
- Credentials are per-user and encrypted at rest.
- OpenAI is implemented first behind a provider abstraction.
- The app avoids a hosted AI billing model during validation.

### Review artifact model

- Running a review creates an immutable `ai_review` row.
- Recommendations are stored as child rows.
- Provider failures can be stored as failed review artifacts.
- Recommendation lifecycle states currently include `OPEN`, `ACCEPTED`, `REJECTED`, `FUTURE`, `DELETED`, and `PROMOTED`.

### Review rail

AI review UI moved from the Properties panel to a collapsible editor rail. This keeps manuscript editing primary while letting the author triage review findings beside the text.

Prompt version `chapter-review-v2` added `anchorText`, enabling click-to-scroll and temporary passage highlighting.

### Codex promotion

AI recommendations can be promoted into Codex entries. Promotion is distinct from deletion: a promoted recommendation leaves the active review queue but remains auditable as acted-on AI output.

## Phase 2 — Trash

Trash is a per-user top-level surface. Delete means soft-delete for supported root types; purge is the irreversible operation.

Supported roots include projects, books, chapters, scenes, codex categories/entries, and AI reviews. Parts are intentionally excluded because deleting a part promotes its chapters to direct-book children, making restore semantics unclear.

Design decisions:

- Root-only stamping: only the deleted root receives `deleted_at` and `deleted_batch_id`.
- Descendants are hidden transitively through parent filters.
- Aggregate queries and numbering CTEs must filter deleted rows at every joined level.
- Restore appends to the end of the parent and de-duplicates non-blank titles.
- Purge performs real deletes and sweeps orphaned trash-batch rows.

## Phase 3 — Settings restructure and AI form/functional split

### AI form/functional split (V20)

The AI review system prompt was a single fused block in `OpenAiProvider.systemPrompt()`. It is now split into two parts assembled as `form + "\n\n" + functional`:

- **Functional** (constant, non-editable): the JSON output contract NovelKMS consumes — field requirements, severity/codex enums, anchorText spec, the exact response shape, empty-array fallback. Owns all scope-awareness (`%unit%`, `%categories%`). Lives as `functionalBlock()` in `OpenAiProvider`.
- **Form** (author-editable): the editorial persona and behavioral constraints. Independently overridable at four scopes with single-block selection (no inheritance, no concatenation): `book → project → user global → system default`. The system default is a Java constant (`AiFormInstructionsDefaults.SYSTEM_DEFAULT`); the user global is `ai_form_global(user_id PK)`; project and book are nullable `ai_form_instructions` columns.

Resolution runs in `AiReviewService.execute()`, which already holds `userId`, `projectId`, and `bookId`. The resolved form string flows into `ReviewRequest.formInstructions` and then into the provider.

Each `ai_review` records immutable provenance: `form_scope` (BOOK/PROJECT/USER/SYSTEM) and `form_instructions` (the exact form text used), so the artifact stays faithful even if the user later edits their global.

Prompt version bumped to `chapter-review-v4`.

`AiFormInstructionsResource` endpoints mirror `EditorSettingsResource`: `GET/PUT/DELETE /ai-form-instructions/global`, `/projects/{id}/ai-form-instructions`, `/books/{id}/ai-form-instructions`. The `…/global` path is current-user-scoped via `CurrentUser`; project/book segments are tenant-authorized. Each GET returns `{ scope, instructions, hasOwnOverride }` for dialog pre-population and override toggle state.

### Uniform settings scope model (V21–V22)

Three categories of settings were brought under a uniform scope model so the frontend can present them as a single three-tab context dialog with per-tab override toggles:

| Category                                                    | Resolution                     | Storage                                        | Live editor?             |
| ----------------------------------------------------------- | ------------------------------ | ---------------------------------------------- | ------------------------ |
| Document (font, line height, indents, spacing, scene break) | BOOK → PROJECT → USER → SYSTEM | `editor_settings` table (V17 + V21 BOOK scope) | Yes                      |
| Page layout (paper size, margins, enabled flag)             | BOOK → PROJECT → SYSTEM        | `page_layout` table (V22, typed columns)       | No — export/preview only |
| AI form instructions                                        | BOOK → PROJECT → USER → SYSTEM | columns + `ai_form_global` (V20)               | N/A                      |

**V21** added a `book_id` column + FK + unique index to `editor_settings` and extended the DAO/resource with `resolveBook(userId, bookId)` = BOOK → PROJECT → USER → SYSTEM, plus `upsertBook`/`deleteBook` and `GET/PUT/DELETE /books/{id}/editor-settings`.

**V22** moved page layout from 8 physical columns on `book` to a dedicated `page_layout` table with typed columns (not JSON). The migration copies each customized book's columns into a BOOK row (only where values differ from the factory default), then drops the 8 `book` columns. `BookDao.update` became metadata-only. `ExportService` now takes `PageLayoutDao` as a dependency, resolves once per export via `resolveBook(bookId)`, and threads the `PageLayout` through all helpers (whose getter names were preserved to minimize body changes). `PageLayoutDefaults` seeds the lazily-created SYSTEM row (Letter, 1in/1.25in margins). `BookResource.UpdateRequest` temporarily accepts but ignores the former page-layout fields until the frontend stops sending them.

### Frontend context dialog

The AppBar gear opens a menu: "Book/Project Settings" (context dialog, scope by current selection) and "Global Defaults…" (the existing global dialog). The context `EditorSettingsDialog` has three tabs (Document / Page Layout / AI), each wrapped in a shared `OverrideShell`:

- **Toggle off** = inherit from the next level up; fields shown read-only with a note naming the source.
- **Toggle on** = creates a copy-on-write override seeded from the inherited value; fields become editable; Save persists.
- **Toggle off again** = deletes the override (reverts to inherited).

Each tab body remounts on scope change via key (`id:serverScope`) — no `useEffect→setState`. The Page Layout tab carries a note that size/margins are export-only.

Data layer: `pageLayout` api + hook (project/book), `editorSettings` api/hook extended with book scope, and `aiFormInstructions` api/hook (from V20). All write mutations invalidate the whole query prefix since a project-level change can alter what its books resolve to.

### Cleanup completed in this phase

- `ChapterReviewRequest.java` deleted (dead since the `ReviewRequest` record replaced it).
- `ReviewToggleButton` import and usage removed from `App.jsx` (component was already unused since V19).

## Deployment architecture

### Current hosted model

- Fedora media server.
- Rootless Podman Quadlet/systemd user services.
- PostgreSQL 17 container with persistent Podman volume.
- NovelKMS application container.
- Caddy reverse-proxy container.
- Private Podman network for service-to-service traffic.
- Caddy is the only public-facing service.

### Database migration

The hosted deployment moved from H2 to PostgreSQL. The authoritative data source for migration was the local Eclipse H2 database, not the mostly empty server H2 database.

Migration lessons:

- Flyway should initialize schema before data copy.
- The migrator should copy only application tables and exclude `flyway_schema_history`.
- Row counts must be verified after migration.
- PostgreSQL-specific runtime mapper issues may appear even if H2 worked.

### Backup architecture

- PostgreSQL backups should use `pg_dump`/`pg_restore`, not raw live-volume sync.
- Config, secrets, Caddy state, Quadlet definitions, and database dumps should be archived together.
- Completed archives should be atomically moved into Dropbox after verification.
- Restore tests are mandatory before backups can be trusted.

## Current architectural watchlist

- Finish/repair ePub export menu and endpoint wiring.
- Move project/document settings out of localStorage.
- Add style-editor UI.
- Add deferred AI findings view.
- Add selected Codex/context to AI prompts.
- Consider a Codex draft/suggestion layer.
- Convert long AI workflows to async execution.
- Periodically profile full-book editor/search performance.
- Keep Caddy independent enough that app restarts do not stop the public reverse proxy.
- Book-summary `stale` currently treats any newer chapter summary as staleness (prompts a rebuild after touching any chapter summary); revisit if too eager.
- Async execution for book-summary generation if large books make the synchronous call slow (shares the review-async watchlist item).

**Scene-level AI Review (V19).** Chapter and scene reviews are one artifact differing only in scope; scope is *derived*, never stored: `chapter_id NULL → BOOK` (reserved), else `scene_id NOT NULL → SCENE`, else `CHAPTER` (`AiReview.deriveScope`). 

V19 adds `ai_review.scene_id` (+ index) and relaxes `chapter_id` to nullable as forward-prep for a future book scope — no book-scope code yet; every insert still sets `chapter_id`. 

A scene review records its parent chapter in `chapter_id` so it groups under that chapter's AI workflow via the existing `ix_ai_review_chapter` listing. `ChapterReviewRequest`→`ReviewRequest` (adds `scopeWord`/`unitLabel`); `AiProvider.reviewChapter`→`review`. 

Prompt `chapter-review-v3`: scope-aware ("chapter"/"scene") wording, identical output JSON contract to v2. 

New endpoint `POST /ai/reviews/scenes/{sceneId}` (tenant-covered via the `scenes/{uuid}` segment). 

Frontend: `ReviewRail` takes `sceneId`; scene selection runs/filters to that scene, chapter selection shows all reviews under the chapter tagged by origin; per-review history model retained.

### V24 — Chapter Memory Documents (frontend)

The frontend surfaces the per-chapter memory document built in the backend pass.

- **Memory tab in ReviewRail.** A fourth tab ("Memory", beside Active/Resolved/
  History) renders the open chapter's document via the shared `ChapterMemoryEditor`:
  a staleness chip (Up to date / No memory / Stale / Out of sequence), a
  Generate/Regenerate action, and inline hand-editing (Save marks the document
  EDITED). At scene scope it shows the parent chapter's document with a "memory is
  per chapter" note. ReviewRail now takes a `bookId` prop (supplied by EditorPanel,
  where it was already in scope) to drive the staleness query.
- **Pre-review warning.** Chapter-scope review runs are intercepted by
  `PreReviewMemoryDialog` only when a *preceding* chapter's document is flagged
  (MISSING / STALE_CONTENT / OUT_OF_SEQUENCE) — i.e. the "story so far" context
  would be incomplete. The dialog offers Cancel, Review anyway, or Regenerate
  flagged & review (regenerates the flagged preceding documents sequentially in
  book order, with progress, and proceeds to the review only if all succeed).
  "Preceding" is derived from array position in the book-ordered status list, so
  no sequence field is needed client-side. Scene reviews are never gated.
- **Nav context menu (chapter nodes).** "Generate memory document" fires
  generation inline; "Edit memory document…" opens `MemoryDocDialog`, a standalone
  editor that works for any chapter (the rail tab only exists for the open
  chapter). Both share one inner `ChapterMemoryEditor`.
- **Template editor.** `MemoryTemplateEditor` clones `AiFormInstructionsEditor`
  exactly (single-block selection book → project → user global → system default,
  `content` field) and is mounted at the same three scopes: Settings → AI (global),
  Book Properties, Project Properties.

Decisions: per-row staleness badge on ChapterItem was deferred (it would require
the nav to hold book-wide status for every chapter; staleness is already surfaced
where it's actionable — the rail tab and the pre-review dialog). No global toast
infrastructure existed, so the inline "Generate" feedback uses a self-contained
MUI Snackbar in the nav provider rather than introducing one.

Cache keys: `CHAPTER_MEMORY_KEYS` ({ doc, status }) and `MEMORY_TEMPLATE_KEYS`
(prefix-invalidated like AI form instructions, since editing the global changes
what every scope resolves to). Generate/edit/delete invalidate both the chapter
doc and the book status (a fresh generation can re-order the staleness picture).

### V25 — Book / chapter summaries

A new AI artifact family, deliberately **independent of memory documents** — no shared rows, prompts, or staleness semantics, only a shared one-per-parent shape and the same book-wide numbering CTE. Backend built and statically verified first, then frontend.

**Schema (V25, identical in both dialects).** `chapter_summary` (one per chapter, `chapter_id` UNIQUE, `source` AI|EDITED, prompt/model/generated_at) and `book_summary` (one per book, `book_id` UNIQUE, plus `word_count`). Both `ON DELETE CASCADE`; trashed parents keep their rows (excluded by the `deleted_at` read filters) and reappear on restore — same trash interaction as `chapter_memory`.

**Generation paths (free-text, no JSON contract).** `chapter-summary-v1` turns one chapter's prose into a single readable paragraph. `book-summary-v1` is built **entirely from the chapter summaries** (assembled in linear book order under `Chapter N: Title` headings), never the manuscript, with a hard 1000-word ceiling threaded into the system prompt. Both reuse `OpenAiProvider.postForContent` with no `response_format` and no token caps (reasoning-model safe), exactly like memory generation. Two new provider methods on `AiProvider` (`generateChapterSummary`, `generateBookSummary`) returning a shared `SummaryResult`.

**Aggregation & staleness.** `ChapterSummaryDao.bookChapterSummaries(bookId)` clones the memory DAO's CTE (book-wide numbering + absolute `seq`, codex chapters and trashed rows excluded, left-join the one-per-chapter summary and each chapter's `MAX(scene.updated_at)`). `AiReviewService` derives per-chapter state MISSING / STALE_CONTENT / OK — no OUT_OF_SEQUENCE, since summaries are independent paragraphs. `bookSummaryStatus` reports book-summary staleness (stale iff a chapter lacks a summary, a chapter summary drifted, or any chapter summary postdates the book summary) plus coverage counts. The single aggregated read drives both the read-only "View chapter summaries" dialog and the pre-book-summary coverage warning. Book-summary generation hard-fails only when *no* chapter has a summary; partial coverage proceeds (the pre-dialog offers to fill gaps first).

**Endpoints (one `SummaryResource`, all tenant-covered via `chapters/{id}` / `books/{id}`).** Chapter CRUD `/ai/summary/chapters/{id}`; book CRUD `/ai/summary/books/{id}`; `GET /books/{id}/chapter-summaries` (aggregate); `GET /books/{id}/book-summary-status`. Edits mark `EDITED` and re-count words. `AiReviewService` gained two DAOs (constructor now 13-arg) and the summary methods; wired/bound/registered in `NovelKmsServer`.

**Frontend.** `api/summary.js` + `hooks/useSummary.js` (cache keys `SUMMARY_KEYS` = { chapterDoc, bookDoc, chapters, bookStatus }; a chapter-summary change invalidates the chapter doc + the book's aggregate + book status, since coverage feeds book-summary staleness). `summaryStatus.js` mirrors `memoryStatus.js` minus OUT_OF_SEQUENCE. `ChapterSummaryEditor` (shared, no preceding-chain gating) + `ChapterSummaryDialog` (nav). `BookSummaryDialog` is the book right-click target: book-summary panel (chip, coverage line, word count, generate/regenerate/edit) over the read-only aggregated chapter summaries. `PreBookSummaryDialog` warns on missing/stale coverage and can generate the flagged chapter summaries sequentially before proceeding. `NavContextMenu` gained book "View chapter summaries…" and chapter "Generate / Edit… / Clear chapter summary", with a self-contained Snackbar (same pattern as the memory nav feedback).

**Decisions.** Separate artifact family (confirmed) rather than reusing memory docs — different output shape and decoupled regeneration. Summary-prompt template editors deferred; v1 uses fixed system-default prompts. The read-only aggregate is the single source for both the view dialog and the coverage gate, so ordering lives in one place.

### V26 — One-time author guidance for AI generation

A lightweight addition layered on top of all four existing generation flows (review, memory, chapter summary, book summary), not a new artifact family. Lets the author supply free-text guidance for a single generation call — distinct from the persistent form/template override cascades (V20/V24) and
from the still-dormant `referenceContext` Codex-pinning path (forward-prep in V19, not yet implemented). Codex integration was explicitly deferred again here; this increment is purely the one-time-note mechanism.

**Schema.** `V26` adds a nullable `user_guidance TEXT` column to `ai_review`, `chapter_memory`, `chapter_summary`, and `book_summary` (one `ALTER TABLE` per column, identical in both dialects, per the established rule). Stamped at generation time as immutable provenance; untouched by hand-edits.

**Backend threading.** Each of the four `ai` package records (`ReviewRequest`, `MemoryRequest`, `SummaryRequest`, `BookSummaryRequest`) gained a `userGuidance` field. `AiReviewService`'s four generation entry points (`runChapterReview`, `runSceneReview`, `generateChapterMemory`,
`generateChapterSummary`, `generateBookSummary` — `ReviewTarget` also carries it) gained a `userGuidance` parameter, normalized through a new `blankToNull` helper so blank input and "no guidance" are indistinguishable downstream. The three resources' `RunRequest`/`GenerateRequest` DTOs gained a
matching field. `OpenAiProvider` appends guidance to the **user** message only (never the system/form/functional/template blocks) as a clearly fenced addendum, positioned closest to the content it concerns — after any reference/prior-context blocks in `userPrompt(ReviewRequest)`, and between the
chapter/book label and the prose/summaries in the other three builders.

**Prompt versions bumped:** `chapter-review-v6` (was v5), `memory-v2` (was v1), `chapter-summary-v2` (was v1), `book-summary-v2` (was v1) — the review JSON output contract is unchanged; the other three remain free-text.

**Frontend.** `ReviewRail`, `ChapterMemoryEditor`, `ChapterSummaryEditor`, and `BookSummaryDialog` each carry a local `guidance` text field above their generate/run action. Pre-fill uses the same "derive during render, track a previous key" pattern established for `DocSettingsPopover`-style state: a
`guidanceInitKey` combining the scope/chapter/book id with the underlying query's loading state, so the field re-syncs once on scope switch and once when the source data finishes loading, without an `useEffect`. The field is deliberately **not** cleared after a successful generation, so guidance can be
repeated or refined across runs; a "Clear guidance" button is shown when non-empty. Sequential batch-regenerate flows (`PreReviewMemoryDialog`, `PreBookSummaryDialog`) and the nav context menu's
one-click quick-generate actions were left out of scope — they don't map cleanly onto a single free-text note.

### V27 — Memory/Summary documents as nav-tree leaves

Moves memory-document and chapter/book-summary editing out of modal dialogs and into EditorPanel, using the document's own nav node — the same architectural slot the V4 template "third mode" already established.

**Nav tree.** Two new fixed leaf components: `ChapterAiDocItem` (Memory/Summary, rendered after a chapter's scene `SortableContext`, never a member of it) and `BookSummaryItem` (rendered after `CodexSection`, true bottom of a book's children). Both italic, alternate icons (`PsychologyOutlinedIcon` for Memory, `SummarizeOutlinedIcon` for Summary) distinct from `ArticleIcon`/`TheatersIcon`. Not draggable, no rename, no own context menu.

**Selection state.** `selection.aiDocType` (`'memory'|'chapterSummary'|'bookSummary'|null`) added to `EMPTY_SELECTION`; `setSelection`'s transient-mode cleanup now strips it too, so any ordinary nav click clears it automatically — no separate `selectAiDoc` helper needed, since these nodes already carry full chapter/book parent context the way Scene/Chapter/Part/Book do.

**EditorPanel.** New top-priority `aiDocMode`, gating every other mode off. Mirrors the template-mode `loadedTemplateKeyRef`/`templateKey()` pattern exactly via a new `aiDocKey(type, parentId, doc)` keyed on `generatedAt` (bumped by both generation and hand-edit saves per the existing DAO contract). Autosave goes through the stable `chapterMemoryApi`/`summaryApi` modules directly inside `scheduleSave` — not the TanStack mutation hooks, which aren't safe to call from a `useCallback` with a narrow dependency array — manually mirroring each hook's own cache invalidation. Generate/Regenerate is a separate explicit action via the mutation hooks, safe there since it's triggered directly from a click handler.

**Storage format: plain text → authored HTML.** `chapter_memory.content`/`chapter_summary.content`/`book_summary.content` remain `TEXT` (no migration) but now hold TipTap HTML. `AiReviewService.assemblePriorContext()` and `assembleChapterSummaries()` call the existing `htmlToPlainText()` Jsoup helper before folding content into AI prompts; `editBookSummary()`'s word-count recompute does the same before `countWords()`. Legacy plain-text rows still display and aggregate correctly via that helper's existing raw-text fallback.

**Regeneration warning.** New shared `RegenerateConfirmDialog`, shown whenever Regenerate is clicked and content already exists (skipped on a first-ever Generate). Wired into EditorPanel's toolbar and into the two kept legacy peek surfaces.

**Kept legacy surfaces, redefined.** The ReviewRail "Memory" tab and the book "View chapter summaries…" dialog stay, but inline `TextField` editing is gone — both render content read-only via a new shared `RichTextPreview` (mirrors EditorPanel's own template-preview `dangerouslySetInnerHTML` pattern, same trust boundary) with an "Edit in document" button selecting the nav node. Generate/Regenerate/guidance/staleness are unchanged. The book dialog's dense chapter-summary list renders a stripped plain-text preview instead (new `utils/htmlText.js` → `stripHtmlToText()`).

**Removed.** `MemoryDocDialog`, `ChapterSummaryDialog`, `ChapterSummaryEditor` — superseded by the nav leaf + EditorPanel. The chapter context menu's "Edit memory document…"/"Edit chapter summary…" now select the nav node instead of opening a dialog; Generate/Clear quick actions are unchanged.

**PropertiesPanel.** New `AiDocProperties` block (source, generated-at, model, word count for book summaries, last guidance), routed ahead of the normal router exactly the way `TemplateProperties` already is.
