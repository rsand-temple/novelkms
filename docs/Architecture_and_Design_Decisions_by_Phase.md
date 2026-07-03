- # NovelKMS Architecture and Design Decisions by Phase

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


  ### V28 — Stripe billing and subscription enforcement

  Stripe subscription support was added using Stripe-hosted Checkout and Customer Portal, with NovelKMS retaining a local entitlement table for authorization. Stripe remains the billing system of record; `user_subscription` is the app's access-control snapshot, and `stripe_webhook_event` provides webhook idempotency/audit. Detailed implementation notes live in `README.billing_and_subscriptions.md`.

  **Backend.** New billing config (`stripeSecretKey`, `stripeWebhookSecret`, `stripePriceId`, success/cancel URLs, `enforceSubscriptions` kill switch), `BillingService`, `BillingResource`, `StripeWebhookResource`, `UserSubscriptionDao`, and `StripeWebhookEventDao`. The webhook endpoint is public through both authentication and tenant-authorization filters. Checkout maps Stripe sessions to NovelKMS users through `client_reference_id` and subscription metadata.

  **Entitlements.** Access-granting statuses are `active`, `active_canceling`, `trialing`, and `family`; `past_due` can remain grace-access while still within `current_period_end`. `family` is a manual entitlement override and Stripe events must not demote it. Active scheduled cancellations are normalized to local status `active_canceling`, with `cancel_at`, period end, feedback, comment, reason, and cancellation request timestamp preserved.

  **Frontend.** Added billing API/hooks, a Settings → Billing tab, Stripe success/cancel return pages, and global handling for `402 subscription_required` so blocked users are routed to Billing instead of seeing generic failures.

  ### V29–V31 — Admin role foundation, audit, billing support, and minimal console

  This phase created the first production-oriented admin/support surface. The guiding rule was security and auditability before mutation: build role-aware authorization first, then audit logging, then read-only support visibility, then a single conservative billing mutation.

  **Admin role foundation.** A `user_role` table gives users explicit roles, beginning with `ADMIN`. Authenticated sessions now hydrate roles from `AuthDao.findRolesForUser(...)`. `AuthenticationFilter` creates a `NovelKmsPrincipal`, stores it on the request, and installs a JAX-RS `SecurityContext` whose `isUserInRole(...)` delegates to that principal. `RolesAllowedDynamicFeature` is registered so resources can use `@RolesAllowed(Roles.ADMIN)`. `CurrentUser` gained helpers for reading the principal and role membership while preserving existing user-id access. Tenant and subscription filters explicitly allow admin paths only for admin principals and deny non-admins before tenant checks.

  **Admin audit.** `admin_audit_log` records admin actions with `admin_user_id`, optional `target_user_id`, `action`, `entity_type`, `entity_id`, `old_value`, `new_value`, `reason`, and `created_at`. `AdminAuditDao` supports insert and basic retrieval (`recent`, by target user, by admin user, by id). `AdminAuditResource` exposes read-only audit endpoints under `/api/admin/audit/*`. Admin mutations should treat audit logging as mandatory and should record old/new JSON where practical.

  **Read-only admin user and billing views.** `AdminUserDao` and `AdminUserResource` expose `/api/admin/users` search and `/api/admin/users/{userId}` detail. Search covers email/name/user id/Stripe ids. User detail includes identity, roles, subscription summary, and usage counts. The usage SQL was corrected against the real Flyway schema after tests exposed invalid assumptions about `deleted_at` columns on some tables. `AdminBillingService.billingDetail(...)` and `AdminBillingResource` expose `/api/admin/billing/users/{userId}`, returning subscription state plus computed flags such as `hasAccess`, `familyAccess`, `stripeLinked`, `trialActive`, `canceling`, `paymentProblem`, and `accessReason`.

  **First admin mutation: grant family access.** `AdminBillingService.grantFamilyAccess(...)` checks that the target user is active, captures the old subscription, calls `UserSubscriptionDao.setFamilyAccess(...)`, captures the new subscription, writes `GRANT_FAMILY_ACCESS` to `admin_audit_log`, and returns the updated subscription. The manual `family` entitlement remains a local override that Stripe webhook processing must not demote. Revocation was intentionally deferred because it requires a policy decision: restore last Stripe-derived status, query Stripe live, fall back to no access, or introduce a richer manual-override model.

  **Frontend admin console.** A minimal support console was added at `/admin`, integrated into the existing pathname-based app branching rather than introducing new route structure. The user menu shows “Admin console” only when `/api/auth/status` reports the `ADMIN` role. The console supports user search, selected-user identity details, role/status chips, billing state, usage counts, recent audit entries, and a dialog to grant family access with reason/note. MUI warnings were resolved by using current `slotProps` for `ListItemText`, moving forwarded layout props into `sx`, and keeping all `MenuItem` components inside `Menu`.

  **Testing lessons.** Admin DAO/service tests started with hand-rolled mini schemas, then moved to `NovelKmsTestBase` so Flyway owns schema setup. This exposed real schema mismatches and foreign-key cleanup issues. `NovelKmsTestBase.truncateAll()` now deletes admin audit and role rows before deleting `app_user`. Test fixtures should use unique emails and Stripe ids because the shared H2 database can retain data across methods until explicit truncation.

  **Next admin increments.** The next backend actions should be trial extension and billing diagnostics. Revoke/remove family access should wait until manual override restoration semantics are deliberately designed. Frontend should keep the admin console narrow: support workflows first, not a broad dashboard.

  ## Current architectural watchlist

  - Extend admin billing support: trial extension, revoke-family/manual-override design, webhook diagnostics, plan mapping, and Stripe reconciliation.
  - Add plan mapping from Stripe price IDs to friendly `plan_key` values.
  - Continue Flyway-backed tests for admin billing actions, billing entitlement logic, and Stripe webhook subscription parsing.
  - Consider a scheduled Stripe reconciliation job for local subscription state.
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

  ### V31 — In-app help system

  A frontend-only feature adding a full help center, context-sensitive help buttons, and a static validator — no backend, no migration, no database.

  **Design principles.** The system is optimized for four things: (1) adding a topic is dropping a `.md` file — no registry to edit, (2) cross-linking is a single `[label](#help:topic.id)` link, (3) attaching context help to any control is one JSX element `<HelpButton topic="..." />`, and (4) broken links cannot rot silently because the validator catches them.

  **Content storage.** Help topics are Markdown files bundled at build time under `src/help/content/`, auto-discovered by Vite's `import.meta.glob('./content/**/*.md', { eager: true, query: '?raw' })`. Each file carries YAML-style frontmatter (`id`, `title`, `section`, `order`) and a Markdown body. Section membership comes from frontmatter, not filesystem path, so topics can be flat or organized into subdirectories freely.

  **Markdown rendering.** A zero-dependency renderer (`miniMarkdown.js`) converts Markdown to HTML at render time, supporting headings, bold/italic, inline code, fenced code blocks, links, lists, blockquotes, and horizontal rules. All text is HTML-escaped before structural tags are emitted — the same trust boundary as `RichTextPreview`. The renderer is a single swappable file; replacing it with `marked` or another library requires no other changes.

  **Help registry (`helpRegistry.js`).** Parses frontmatter at import time and exposes `getTopic(id)`, `hasTopic(id)`, `allTopics()`, `getTableOfContents()`, `getDefaultTopicId()`, and `searchTopics(query)`. The table of contents groups topics by their `section` field against the ordered list in `helpSections.js`; unknown sections fall into a trailing "More" group. Search is a case-insensitive filter over title and body, with title hits ranked first.

  **HelpProvider / useHelp().** A context provider mounted in `main.jsx` (alongside `AuthProvider`) managing modal open/close state, the current topic id, and a back-history stack. Any component anywhere can call `useHelp().openHelp('topic.id')` without prop-drilling — same pattern as `SearchProvider` and `ReviewProvider`. The provider includes a soft fallback so a stray `HelpButton` outside the provider no-ops instead of crashing.

  **HelpCenter (modal).** A centered `Dialog` (confirmed D2) with a 280px left TOC pane (sections → topics, with live search) and a right content pane rendering the selected topic via `MarkdownView`. In-content `#help:` links are intercepted by event delegation and routed through the provider's `navigate()`, pushing onto the history stack so the Back button retraces the trail. External `https://` links open in a new tab.

  **HelpButton.** A small circular `?` affordance (a styled text glyph, not an icon-font import, so it can never break the Rolldown build on a missing icon file). Renders an `IconButton` that calls `openHelp(topic)`. One line to add anywhere: `<HelpButton topic="ai.review.rail" />`.

  **AppBar integration.** A Help icon button (using the proven `MenuBookIcon`) was added to the top bar between Templates and Settings. It opens the Help Center at the default topic (the welcome page).

  **Wiring reference.** `SettingsDialog` carries a `HelpButton` in its title bar as a working example of dialog-level context help.

  **Validator (`scripts/check-help.mjs`).** A standalone Node script (zero dependencies, no Vite needed) that walks the content directory and source tree. It **fails** on: cross-links to missing topics, `HelpButton topic="..."` or `openHelp('...')` references to missing topics, duplicate topic ids, and files with no `id` frontmatter. It **warns** on topics whose `section` is not declared in `helpSections.js`. Added as `npm run check-help` and intended to run as part of the existing static-verification pass. Confirmed to pass clean and fail correctly on injected bad links.

  **Starter content.** 26 topics across 7 sections (Getting Started, Manuscript & Navigation, Editor & Formatting, AI Review & Summaries, Codex, Import & Export, Account & Billing), all cross-linked. These are seed content covering the full product surface; expanding or editing them is the `.md` authoring loop.

  **Decisions.**

  - D1: Markdown files bundled in the frontend (not DB-backed, not hardcoded JSX).
  - D2: Centered modal (not drawer — right side is already owned by ReviewRail).
  - D3: One component (`HelpButton`) for launch; popover variant deferred.
  - D4: Dotted-string topic ids in frontmatter; `#help:id` cross-link syntax.
  - D5: Zero-dep `miniMarkdown.js` (swappable for `marked` later).
  - D6: Section grouping/ordering in `helpSections.js`; per-topic `section`+`order` in frontmatter.
  - D7: Static validator in `scripts/check-help.mjs`.
  - D8: Client-side search over title + body included.

  ### V32 — Artifacts (per-project file/folder store)

  A per-project non-manuscript file/folder area — the first feature that stores opaque binary content outside PostgreSQL. Manuscript scenes/chapters/templates remain in the database; artifact file bytes live on a host-mounted disk volume keyed by an opaque blob id, so the SQL tree owns names/hierarchy/trash/ordering and the on-disk store owns nothing but bytes.

  **Schema (V32, identical in both dialects).** `artifact_blob` (content-store index: `user_id` for the quota SUM, `sha256` captured for future content-addressed dedup, `storage_key` for the on-disk path) and `artifact_node` (self-referential tree: `parent_id NULL` = virtual project root — no root row; `node_type` FOLDER|FILE; `name`/`name_normalized` for case-preserving/case-insensitive uniqueness; `blob_id` FK → `artifact_blob` for FILE nodes; denormalized `size_bytes`/`content_type` so the Explorer details view and the tree read need no blob join; standard `deleted_at`/`deleted_batch_id` columns for the root-stamping trash pattern). `app_user.artifact_quota_bytes` added as a nullable per-user override (NULL = config default); forward-prep for a future admin "grant more storage" action.

  **Blob store (`ArtifactStorage`).** Uploads stream to a `.staging` file under the root directory (cap enforced mid-stream via `FileTooLargeException`; SHA-256 computed in the same pass), then `commit()` does an atomic same-volume rename into a sharded location (`shard/blobId`). Staging and final share a root so the move is a filesystem rename, not a copy. `discard()` cleans up staged bytes on cap/quota rejection. `delete()` is best-effort post-purge (an unreferenced leftover file is harmless). Blank `storageDir` falls back to a temp directory with a startup warning — the same dev-fallback idiom as the encryption key.

  **Case convention.** Windows-style: `name` stored exactly as authored; `name_normalized = lower(name)` drives case-insensitive sibling uniqueness within a folder. Uniqueness is enforced in the DAO inside the mutating transaction (collision → 409 `name_conflict`), NOT by a DB unique index: H2 cannot express a filtered unique index, and trashed siblings must not block name reuse.

  **Quota model.** Per-user (summed across all their projects via `artifact_blob.user_id`), not per-project. Quota = `COALESCE(app_user.artifact_quota_bytes, config.artifacts.defaultUserQuotaBytes)`. Usage counts trashed-but-not-purged files (trashing frees nothing; only purge does). Two structured error responses: `file_too_large` (400, includes `maxBytes`) and `storage_quota_exceeded` (400, includes `usedBytes`/`quotaBytes`/`fileBytes`).

  **Upload transaction.** Stream → stage (cap check) → quota check → `storage.commit()` → single JDBC transaction (insert `artifact_blob` + insert `artifact_node` on the same connection) → return. On DB failure: rollback connection, then `storage.delete(storageKey)` for the committed-but-unreferenced blob. On cap/quota failure: `storage.discard(staged)`. Two error classes, one cleanup path, no leaked bytes.

  **Trash integration.** Two new root types: `ARTIFACT_FOLDER`, `ARTIFACT_FILE`. `TrashDao.trashArtifactNode` resolves name/type/project/child-count via the project ownership join and calls the shared `doTrash`. `TrashDao.purgeArtifactNode` uses a recursive CTE to collect all blob ids and storage keys before cascade-deleting the node subtree, then batch-deletes the orphaned blob rows, and returns the storage keys so `TrashService` can delete the on-disk bytes after the DB commit. `sweepOrphans` extended with `ARTIFACT_FOLDER`/`ARTIFACT_FILE` clauses. Restore: resolves parent liveness (root-level needs live project, nested needs live parent folder), case-insensitive Windows-style name de-dup via `dedupeArtifactName`, append `display_order`.

  **Tenant authorization.** `TenantAccessDao.ownsArtifactNode` added (joins `artifact_node → project`); folded into `ownsAnyEntity` so the tenant filter's generic JSON-body inspector accepts an artifact `parentId` on `/move` requests without rejecting it as an unknown entity. Project-scoped paths (`/projects/{id}/artifacts/...`) are authorized by the existing `projects/{uuid}` segment check. Node/file-scoped paths (`/artifacts/nodes/{id}/...`, `/artifacts/files/{id}/...`) carry only their own UUID; ownership is enforced in-resource via `requireOwnership`, following the established "UUID-only paths enforce ownership in the resource" rule.

  **Configuration.** New `artifacts:` block in `config.yaml`: `enabled` (true), `storageDir` (env `NOVELKMS_ARTIFACT_DIR`), `maxFileSizeBytes` (50 MB), `defaultUserQuotaBytes` (1 GB). `NovelKmsConfig.Artifacts` nested class with Lombok `@Getter`.

  **Frontend — nav.** `ArtifactsSection` (per-project root node, structural slot after `CodexSection` in `ProjectItem`): fixed, non-draggable, expandable. `ArtifactFolderItem` recursive folder nodes underneath. Files are intentionally NOT in the nav tree — folders only, mirroring the Windows two-pane model. Selecting any artifact node sets `selection.artifactFolderId` (`'root'` | folderId | null), added to `EMPTY_SELECTION` and stripped by `setSelection`'s transient-cleanup exactly like `aiDocType`.

  **Frontend — Explorer.** `ArtifactsPanel` replaces `EditorPanel` in the center pane when `artifactFolderId` is set (branched at the App level so EditorPanel is byte-for-byte untouched). Details table (Name/Type/Size/Modified), breadcrumb, toolbar (Up / New folder / Upload), right-click row menu (Download / Rename / Move to… / Move to Trash), per-user storage usage bar. Its own isolated `DndContext` for drag-a-row-onto-a-folder (and onto the `..` row to move up), fully separate from the manuscript `NavPanel` `DndContext`. Native OS file drag-and-drop with a `useEffect` document-level `dragover`/`drop` prevention (no browser navigation on mis-aimed drops) and a visual drop-zone overlay.

  **Decisions.**

  - D1: Scope = per-project (not global).
  - D2: Single `artifact_node` self-referential tree; no root row.
  - D3: External filesystem blob store (not Postgres LOB/bytea).
  - D4: Case-preserving, case-insensitive uniqueness via DAO check, not DB unique index.
  - D5: Integrated into existing Trash (same root-stamping pattern).
  - D6: Jersey multipart upload (existing `MultiPartFeature` registration).
  - D7: Quota per-user (not per-project).
  - D8: Files not in nav tree; Explorer in center pane with its own DndContext.
  - D9: Designing toward versioning (sha256 captured, artifact_file_version additive).
  - D10: Nav-pane folder drag deferred (additive fast-follow).
  - D11: Native OS file drop handled with document-level prevention + panel-level overlay.
  - D12: Image artifacts get an in-pane read-only preview modal (double-click or right-click "Preview"); image bytes are served from the existing cookie-authenticated `downloadUrl` as an `<img src>`, so no new endpoint. This is the first in-app rendering of an artifact — the "download only" rule now means "no editing/round-trip," not "never displayed." Text (editor) and all other types (download) dispatch unchanged.

  ### V34 — Editorials (per-chapter author-facing AI reading)

  A new AI artifact family: a short editorial reading of one chapter — the model's overall "what do you think?" on tone, genre drift, character arcs, and storyline evolution. Modeled on chapter summaries (V25) + nav-leaf editing (V27), but with one defining difference from every prior AI artifact: **an editorial is never consumed by any other AI function.** It exists purely for the author's edification. It is also distinct from an `ai_review`: an impressionistic overall read, not discrete triageable findings, and it deliberately does not surface spelling/grammar/line-level issues unless egregious ("less is more", ~half a page).

  Note: the real next Flyway number was **V34** — the feature-version labels (V25–V32) had drifted ahead of the actual migration sequence, which sat at V33. This section uses the true migration number.

  **Schema (V34, identical in both dialects).** `chapter_editorial`: one per chapter (`chapter_id` UNIQUE), `content` TEXT (authored TipTap HTML, like memory/summary since V27), `source` AI|EDITED, prompt/model/generated_at, and `user_guidance` — the last included directly in the CREATE (the V26 one-time-guidance column) since this family arrives after V26. `ON DELETE CASCADE`; trashed chapters keep the row (excluded by read filters) and it returns on restore. No `book`/`project` columns, no aggregate table.

  **Context inputs, but no downstream consumption.** When generated, an editorial draws on the **same** context a chapter review does — the preceding chapters' memory documents (`assemblePriorContext`) as "story so far", plus pinned Codex reference entries (`assembleReferenceContext`, `includePinnedContext` defaulting to true exactly as the review does). But nothing reads it back: `AiReviewService.assemblePriorContext()` and `assembleChapterSummaries()` are byte-identical — editorial is never folded into any prompt.

  **Backend.** `ChapterEditorial` model; `ChapterEditorialDao` (single-doc CRUD only — no book-wide numbering CTE, since editorials are never aggregated or gated). New `EditorialRequest`/`EditorialResult`; `AiProvider.generateEditorial`; `OpenAiProvider` gains `EDITORIAL_PROMPT_VERSION = "chapter-editorial-v1"`, a fixed system-default persona wrapper (developmental-editor voice; half-page ceiling; no findings list; no line edits unless egregious; reuses the review's fenced reference/prior-context/guidance blocks in the user message), and `buildEditorialRequestBody`. `AiReviewService` gains `chapterEditorialDao` (constructor now 14-arg) and `generateChapterEditorial` / `getChapterEditorial` / `editChapterEditorial` / `deleteChapterEditorial`. `EditorialResource` exposes `GET/PUT/DELETE/POST /ai/editorial/chapters/{id}`, tenant-covered via the `chapters/{id}` segment; wired/bound/registered in `NovelKmsServer`.

  **Frontend.** `api/editorial.js` + `hooks/useEditorial.js` (`EDITORIAL_KEYS = { doc }`; per-chapter invalidation only — no aggregate/coverage). A third fixed chapter nav leaf via `ChapterAiDocItem` (`RateReviewOutlinedIcon`, distinct from Memory's `PsychologyOutlined` and Summary's `SummarizeOutlined`); `selection.aiDocType` gains `'editorial'` (stripped generically by `setSelection`'s transient cleanup like the others — no App.jsx change). `EditorPanel` slots editorial alongside memory/summary for load/autosave/toolbar-guidance/Generate-Regenerate, but with **no continuity gate and no staleness chip** — purely author-facing, so it shows only the Generated/Edited metadata line. `AiDocProperties` covers it. `NavContextMenu` gains chapter-node Generate editorial / Edit editorial… / Clear editorial (with confirm dialog + self-contained Snackbar), no gate.

  **Decisions.**

  - D1: New artifact family (confirmed) — same one-per-chapter shape as summaries, but decoupled and never consumed downstream.
  - D2: True next Flyway number (V34), read from the migration directories rather than the drifted feature labels.
  - D3: HTML storage + nav-leaf editing (per V27).
  - D4: Same context inputs as a chapter review (preceding memory docs + pinned Codex).
  - D5: Never folded into any prompt — assembly methods byte-identical.
  - D6: `chapter-editorial-v1`, fixed system-default persona; four-scope template editor deferred (matching the summary precedent).
  - D7: One-time guidance (V26) supported.
  - D8: Separate `EditorialResource` rather than folding into `SummaryResource`.
  - D9: `RateReviewOutlinedIcon` for the leaf.
  - D10: No ReviewRail surface — nav leaf + context menu + EditorPanel only.
  - D11: `AiDocProperties` + context-menu items with Snackbar feedback.

  ### Anthropic (Claude) provider — no Flyway migration

  Added `AnthropicProvider` as a second peer implementation of `AiProvider`, alongside the existing `OpenAiProvider`. No schema migration, no new endpoints, no DAO changes. The existing `ai_credential.provider` column already stores an arbitrary string; `ANTHROPIC` is simply a new valid value that the `Map<String, AiProvider>` registry in `AiReviewService` resolves at call time.

  **Transport differences from OpenAI.** The Anthropic Messages API differs from OpenAI Chat Completions in three structural ways:

  1. Auth is `x-api-key: {key}` + `anthropic-version: 2023-06-01` headers rather than `Authorization: Bearer`.
  2. The system prompt is a top-level `"system": "..."` field; the `messages` array contains only user/assistant turns (no `role: "system"` message).
  3. `max_tokens` is required. Values used: 4096 for the review JSON call (generous headroom for a full recommendations payload), 2048 for memory/summary/book-summary, 1024 for editorial and weather interpretation.

  **JSON contract without `response_format`.** OpenAI's `response_format: json_object` has no Anthropic equivalent. The functional block already demands pure JSON with no prose or fences, and Claude follows it reliably. The existing `stripCodeFences()` helper handles edge cases. `parseRecommendations()` is byte-identical to the OpenAI version.

  **Prompt versions shared.** `AnthropicProvider` uses the same prompt version constants (`chapter-review-v7`, `memory-v2`, etc.) as `OpenAiProvider`, because prompt version tracks content, not transport format.

  **`NovelKmsServer` change.** The `Map.of(openAiProvider.providerKey(), openAiProvider)` one-liner was replaced with a two-entry map including both providers. `WeatherLookupService` already took the providers map, so it picks up Anthropic at no additional cost.

  **Frontend.** `AiCredentialsPanel.jsx` introduced a `PROVIDERS` array (key, friendly label, key prefix placeholder, default model hint, model helper text). The provider dropdown was enabled (it was previously `disabled` with a "More providers coming later" note) and now offers `OpenAI` and `Anthropic (Claude)`. Provider is locked to read-only on edit — a credential's provider type cannot be changed after creation. The list view renders the friendly label instead of the raw provider key string.

  **Decisions.**

  - D1: No schema migration. The `provider` column was always a free string; adding `ANTHROPIC` requires no DDL.
  - D2: `max_tokens` ceiling values are hardcoded per call type in `AnthropicProvider`, not author-configurable. The models stop at natural end-of-output well before these limits.
  - D3: Prompt version constants are shared references from `OpenAiProvider` — `AnthropicProvider.PROMPT_VERSION = OpenAiProvider.PROMPT_VERSION`, etc. A future prompt content change bumps both simultaneously.
  - D4: `functionalBlock()` is duplicated into `AnthropicProvider` (identical content) rather than extracted to a shared base class, consistent with the existing hand-rolled-integration ethos and keeping each provider self-contained.
  - D5: Provider dropdown is locked on edit — users replace keys by editing the credential, not by changing its type.
  - D6: Default model `claude-sonnet-4-6`; author can override per-credential.
