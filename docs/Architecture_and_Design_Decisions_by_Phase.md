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
