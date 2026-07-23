# NovelKMS Architecture and Design Decisions by Phase

This document captures durable architectural decisions and key lessons. It is intentionally concise; it is not a transcript of every implementation session.

## Phase 0 — Product and architecture foundation

### Product vision

NovelKMS is a novel knowledge-management system with a manuscript editor attached. Manuscript text, project metadata, Codex entries, templates, styles, AI review history, and future character/canon/timeline entities are first-class project data.

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

- `display_order` drives ordering. Drag-and-drop generalized to parts, chapters, and scenes, including cross-container moves.
- Nav tree selection must carry enough parent context (`projectId`, `bookId`, `partId`, `chapterId`, `sceneId`) so tools and properties panels operate on the correct scope.

### Parts and chapter numbering

- Parts are real manuscript containers. Direct-book and part-contained chapters share one book-level order.
- Numbers computed dynamically (never stored). Blank titles render as `Chapter N` or `Part I`.
- Reset numbering uses a two-stage SQL window pattern: running reset group, then row number within group.

### Single and aggregate editing

Scene → chapter → part → book editing scopes. Aggregate editing preserves scene IDs and saves each independently. Protected structural nodes mark scene boundaries. Autosave fails closed if boundaries are corrupted.

### Rich text editor decisions

TipTap foundation. Packages must remain version-aligned. Paragraph style keys are semantic. Inline formatting separate from paragraph styles. Base64 in-editor images accepted for simplicity. Node views with JSX require `.jsx` files.

### Templates and page layout

Cover/part templates with token atoms (TITLE, SUBTITLE, AUTHOR_FULL_NAME, etc.). Lazily seeded globals; book overrides are copy-on-write. Page layout mode is a drafting preview, not full typesetting.

### Import/export

DOCX import → Book/Part/Chapter/Scene. DOCX export from hierarchy + templates. Scene boundaries preserved. Markdown and ePub remain separate tracks.

### Search

Frontend/TipTap decoration-based. Highlights are transient decorations, not stored marks. Search text nodes, not raw HTML.

### HTTP response code convention

`204 No Content` for valid requests with no entity. `400 Bad Request` when response body needed. `404` reserved for config/routing errors. DELETE: `200` = existed and deleted; `204` = did not exist, idempotently complete.

## Phase 1 — Key implementation lessons

- H2 and PostgreSQL need dialect-specific migrations; H2 requires one `ALTER TABLE ... ADD COLUMN` per column.
- MUI disabled buttons inside `Tooltip` need a wrapper element.
- Avoid `useEffect(() => setState(...))` for derived state; derive during render or remount with key.
- Update DTOs should use boxed types for required toggles. Every update path must echo unchanged fields.
- Cache invalidation must match the blast radius of the change.

## Phase 2 — Authentication, ownership, and multi-user readiness

Design principles: user-owned entities scoped to authenticated user. Paths with entity UUIDs use path-derived authorization. Resources with only their own UUID enforce ownership in resource/DAO. Secrets stay out of Git, images, and JSON responses.

## Phase 2 — AI review framework

AI is an editorial assistant, not a manuscript generator. BYOK model with encrypted credentials. OpenAI first behind provider abstraction. Review artifacts are immutable rows with child recommendations. Review rail beside the editor. Anchor text (`chapter-review-v2`) enables click-to-scroll. Codex promotion creates entries from recommendations.

## Phase 2 — Trash

Per-user top-level surface. Root-only stamping; descendants hidden transitively. Supported roots: projects, books, chapters, scenes, codex categories/entries, AI reviews. Parts excluded (promote-children semantics). Restore appends to end, de-duplicates titles. Purge is irreversible.

## Phase 3 — Settings restructure and AI form/functional split

### AI form/functional split (V20)

System prompt = `form + "\n\n" + functional`. Form is author-editable at four scopes (book → project → user → system default). Functional is the constant JSON output contract. Provenance (`form_scope`, `form_instructions`) stamped per review. Resolution in `AiReviewService.execute()`. Prompt version bumped to `chapter-review-v4`.

### Uniform settings scope model (V21–V22)

| Category             | Resolution                     | Storage                             |
| -------------------- | ------------------------------ | ----------------------------------- |
| Document settings    | BOOK → PROJECT → USER → SYSTEM | `editor_settings` table             |
| Page layout          | BOOK → PROJECT → SYSTEM        | `page_layout` table (typed columns) |
| AI form instructions | BOOK → PROJECT → USER → SYSTEM | columns + `ai_form_global`          |

Frontend: `EditorSettingsDialog` three tabs with `OverrideShell` (off = inherit, on = copy-on-write, off again = delete override). Each tab remounts on scope via key.

## Deployment architecture

Fedora media server, rootless Podman Quadlet/systemd. PostgreSQL 17 + NovelKMS + Caddy containers. Private Podman network; Caddy is the only public service. Backups via `pg_dump` + config/secrets/Caddy state, archived to Dropbox.

### V28 — Stripe billing and subscription enforcement

Stripe-hosted Checkout/Portal with local `user_subscription` entitlement table. Webhook idempotency via `stripe_webhook_event`. Access-granting statuses: `active`, `active_canceling`, `trialing`, `family`. `family` is manual override; Stripe events must not demote it. Active scheduled cancellations normalized to `active_canceling`. Frontend: billing API/hooks, Settings → Billing tab, success/cancel return pages, global 402 handling.

### V29–V31 — Admin role foundation, audit, billing support, minimal console

Security-first: role-aware authorization → audit logging → read-only support → conservative mutation.

**Role foundation.** `user_role` table, `NovelKmsPrincipal`, JAX-RS `SecurityContext`, `RolesAllowedDynamicFeature`, `@RolesAllowed(Roles.ADMIN)`. Tenant/subscription filters allow admin paths only for admin principals.

**Audit.** `admin_audit_log` with admin/target user, action, entity metadata, old/new values, reason. Mandatory for mutations.

**User/billing views.** `/api/admin/users` search (email/name/UUID/Stripe IDs), detail with identity/roles/subscription/usage. Billing detail with computed flags (`hasAccess`, `familyAccess`, `stripeLinked`, etc.).

**First mutation: grant family access.** Validates active target, captures old/new subscription, writes audit. Revocation deferred pending restoration-semantics design.

**Frontend.** Minimal console at `/admin`: user search, identity/billing/usage/audit cards, family-access grant dialog.

**Testing.** Moved to `NovelKmsTestBase` (Flyway-backed schema). `truncateAll()` order includes admin tables. Unique emails/Stripe IDs per fixture.



## Current architectural watchlist

- Admin billing: trial extension, revoke-family design, webhook diagnostics, plan mapping, Stripe reconciliation.
- ePub export repair.
- Style-editor UI.
- Deferred AI findings view.
- Async AI execution for part/book-level reviews.
- Profile full-book editor performance.
- Book-summary staleness may be too eager (any newer chapter summary triggers rebuild).
- Review network: reviewer-side copy/download restrictions (spec §22, open questions 3–4) undecided.
- `review_snapshot` has no source version marker (spec §8.2 calls for one).

### V19 — Scene-level AI review

Scope derived, never stored: `chapter_id NULL → BOOK` (reserved), `scene_id NOT NULL → SCENE`, else `CHAPTER`. `ai_review.scene_id` added; `chapter_id` relaxed to nullable. Scene review records parent chapter for grouping. Prompt `chapter-review-v3`: scope-aware wording. New endpoint `POST /ai/reviews/scenes/{sceneId}`.

### V24 — Chapter memory documents (frontend)

Memory tab in ReviewRail (staleness chip, generate/regenerate, inline editing). Pre-review warning (`PreReviewMemoryDialog`) when preceding chapters are flagged. Nav context menu generate/edit. Template editor at three scopes. Per-row staleness badge deferred. Cache keys: `CHAPTER_MEMORY_KEYS`, `MEMORY_TEMPLATE_KEYS`.

### V25 — Book / chapter summaries

Independent of memory documents. `chapter_summary` (one per chapter, UNIQUE) and `book_summary` (one per book, UNIQUE). `chapter-summary-v1` → paragraph; `book-summary-v1` → synopsis from chapter summaries (never raw prose), ≤1000 words. Aggregation via `bookChapterSummaries` CTE. States: MISSING / STALE_CONTENT / OK. Coverage gating via `PreBookSummaryDialog`. Endpoints on `SummaryResource`.

### V26 — One-time author guidance

`user_guidance TEXT` on `ai_review`, `chapter_memory`, `chapter_summary`, `book_summary`. Appended to user message as fenced block. Pre-fills from previous guidance, not auto-cleared. Batch paths excluded. Prompt versions bumped: `chapter-review-v6`, `memory-v2`, `chapter-summary-v2`, `book-summary-v2`.

### V27 — Memory/summary as nav-tree leaves

Editing moved from modals to EditorPanel via fixed nav leaves. `ChapterAiDocItem` (Memory/Summary per chapter), `BookSummaryItem`. `selection.aiDocType` added. `aiDocKey` pattern mirrors `templateKey`. Content storage: plain text → authored HTML; `htmlToPlainText` strips before prompts. `RegenerateConfirmDialog` shared. ReviewRail Memory tab and "View chapter summaries…" become read-only peek surfaces with "Edit in document" link.

### V31 — In-app help system

Frontend-only. Markdown files in `src/help/content/`, auto-discovered via `import.meta.glob`. Zero-dependency renderer (`miniMarkdown.js`). `helpRegistry.js` for TOC/search. `HelpProvider`/`useHelp()` context. `HelpCenter` modal (TOC pane + content pane). `HelpButton` component. Static validator `scripts/check-help.mjs`. 26 starter topics, 7 sections.

### V32 — Artifacts (per-project file/folder store)

External blob store on host volume. `artifact_blob` (SHA-256 captured, `storage_key`) + `artifact_node` (self-referential tree, case-preserving/case-insensitive names via DAO check). Per-user quota. Streaming upload with staging, cap enforcement, atomic commit. Trash integration (two new root types). Nav tree folders + center-pane `ArtifactsPanel` Explorer with its own `DndContext`. Native OS file drag-and-drop. Image preview modal. Designing toward versioning.

### V34 — Editorials (per-chapter author-facing AI reading)

Short editorial reading: tone, genre drift, arcs, storyline evolution. Never consumed by other AI functions. `chapter-editorial-v1`, free-text, ~half a page. Uses same context as chapter review (preceding memory + pinned Codex) but output never folded into any prompt. `chapter_editorial` table (one per chapter, UNIQUE), HTML storage. `EditorialResource`: `GET/PUT/DELETE/POST /ai/editorial/chapters/{id}`. Third fixed chapter nav leaf (`RateReviewOutlinedIcon`). No ReviewRail tab, no staleness view.

### Anthropic (Claude) provider

`AnthropicProvider` as second `AiProvider` peer. No migration. Transport differences: `x-api-key` header, system prompt as top-level `"system"` field, `max_tokens` required. No `response_format` equivalent; `stripCodeFences` handles edge cases. Prompt versions shared with OpenAI. Provider dropdown locked on edit.

### Per-provider AI document variants — Phase 2 (frontend)

Phase 1 (V36) backend gave each AI doc family a provider dimension. Phase 2 surfaces it: provider selector in editor toolbar (AI-doc mode), instant switching (all variants fetched once, selected client-side). Provider-aware autosave, generate, clear. `selection.aiDocProvider` transient state. Shared provider roster extracted to `aiProviders.js`. Coverage/staleness surfaces stay default-provider (Phase 3 deferred).

### Codex entry DOCX export/import + AI fill-in

**DOCX round-trip contract.** Export: H1 title → H3 per schema field label → Normal paragraphs → H2 "Description" → body paragraphs. Import expects the same structure; unrecognized H3 labels silently skipped; existing field values for absent fields preserved. `CodexExportService` (Apache POI XWPF). Heading detection via `paragraph.getStyleID()` case-insensitive. Import saves directly via existing DAO methods and returns updated `Scene`. `_removedFields` metadata preserved in merge.

**Tenant authorization.** All three endpoints path through `scenes/{sceneId}`, already covered by `TenantAuthorizationFilter`.

**AI fill service.** `CodexAiService` (separate from `AiReviewService`): `SceneDao`, `ChapterDao`, `BookDao`, `CodexDao`, `CodexCategoryDao`, `AiCredentialDao`, `ChapterSummaryDao`, providers map. Resolves schema, assembles context, calls `AiProvider.fillCodexEntry`, returns result without saving. Prompt `codex-fill-v1`, JSON contract `{"fields":{key:value},"body":"plain text"}`. OpenAI uses `response_format: json_object`; Gemini uses `responseMimeType: application/json`; Anthropic uses demand-JSON + `stripCodeFences`.

**Codex-scope context.** A codex is scoped to one book (`Codex.bookId`) or one project (`Codex.projectId`). Context assembly matches: book-scoped → that book's chapter summaries; project-scoped → every book via `BookDao.findByProjectId(...)`, concatenated under `## Book Title` headings when multi-book. **Bug found and fixed same session:** the first implementation took only `bookId` and returned null for project-scoped codexes (the common case for series-wide characters), so the model received no manuscript context and returned all-empty fields. Fix: `assembleManuscriptContext(Codex, provider)` branches on scope. The `no_chapter_summaries` gate applies to both scopes and names whether it inspected the book or project. **Lesson:** when a feature reads "the manuscript" for a codex, mirror the `bookId`/`projectId` duality the `Codex` model encodes.

**Frontend.** Action row at top of `CodexEntryFields`: Export to Word, Import from Word, Generate with AI. Generate expands inline guidance field. Overwrite confirmation dialog when AI would replace non-empty fields. `EditorPanel` passes `entryTitle` and `onBodyGenerated={(html) => editor?.commands.setContent(html, false)}` so AI body lands in the editor. New `api/codexEntry.js` + `hooks/useCodexEntry.js`.

### PDF export

New `PdfExportService`, peer to `ExportService` (DOCX). Same four scopes, same `PageLayout`/`Template`
resolution. Unlike DOCX, renders via OpenHTMLtoPDF (CSS renderer over PDFBox 3): TipTap scene HTML is
embedded directly into a styled XHTML document rather than walked into low-level formatting calls, so
images/lists/inline marks need no special-case code. CSS mirrors DOCX's fixed manuscript-format constants
(double-spaced body, 0.5in first-line indent, 12pt Times). Named `@page cover` (no running header) vs
default `@page` (running header "LastName / ShortTitle / page#" via CSS counter) replicates DOCX's
section-break header suppression on cover/title pages. Text rendered with PDFBox standard Times fonts
(no embedded TTF) — zero container font risk, but WinAnsi-only; non-Latin scripts unsupported until a
Unicode font is bundled. `hr` → "* * *" scene-break substitution duplicated from `ExportService` (small,
intentional duplication — no shared base class between export services in this codebase).

### Static marketing site and `/app` application split

NovelKMS now separates the public marketing/documentation surface from the authenticated React application while preserving the single-container/single-JAR deployment model.

**Routing model.**

```text
/                         Hugo static public site
/faq/                     Hugo public page
/privacy/                 Hugo public page
/terms/                   Hugo public page
/app/                     React/Vite SPA
/app/billing/success      React route
/app/billing/cancel       React route
/app/admin                React route, authenticated/admin-gated
/api/*                    Dropwizard/Jersey API
```

**Maven module model.**

A fourth Maven module, `novelkms-static`, builds the Hugo site. The module packages Hugo output as classpath resources under `site/`. Generated Hugo output is not committed.

The existing `novelkms-frontend` module continues to build the React/Vite app and package it under `webapp/`. The frontend production build uses:

```env
VITE_APP_BASENAME=/app
```

so Vite emits app assets under `/app/assets/...`.

The `novelkms-distro` module depends on backend, frontend, and static modules. The final shaded JAR includes both:

```text
site/**
webapp/**
```

The backend development placeholder resources for `site/**` and `webapp/**` are excluded from the shaded JAR so the generated Hugo and React resources are authoritative in the packaged distribution.

**Dropwizard static serving.**

Dropwizard registers two named asset bundles to avoid servlet-name collisions:

```java
bootstrap.addBundle(
        new AssetsBundle(
                "/site",
                "/",
                "index.html",
                "site-assets"));

bootstrap.addBundle(
        new AssetsBundle(
                "/webapp",
                "/app/",
                "index.html",
                "app-assets"));
```

The prior root-mounted React bundle was replaced. The React app no longer owns `/`; it owns `/app/`.

**SPA fallback.**

The existing SPA fallback filter was updated rather than adding a second filter. It now only falls back to the React SPA for `/app` and `/app/*` browser routes. It leaves `/api/*` to Jersey and root/static paths to the Hugo site.

The fallback behavior is:

```text
/api/*                 pass through
/app                  forward to /app/index.html
/app/                 forward to /app/index.html
/app/* static asset   pass through to AssetsBundle
/app/* route          forward to /app/index.html
/*                    pass through to Hugo/static bundle
```

This allows direct navigation to React routes such as `/app/billing/success` while preventing the SPA from swallowing Hugo public pages.

**React routing decision.**

React Router is configured with `BrowserRouter basename="/app"` using `import.meta.env.VITE_APP_BASENAME`. Code that makes route decisions must use React Router’s `useLocation()` so routes are seen without the `/app` prefix. For example, `/app/billing/success` is seen inside React as `/billing/success`.

Direct use of `window.location.pathname` for in-app route branching is incorrect because it includes `/app` and bypasses React Router basename handling.

**Asset decision.**

React-owned public assets live in `novelkms-frontend/public` and are referenced through `import.meta.env.BASE_URL`, for example:

```jsx
src={`${import.meta.env.BASE_URL}brand/novelkms-logo.png`}
```

Hugo-owned public assets live in `novelkms-static/static` and are served from root paths such as `/brand/...`.

Small branding assets may be duplicated between `novelkms-static/static/brand` and `novelkms-frontend/public/brand` when both the public site and the React app need them. Generated output directories such as `novelkms-static/public/` and `novelkms-frontend/dist/` remain uncommitted.

### V38 — Human Review Network (Phase 1 schema, slice 1A: profiles)

Whole Phase 1 schema in one migration (8 tables) so slices never chase schema. Both dialect files
are byte-identical — every type used is common to H2 and PostgreSQL.

**First legitimate cross-user read path.** `TenantAuthorizationFilter.authorizePathIds` switches on
the segment preceding a UUID and returns `default -> true`, so `/api/review/...` passes through
untouched. Authorization for these tables is therefore enforced explicitly in the resource/service
layer, never by the tenant filter. `SubscriptionAuthorizationFilter` still applies: participating in
the review network requires an active subscription.

**Mutability is the organizing rule.** `review_request` mutable and lifecycle-bearing;
`review_snapshot` and `review_context_item` immutable, frozen at publish; `human_review` mutable
while DRAFT, immutable once SUBMITTED. `review_snapshot.request_id` carries UNIQUE for Phase 1 (one
snapshot per request); dropping that single constraint yields Phase 2's snapshot-lineage model.

`source_entity_id` is a bare UUID with no FK — provenance only. A snapshot must survive deletion of
its source chapter; an FK would either block the delete or cascade away review history.

Contribution metrics are derived, never counted into columns: SUM of snapshot word counts over a
user's SUBMITTED reviews. The definition is self-deduping, so no read-tracking is needed and
withdrawing a review removes it from the metric automatically.

Handles follow the artifact case rule (`handle` preserved, `handle_lower` unique) — but unlike
artifact names this CAN be a DB unique index, because there is no trash/soft-delete requiring a
filtered one.

**`@Context ContainerRequestContext` must be a method parameter, never a field.** Jersey does not
proxy it into the fields of a singleton resource. Production registers resources by class, so they
are instantiated per request and a field binds fine — but `ResourceExtension` registers an
*instance*, i.e. a singleton, where the field silently stays unbound and every endpoint NPEs into a
500. The method-parameter form works identically in both. This is why the resource worked in the
running app while all 35 of its tests failed.

Security tests must not conflate the DTO contract with the invariant. A forged `id` in an update
payload is rejected with 400 (strict mapper, unknown property) rather than ignored — but that is a
mapper-config fact, not a security property. The security property (the DAO's `WHERE user_id = ?`)
is tested separately with a legitimate payload, so relaxing `FAIL_ON_UNKNOWN_PROPERTIES` cannot
raise a false alarm on a security test.

### V39 — Human Review Network slice 1B (publish + My Requests)

**Publish is one atomic step straight to OPEN.** DRAFT exists in the column but is unused: a package
with no manuscript is not a recoverable state, so request and snapshot are both written or neither.

**Snapshot assembly.** Scenes in display order, joined by a bare `<hr>` — *not* the editor's
`<hr data-scene-after="{uuid}">`, which would hand a stranger the internal UUIDs of the author's
scenes. `SceneBreak.js` already parses a bare `<hr>` for backward compatibility. Word count is
recomputed from the assembled HTML via the new canonical `utils/WordCount.fromHtml`, never summed from
`scene.word_count` (historically zeroed by the pre-V37 autosave path).

**`source_updated_at` (V39)** is the source chapter's `updated_at` at capture time. Compared against
the live chapter it yields `sourceState` ∈ CURRENT / CHANGED / DELETED — a string, not a pair of
booleans, because the states are mutually exclusive and two booleans admit a nonsense fourth
combination (and the Lombok is-prefix rule makes boolean fields a hazard regardless). A null marker
(pre-V39 row) resolves to CURRENT rather than inventing a CHANGED the author cannot act on.

**Codex categories are chapter rows** (`codex_id` set, `book_id` null). Publish rejects them with 400
`not_manuscript`, and the nav menu item is gated on `isManuscriptNode` so it never appears — otherwise
private worldbuilding would land in a public queue.

**Frontend.** `ReviewRequestDialog` serves publish and edit from one field set. Edit mode fetches the
whole request rather than seeding from `ReviewRequestSummary`: the summary omits `authorQuestions`,
`contentWarnings`, and `maxReviews`, while PUT rewrites every column — seeding from the list row would
blank three fields silently. Feedback types are stored as stable UPPER_SNAKE keys with a UI-side label
map, so relabeling is never a data migration. Visibility is not exposed: the backend defaults to
PUBLIC, and INVITE would produce a package no reviewer can reach until invitations exist.

### V39 frontend (reconstitution note)

The 1B frontend was originally delivered as a bundle that was never committed; the
later `/app` static-site split moved `master` on, so it was rebuilt against current
`master`. `ReviewRequestDialog` loads the full request in edit mode rather than
seeding from `ReviewRequestSummary` (the summary omits `authorQuestions`/
`contentWarnings`/`maxReviews`; PUT rewrites every column). The publish entry point
lives on the chapter nav context menu, not in My Requests, because summaries carry no
`sourceEntityId` to republish from. `RequestCard`'s offered actions are derived from
status to match the service's legal transitions, so the UI never triggers a 409.

### Slice 1C — reviewer read path (backend)

Reader seam split from author CRUD: `ReviewAccessService` (cross-user reads) vs
`ReviewPublishService` (author's own). Read path named `/review/packages/...` not
`/review/queue/...` so the URL stays honest once a CLOSED package stays readable to
participants (§30.2 Q5) but has dropped out of the queue. All queue exclusions live in
one `ReviewQueueDao` statement: author-profile join filters SUSPENDED, symmetric
`NOT EXISTS` block check, left-joined submitted-review aggregate drives the cap.
404-not-403 for every cross-user denial; author reads own in any status. Participation
gated on a handle (409 `profile_required`); suspended viewer 403. `max_reviews` and
`reviewCount` wired ahead of 1D (both read 0 until reviews exist); `UserBlockDao`
read-only ahead of 1F. New DTOs `ReviewQueueEntry`/`ReviewPackage` expose handles, never
user/source ids. H2 test DB is default mode (no `MODE=PostgreSQL`), so queue SQL stays
plain-standard (`LIMIT ? OFFSET ?`, `COALESCE`, `LOWER()`, `NOT EXISTS`). Test-harness
note: `ResourceExtension.target(path)` percent-encodes a `?` folded into the path — query
params must be attached with `.queryParam(...)`.

### Slice 1C — cross-user render boundary (frontend)

The reviewer snapshot reader renders another user's HTML, so `RichTextPreview`
(self-trust `dangerouslySetInnerHTML`) is unsafe here — a hostile author's
`<img onerror=…>` would be live XSS in a reviewer's session. `SnapshotFrame` uses
`<iframe sandbox="" srcDoc=…>`: opaque origin, no scripting, faithful markup,
self-contained CSS, no new dependency. Capture-time sanitization is deliberately
deferred — a Jsoup safelist could strip custom TipTap markup (`data-style`, resizable
images, font-size spans) and can't be validated without a build; the frozen snapshot
stays faithful, and safety is enforced at the render boundary where the trust
transition actually happens. Queue uses `useInfiniteQuery` (offset paging); filters
commit on Apply for transparency (§12); 409 → claim-a-handle prompt.

### Slice 1D — Human review write path

**Jersey path-specificity fix.** `ReviewRequestResource` was `@Path("/")` with method
paths like `@Path("/review/requests")`. Once slice 1C added `ReviewQueueResource` at
`@Path("/review")`, Jersey's class-level prefix matching gave the more-specific
`@Path("/review")` priority — it routed `GET /review/requests` to `ReviewQueueResource`,
found no `@Path("/requests")` method, and returned 404 without ever checking the
`@Path("/")` resource. Fix: `ReviewRequestResource` moved to `@Path("/review")` with
method paths shortened to `@Path("/requests")`, `@Path("/requests/{requestId}")`, etc.
The publish endpoint (`POST /chapters/{chapterId}/review-requests`) split into a separate
`ReviewPublishResource @Path("/")` because the tenant filter's `chapters` segment
authorization is load-bearing. **Lesson:** once any resource claims `@Path("/review")`,
no `@Path("/")` resource can own a method under `/review/...` — Jersey resolves the class
match first and only then considers methods within the winning class.

**Read-state as a column, not a table (V41).** The only durable state 1D needs beyond
V38's frozen columns is "author has seen this feedback." A read marker on the review
row (`author_read_at TIMESTAMP`, NULL = unread) expresses exactly that with no join and
no inbox. A `review_notification` table is a better fit once 1F adds events genuinely
worth an inbox (close, withdrawal, moderation); it can be added then without disturbing
this column.

**Two write gates because PAUSE means different things.** `ensureCanStart` (OPEN+PUBLIC,
not self, not blocked, author ACTIVE) guards a *new* review; `ensureCanWrite` (OPEN or
PAUSED) guards saving/submitting an *existing* one — so a reviewer finishes through a
pause but no new reviewer slips in. Withdraw has no request-status gate: a reviewer may
retract their own review whatever became of the request, and the row is retained (never
deleted) for dispute handling (§30.2 Q8).

**Saving always lands in DRAFT.** `saveContent` clears both terminal timestamps, which
makes "withdraw and rewrite" (§30.2 Q6/Q7) and "revise a submission" one rule instead of
special cases. Submit is atomic (save-then-mark) and enforces the author's `max_reviews`
cap at the last honest moment even though the queue already excludes capped requests.

**Cross-user identity keyhole.** Both list reads carry only handles; `HumanReview`,
`ReviewWritingSummary`, and `ReviewReceived` keep `reviewerUserId`/`snapshotId`/
`authorReadAt`/`sourceEntityId` off the wire. Resource tests assert the absence directly.
`markAuthorRead` authorizes by request-ownership subquery inside the UPDATE (a forged id
touches zero rows). All 1D SQL is plain-standard — runs on default-mode H2 and PostgreSQL.

**Render boundary.** Received review bodies are rendered as plain text (`htmlToPlain`),
not live HTML — a hostile reviewer's markup shows as inert characters, so no sandboxed
iframe is needed while Phase-1 reviews are plain text.

**Deferred (watchlist).** §30.2 Q5 participant-read of paused/closed packages —
`ReviewAccessService.authorizeRead` still gates to OPEN+PUBLIC for non-authors. Reviewers
can withdraw on any request state but cannot re-open the snapshot reader once a request
leaves OPEN. Fix = participant-aware read in `ReviewAccessService` (ripples its constructor
and two 1C test files); scheduled as its own small slice.

### Slice 1E — Contribution metrics

Derived at read time, no migration — everything measured already exists
(`review_snapshot.word_count`, `human_review.word_count`/`status`,
`review_request.author_user_id`, `review_profile.created_at`).
`ReviewMetricsDao.contributionFor(userId)` runs two plain-standard SELECTs: a reviewer-side
aggregate (words reviewed / review words written / reviews completed over SUBMITTED, self-deduping
via `UNIQUE(request_id, reviewer_user_id)`) and a received count over the author's requests.
`ProfileMetrics` assembles those with handle + member-since from the profile already in hand.

**The objective-metric rule (§6.5).** Reviews-received is deliberately NOT block-filtered, unlike
`HumanReviewDao`'s writing/received lists — a viewer-relative "received: 10 for me, 9 for you" is
exactly what §6.5 forbids, so the aggregate carries no `user_block` predicate. Word-count integrity
holds because `human_review.word_count` is server-computed (`WordCount.fromHtml`) at save/submit,
never trusted from the client.

**Shared cross-user gate.** `byHandle` and the new `metricsByHandle` both go through one private
`readableByHandle` (missing/HIDDEN/SUSPENDED → non-disclosing 404; owner always reads own), so the
profile view and its metrics cannot diverge and a 1F block-hide rule lands in exactly one place.
Endpoints on `ReviewProfileResource`: `GET /review/profile/metrics` (self, 404 `no_profile`) and
`GET /review/profiles/{handle}/metrics`. Deferred: public/private split (every submitted review is
PRIVATE in Phase 1, so the public count is dead) and recent-activity (leaks cadence). Frontend wires
self-view only; the cross-user endpoint ships tested with no UI consumer yet.

### Slice 1F — Blocking, reporting, admin removal

Phase 1's final slice, and its safety floor. **No migration** — `user_block` and
`content_report` were part of V38's whole-Phase-1 schema, read-only until now.

**Two resources, two trust levels.** `ReviewSafetyResource` is user-facing and
active-profile-gated (409 `profile_required` / 403 suspended); `AdminModerationResource`
is `@RolesAllowed(ADMIN)`. Both keep the cross-user keyhole from earlier slices:
everything on the wire is a handle, cross-user denial is 404 never 403. A report's
PROFILE target is addressed by `targetHandle` and resolved to a profile id server-side —
there is deliberately no bare USER target, so a report always names a concrete artifact
or a public profile, never an opaque account.

**Auto-resolve on removal.** Taking content down (`requests/{id}/remove`,
`reviews/{id}/remove`) or suspending a profile also auto-resolves that target's OPEN
reports in the same transaction. The moderation queue therefore never shows a report
whose subject has already been actioned, without a second admin step or a reconciliation
sweep — the removal *is* the resolution.

**Report is file-and-forget.** The reporter gets back only `{id, status}` and cannot
list their own reports in Phase 1. This sidesteps a retaliation/harassment surface
(a visible "my reports" feed invites score-keeping) while the audit log still preserves
everything a moderator or dispute needs.

**Block is symmetric and idempotent.** A block hides the counterparty's requests from
the blocker's queue and their reviews from both review lists, in both directions;
re-blocking is a no-op and unblocking a non-blocked handle still returns cleanly. The
objective-metric rule (§6.5) is untouched: reviews-received counts are *not*
block-filtered, so a block changes what you see, never the public totals.

**Frontend — one shared menu, one shared dialog.** Rather than duplicate block/report
wiring into four cards, `ReviewCardMenu` owns the ⋯ trigger, the block mutation (with an
Undo-in-snackbar that exercises unblock), and a conditionally-mounted `ReportDialog`;
each surface drops in one element. The ⋯ trigger is a literal glyph, not an icon import —
no `MoreVert`/`MoreHoriz` is proven present in node_modules, and an absent icon fails the
Rolldown build. Menu children stay flat (no Fragment wrappers) per the MUI child-indexing
rule. Block/unblock invalidation is scoped to exactly the block-filtered reads (blocks
list, queue, received, writing). The admin surface is a self-contained
`AdminModerationPanel` mounted from a new console tab (imperative useState + adminApi,
matching the console rather than introducing react-query there); one `{reason, note}`
dialog drives every moderation action, and profile suspension is keyed by handle because
`ContentReportView` carries a target id but not a target handle.

### Autosave arm-time capture fix (EditorPanel)

**Root cause.** `scheduleSave` read the editing mode and target ids from refs at FIRE time (1500 ms after the keystroke), not ARM time. Moving between a scene and its parent chapter does not change `chapterId`, so the scope-change effect never reset the timer — but the mode refs had already flipped. Three defects compounded:

1. **Scene → chapter**: the content-load effect cleared the timer on scope change, silently discarding the pending scene edit before it reached the server.
2. **Chapter → scene**: no equivalent guard existed, so the timer fired with `singleSceneModeRef === true` and wrote the entire chapter's aggregate HTML into the newly selected single scene.
3. **Neither save path invalidated `SCENE_KEYS.byChapter`**: with `staleTime: 30_000`, switching from scene to chapter served stale content for up to 30 seconds. The content-load effect's guard keyed on scene IDs (not content), so when the list finally refetched with the same IDs the effect early-returned and kept the stale document — the next keystroke autosaved it back, destroying the edit.

**Fix: arm-time job capture.** `buildSaveJob(html)` runs synchronously in `onUpdate` and returns a closure that captures mode, target ids, and word count. `scheduleSave` stores the closure in `pendingSaveRef` and arms the timer; the timer only calls `runPendingSave()`, which executes whatever job is pending. A save always lands on the document it came from, independent of navigation and effect ordering.

**Fix: flush on scope change.** A cleanup effect keyed on `saveScopeKey` (a composite of mode + entity ids) calls `flushPendingSave()`. React runs cleanup before the incoming effect body, so the outgoing document's save commits and seeds caches before the incoming document loads. Also fires on unmount, replacing the old discard-on-unmount `clearTimeout`.

**Fix: gate content loading.** The content-load effect early-returns while `pendingSaveRef.current || savingRef.current`. Completing a save bumps `saveEpoch` (a state counter in the dependency array), which re-runs the effect against settled caches.

**Fix: seed caches from save response.** `applySavedScenes` calls `setQueryData` on `SCENE_KEYS.detail(id)` and patches the matching row inside `SCENE_KEYS.byChapter(chapterId)` from the `Scene` row `PUT /scenes/{id}/content` returns. In-flight GETs for those keys are cancelled first (`cancelQueries`) so a fetch issued at the moment of navigation cannot resolve afterward and restore pre-save content. `draft-document` caches (whole-book/part editing) are patched identically. Falls back to `invalidateQueries` on a 204 or when no list is cached.

**Scene split guard.** `handleSplitSceneConfirm` clears `pendingSaveRef` in addition to the timer — without this the flush effect would commit a pre-split document on the next navigation.

**Dead code removal.** `useUpdateSceneContent` in `useScenes.js` was never imported; its `onSuccess` omitted the `byChapter` invalidation, so reviving it would reintroduce the stale-chapter bug. Removed with a comment recording why.

**Single-scene word count fallback.** The word count captured in `buildSaveJob` falls back to `countWords(html)` instead of `0` when `CharacterCount` is unavailable. A zero there is what historically corrupted `scene.word_count` (pre-V37 autosave bug).

- Autosave `buildSaveJob` reads `activeScenesRef` for the draft-mode chapter-by-scene map; if a scene is added or removed between arm and fire, the boundary check rejects the save. This is correct for protection but means a very rapid add-scene → type → save sequence could drop the first keystroke. Monitoring.

### V43 — Book Scratchpad

A per-book holding pen for scenes that are not part of the manuscript: parked drafts, cut
scenes, alternate takes. Never rendered into the book, never counted, never sent to any AI.

**The shape does the work.** A Scratchpad is a `chapter` row with `book_id`, `part_id` and
`codex_id` all NULL and a new `scratchpad_book_id` naming the book. That NULL `book_id` is the
entire exclusion mechanism, reusing V13's codex trick: every book-rooted read in the codebase
filters `chapter.book_id`, so the Scratchpad drops out of the numbering CTE, `BookOutline` (all
four statements), `bookChapterSummaries`, `ChapterMemoryDao`, `SceneDao.findContentForBook`
(search), the word/paragraph rollups in `BookDao`/`ProjectDao`/`PartDao`, the trash child
counts, and all three export services **without one line added to any of them**. It also fails
safe: a book-rooted query written next year is excluded by default rather than needing to
remember a guard. The rejected alternative — keeping `book_id` set plus a `chapter_role`
discriminator — required editing ~14 existing statements and would have failed open forever.

**Every AI guard was already correct.** `AiReviewService` (chapter review, scene review, memory,
summary, editorial) and `ReviewPublishService` all gate on `chapter.getBookId() == null` and
throw `not_manuscript`. A Scratchpad chapter satisfies that predicate, so the entire
AI-exclusion requirement needed no code. Worth remembering as a design argument: choosing the
shape an existing invariant already tests is cheaper than adding a parallel one.

**The enumerable cost is the ownership chain.** Any query resolving a chapter to its owning
project must now carry four arms, not three:
`COALESCE(b.project_id, cx.project_id, cb.project_id, sb.project_id)` with
`LEFT JOIN book sb ON sb.id = c.scratchpad_book_id`. Without it the tenant filter 404s the
author out of their own Scratchpad. Updated: `TenantAccessDao.ownsChapter`/`ownsScene`,
`TrashDao.trashChapter`/`trashScene`, and the five chapter-rooted counts in
`AdminUserDao`/`AdminMetricsDao`. The two codex-rooted chains in those admin DAOs are correctly
untouched. **Grep `cx.project_id` before adding any new chapter→project resolution.**

**Archive would have silently eaten it.** `ArchiveDao.findChaptersForProject` joined on
`ch.book_id`, which would have dropped the Scratchpad and every scene parked in it out of the
backup. Now joins `COALESCE(ch.book_id, ch.scratchpad_book_id)` and exports the column.
`ArchiveService` needed nothing: `remapForeignKeys`, `setParam` and `insertRow` are all generic
over `_id`-suffixed columns, so the round trip works as soon as the column is exported.
Archives taken before V43 simply lack the key and import with it NULL.

**Not trashable, not renamable, not movable.** `trashChapter`'s context query carries
`AND c.scratchpad_book_id IS NULL`, and `ChapterResource` rejects update/move/delete with 400
`not_scratchpad_operation` so the author gets a message rather than a silent 204. Scenes inside
are fully trashable and restore into place unchanged (`restoreScene` is parent-generic).

**One per book, lazily created, no backfill.** `uq_chapter_scratchpad` on `scratchpad_book_id`;
NULLs are distinct in a unique index in both dialects, so ordinary chapters do not collide
(same property `uq_codex_project` relies on). `GET /api/books/{bookId}/scratchpad` is
get-or-create; a lost insert race re-reads rather than failing. Tenant authorization comes free
from the `books/{bookId}` segment.

**Frontend.** `selection.scratchpadBookId` is keyed by **book**, not by the Scratchpad's chapter
id — the id is unknown until the get-or-create fetch lands and the node must be selectable
before then. It joins `setSelection`'s existing scrub list in `App.jsx` alongside `aiDocType`
and `artifactFolderId`, which is why no other nav component needed touching: every click
anywhere clears it. This deliberately avoids adding another sticky selection key of the kind
that causes the known stale-`codexId` problem.

`ScratchpadItem` is a fixed leaf between the Codex section and the book Summary, outside the
outline `SortableContext` because it has no position in the book. Its contents are an ordinary
`scenes-{chapterId}` `SortableContext`, so NavPanel's existing scene handlers move scenes in and
out in both directions unchanged. The row itself is registered as a **droppable whose id is that
container id** — without it an empty Scratchpad is undroppable (no sortable child to aim at, and
the row is not a chapter node so NavPanel's "dropped on a chapter row" fallback does not apply);
registering the container id directly means `resolveToContainer` picks it up through the
existing `isContainerId` branch. Consequence: the scene list is fetched on mount, not on expand,
because NavPanel builds the reorder payload from the TanStack cache and a miss would renumber
the dropped scene to 0 on top of scenes already there.

`SceneItem` is given `bookId={null}`, the same signal a codex entry carries, so
`isManuscriptNode = !!menuNode.bookId` removes every AI action from the context menu with no new
conditions — mirroring the server rule rather than restating it. `EditorPanel` gets a
`scratchpadMode` placeholder rather than the aggregate chapter editor: the scenes have no
reading order and no chapter heading, so stitching them into one document would invent a
structure that does not exist.

**Pre-existing bug fixed in passing.** `ExportService.exportScene` resolves a scene's book
through its chapter and throws when there isn't one, so "Export as Word/PDF" on a **codex entry**
has been returning 500 rather than a file. Both `exportUrl` and `exportPdfUrl` in
`NavContextMenu` are now gated on `menuNode.bookId` for scenes, fixing codex and preventing the
Scratchpad from acquiring the same broken path.

**Verification.** Maven cannot run in the build environment. V1→V43 replayed on H2 2.4.240 (the
pinned version) with 16 assertions: schema shape; unique index rejects a second Scratchpad while
NULL rows still insert; exclusion from book-rooted chapter counts, word rollups,
`BookOutline.nextPosition`, the memory/summary ordering CTE and `findContentForBook`; ownership
resolving through the new arm for both chapter and scene; archive carrying both books'
Scratchpads; the `trashChapter` predicate refusing the Scratchpad while still accepting a
manuscript chapter; book delete cascading. Static: Java brace/package tokenizer, SQL bind and
INSERT arity, every `map(rs)` query confirmed to select the new column, esbuild JSX transform,
import-path and icon-presence checks.

## Extensible Codex E6 — non-destructive field removal.
- Soft-remove via codex_type_field.deleted_at (existing since V42); active reads
  already exclude removed rows. restoreField preserves original display_order.
- Entry counts computed in Java over scene.structured_data, never via a
  dialect-specific JSON operator (H2 DEFAULT mode has no reliable JSON path).
  "Contains information" = non-null, non-blank-after-trim.
- New read model CodexFieldUsage kept separate from CodexField/CodexType so the
  entry-form and AI contracts stay narrow (active-only, no editor fields).
- Endpoints hang off the tenant-authorized types segment; bodyless DELETE +
  restore POST avoid authorizeSensitiveJsonBody. GET .../fields/usage relies on
  JAX-RS literal-over-template precedence (same as E4's /fields/order).
- Counting lives in a small service (CodexFieldUsageService), not inlined in the
  resource, to keep the resource free of SceneDao/JSON parsing and give the
  count logic a Flyway-backed unit-test seam.

## Extensible Codex E8 — DOCX + AI promotion against per-instance 
Types (2026-07-21, no migration). CodexExportService resolves the round-trip schema from the entry's
own Type (codex_type_field), returning null for zero active fields to keep the plain
title+body branch. Promotion gains an optional codexTypeId (author-chosen project
Type), guarded to a live chapter of the review's project codex (404-safe 400
type_not_in_project); the fallback system_key map is retained. The promotion codex
path now stamps per-instance fields on newly seeded Types (E7 parity), so promotion
into a fresh project never yields a field-less Type. Invariant reaffirmed: /api/ai
promotion is user-scoped via reviewDao.findByIdForUser, and cross-Type targeting is
confined to the review's own project codex in the service layer.

## Extensible Codex E9 — Terminology sweep + DELIVERED docs (2026-07-22, no migration).
Decision 8 executed literally: only user-facing strings moved from "Category" to "Type";
schema columns (`chapter.codex_category`), DTO fields (`codexCategory`), routes
(`/codex/categories`), TanStack cache keys (`['codex','categories']`), the nav node type
`'codex-category'`, the trash type string `CODEX_CATEGORY`, and the filename
`CodexCategoryItem.jsx` are all unchanged. This keeps the sweep a zero-risk edit: no
rename migration, no cache-key churn, no API surface change, and no chance of a
half-renamed identifier reaching production.

Two internal constant renames were made anyway because they are module-private and
their old names actively misled readers about which model is live:
`CODEX_CATEGORY_LABELS` → `CODEX_TYPE_LABELS` and `CodexCategoryProperties` →
`CodexTypeProperties` in `PropertiesPanel`, `CATEGORY_ICONS` → `TYPE_ICONS` in
`CodexCategoryItem`. Both maps are keyed by system key and both already fell back
correctly for author-created Types (NULL key → `FolderSpecialIcon` / the Type's own
title); only the names were wrong.

Comment debt was the real finding. Three files still documented the pre-E4 world —
"categories are fixed (hardcoded at codex creation); they cannot be added, deleted, or
renamed", "Codex categories are hardcoded — cannot be deleted", "Rename — not available
for codex categories (fixed)". Each is now an accurate statement of the live model, and
the deletion comments say specifically that the affordance is missing rather than that
the operation is impossible — the distinction the next slice needs.

`NavToolbar` gained one derived value rather than new state:
`useCodexChapters(selection.codexId ?? null)` (guarded by the hook's own
`enabled: !!codexId`) resolves the selected Type's title from the same query key
`CodexItem` already populates, so TanStack deduplication means no extra request. It
feeds both `getAddLabel(selection, typeName)` and `AddCodexEntryDialog`'s `typeName`
prop, bringing the toolbar to parity with the context menu, which had passed `typeName`
since E5. Seeded Types still resolve from `ADD_ENTRY_LABELS` first; the Type's own name
is the fallback, so a NULL-system-key Type reads "Add Dragon" / "New Dragon". No manual
memoization was added (React Compiler is active).

Help gained `codex.types`, the first documentation of the entire E4–E6 editor surface.
It states the two contracts an author most needs to trust: renaming a Type or a field
never loses data (each field keeps a permanent internal key that `structured_data` is
stored against, so labels are free to change), and removing a field is non-destructive
(values are preserved, the field collects in "Removed fields" with a live entry count,
and Restore brings it back intact). The topic deliberately avoids Markdown tables —
`miniMarkdown.js` supports headings, emphasis, code, links, lists, blockquotes, and
rules, but not tables, which is a pre-existing latent bug in `artifacts.md`.

Known gap left open on purpose: `getDeleteContext` has no `codex-category` case in
either `NavContextMenu` or `NavToolbar`, so a Type has no nav delete affordance despite
E7 shipping the backend (trash leaves `codex_type_field` rows intact; cascade fires only
on hard purge). Terminology and behavior were kept in separate slices.

## Extensible Codex E10 — Close-out: Type delete + reorder, retire dead paths, surface description (2026-07-22, no migration).

Five decisions confirmed at thread start; all five executed as recommended.

**Decision 1 (promotion vs. deleted seeded Type).** `AiReviewService
.getOrCreateCategoryChapter` no longer creates-on-miss. When the mapped Type is
absent from the codex, it promotes into the live NOTES Type and logs the
redirect; only when NOTES itself is also missing does it create — and it
creates NOTES, never the deleted Type. The reasoning is recorded in the method's
javadoc and depends on an invariant verified at design time: all seven master
`codex_category` rows are `is_default = TRUE`, so every codex is seeded with
all seven at creation. A missing mapped Type therefore can only mean deliberate
deletion — there is no "never seeded" case to regress. New `NOTES_CATEGORY`
constant replaces the bare string literal in `resolveCodexCategory`. New private
`findLiveType(List<Chapter>, String)` helper, case-insensitive.

**Decision 2 (delete `POST /codex/{codexId}/chapters`).** Endpoint and
`CreateCodexChapterRequest` removed from `CodexResource`. The endpoint called
`ChapterDao.createCodexChapter` directly without touching `codex_type_field`,
producing a field-less Type — exactly the failure E7's seeding and E8's
promotion-path parity were built to eliminate. `ChapterDao.createCodexChapter`
itself stays: `CodexTypeDao.createType` and both seeding paths use it. Now
exactly one code path creates a Type, and it seeds.

**Decision 3 (Type description placement).** `CodexTypeProperties` in
`PropertiesPanel` reads `useCodexType(chapterId)` and renders the description as
`body2` with `pre-wrap`, only when set. An empty prompt was deliberately avoided
— the type editor is the single write path and a dead "Add a description"
affordance in a read-only panel invites a second edit path. As a side fix, the
label chip now prefers `type.name` over the AI-context entry list, which fixes
an author-created Type with no entries falling through to the literal "Type".

**Decision 4 (keep `GET /codex/categories`).** Route retained; frontend client
(`codexApi.getCategories`), hook (`useCodexCategories`), and key factory entry
(`CODEX_KEYS.categories`) removed. Comment on the route and on
`CodexCategoryDao` now states it is seed-template and promotion-mapping only,
names the two exact callers, and spells out the consequence — editing a row
affects future codexes, not existing projects' Types.

**Decision 5 (scope).** Honored. Nothing from the deferral list. No artifacts.md
table fix.

**A1 — Type deletion in the nav.** Both `NavContextMenu.getDeleteContext` and
`NavToolbar.getDeleteContext` gained a `codex-category` case. The confirm-dialog
`detail` quotes the actual E7 contract: "goes to Trash together with its fields
and all of its entries; restoring it brings them all back." Both dispatch
`useDeleteCodexChapter` → `chaptersApi.delete(id)` (the chapter soft-delete).
`setSelection` clears `chapterId` / `sceneId` / `codexCategory` but keeps the
codex selected.

`useDeleteCodexChapter`'s `onSuccess` invalidation was widened from
`CODEX_KEYS.chapters(codexId)` alone to also cover `['trash']` and
`['aiContext']`. This was a latent gap: the hook had zero consumers from E5
through E9, so the narrow blast radius never surfaced; A1 makes it live, and
without the fix the Trash and AI-context dialog would retain stale entries.

**A2 — Type reordering in the nav.** `canReorder` in both components includes
`codex-category`. Siblings come from `useCodexChapters(menuNode.codexId)` in the
context menu and from the existing `useCodexChapters(selection.codexId)` in the
toolbar — both hit CodexItem's cache key. `dispatchReorder` branches onto
`useReorderCodexChapters` → `PUT /codex/{codexId}/chapters/reorder`, which calls
`ChapterDao.reorderInCodex` (integer renumber scoped to the codex). DnD was
deliberately not added: `CodexCategoryItem` renders children in a plain `Box`
with no `SortableContext`, and that was judged too large for this phase.

One latent issue corrected here: `NavToolbar` now computes `isCodexTypeContext`
and excludes it from `isChapterContext`. Previously a selected Type satisfied
`isDirectChapterContext` → `isOutlineContext`, which fired `useChapters` and
`useParts` for the book outline — a list the Type is never in. The arrows came
out disabled (correct appearance), but because `canReorder` was false (the
`!!siblings && length > 1` check against the wrong list), not because the index
was absent. A2 would have inherited this: the outline-sibling list is non-null
but the Type's id is not in it, so `findIndex` returns -1, `canReorder` is true
but both arrows are disabled — which accidentally works but has a false code
path. The cleanest fix was the new context flag.

**B1/B2/B3 — dead frontend paths.** `useCodexCategories`,
`useCreateCodexChapter`, `CODEX_KEYS.categories`, `codexApi.getCategories`, and
`codexApi.createChapter` removed. Grep sweep confirmed zero consumers.

**C1 — `CodexCategoryDao` javadoc.** Now states the table is seed-template and
promotion-mapping only. Audit confirmed the only readers are `CodexResource`
(seeding + the retained categories route), `AiReviewService` (promotion mapping +
seeding), and DI wiring. No `ExportService`, no entry form, no frontend.

**D1 — help topic.** `codex-types.md` updated: deletion section now says
right-click Delete Type or toolbar delete, explicitly states seeded types can be
deleted, documents the NOTES fallback for promotion, and covers Move Up/Down and
properties-panel description. A cross-link to `manuscript.properties` was
dropped — no such topic exists; `npm run check-help` would have caught it.

**Verification.** Static only. Java brace/paren/bracket balance + package-path
(Python state-machine tokenizer, 3 files, all pass). esbuild
`--loader:.js=jsx --loader:.jsx=jsx --bundle=false` transform-only (5 files,
all pass). Grep sweep for every removed symbol (0 dangling references). H2
migration replay: not applicable (no migration). Manual exercise confirmed:
`mvn test` green (all codex suites pass). Recommended manual verification
before deploy: trash CHARACTER → promote a CHARACTER finding without the type
picker → confirm it lands in Notes → restore CHARACTER from Trash → confirm
fields and entries returned.

