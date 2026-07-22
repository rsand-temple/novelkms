# NovelKMS — Running Project Document

This is the current project-status document. Keep it concise. Historical implementation details belong in `Architecture_and_Design_Decisions_by_Phase.md`; deployment details belong in the README operation documents.

## Vision

NovelKMS is a novel knowledge-management system with a manuscript editor attached. Manuscript text, Codex, project metadata, templates, styles, AI review history, and future character/canon/timeline records are first-class project entities.

The immediate goal is practical validation: determine whether NovelKMS manages a real long-form novel project better than Dabble, NovelCrafter, Scrivener, or ad hoc Markdown/Word workflows.

## Repository

- Repository: `https://github.com/rsand-temple/novelkms`
- Branch: `master`
- Architecture: Maven multi-module project with backend, frontend, static marketing site, and distro modules.
  - `novelkms-backend` — Dropwizard/Jersey API and application server.
  - `novelkms-frontend` — React/Vite SPA, built under `/webapp` and served at `/app/`.
  - `novelkms-static` — Hugo static marketing/public site, built under `/site` and served at `/`.
  - `novelkms-distro` — shaded runnable JAR combining backend, React app, Hugo site, and runtime dependencies.
- Development workflow: Eclipse for Dropwizard/backend, Vite dev server for frontend, Hugo dev server for the static site, H2 for local development, PostgreSQL for the hosted deployment.

## Current production/deployment status

- Public deployment runs on the Fedora media server as rootless Podman Quadlet/systemd user services.
- Services include PostgreSQL, NovelKMS, and Caddy.
- Caddy terminates HTTPS and reverse-proxies all public traffic to the NovelKMS container over the private Podman network.
- The NovelKMS JAR now serves three public surfaces:
  - `/` — Hugo-generated static marketing/public site.
  - `/app/` — React/Vite authenticated application.
  - `/api/*` — Dropwizard/Jersey backend API.
- PostgreSQL is the hosted production database; H2 remains useful for local development and migration source/backup scenarios.
- Persistent state is outside disposable containers: PostgreSQL volume, config/secrets, Caddy ACME state, logs, artifact blob storage, and Quadlet definitions.
- Backups target Dropbox as completed archives, not live database files.
- The canonical public domain should be `https://novelkms.com`; legacy aliases such as `www.novelkms.com` and `novelkms.richardsand.com` should redirect to the canonical domain when Caddy is configured that way.

### Static public site and `/app` routing

- A Hugo static site is now integrated into the Maven build as the `novelkms-static` module.
- Hugo source files live in `novelkms-static`; generated output is not committed.
- Maven builds the Hugo site into the static module artifact under `site/`.
- The React SPA remains produced by `novelkms-frontend`, but it now builds with `VITE_APP_BASENAME=/app` so generated asset URLs resolve under `/app/assets/...`.
- The final shaded distro JAR contains both:
  - `site/**` for the public static site.
  - `webapp/**` for the React application.
- Dropwizard serves the Hugo site from `/` and the React SPA from `/app/` using separate named `AssetsBundle` registrations.
- The SPA fallback filter is now `/app`-aware: React deep links such as `/app/billing/success`, `/app/billing/cancel`, and `/app/admin` are forwarded to `/app/index.html`; root public pages such as `/faq/`, `/privacy/`, and `/terms/` are owned by Hugo.
- React routing uses `BrowserRouter basename="/app"` through `VITE_APP_BASENAME`, so in-app route checks should use React Router’s `useLocation()` rather than `window.location.pathname`.
- Public links from the React login page to FAQ, privacy, and terms pages use normal browser anchors such as `href="/faq/"`, not React Router links.

## Technology stack

### Backend

Java 17+, Dropwizard/Jersey, Flyway, explicit JDBC DAO layer, Apache DBCP2, H2 (dev), PostgreSQL 17 (prod).

### Frontend

React + Vite, Material UI v6, React Router, TanStack Query v5, Axios, TipTap 3.x (pinned), dnd-kit. No Redux.

## Current production/deployment status

- Fedora media server, rootless Podman Quadlet/systemd user services (PostgreSQL + NovelKMS + Caddy).
- Caddy terminates HTTPS and reverse-proxies to NovelKMS over a private Podman network.
- Persistent state outside containers: PostgreSQL volume, config/secrets, Caddy ACME state, logs, Quadlet defs.
- Backups target Dropbox as completed archives, not live database files.
- Canonical domain: `https://novelkms.com`.

## Completed feature areas

### Billing and subscriptions

Stripe-hosted Checkout/Portal, webhook sync into local `user_subscription` with idempotency/audit, configurable enforcement (`billing.enforceSubscriptions`), 402 → Settings → Billing routing. Statuses: `active`, `active_canceling`, `trialing`, `past_due`, `canceled`, `unpaid`, `paused`, `incomplete`, `incomplete_expired`, `none`, manual `family`. Details in `README.billing_and_subscriptions.md`.

### Admin support console

JAX-RS role infrastructure (`user_role`, `NovelKmsPrincipal`, `@RolesAllowed`). Admin audit logging, user lookup (identity/roles/subscription/usage), family-access grant. Minimal frontend console at `/admin`. Tests use Flyway-backed schema via `NovelKmsTestBase`.

### Manuscript structure and editing

Project → Book → Part → Chapter → Scene hierarchy. Scene-level persistence with aggregate editing (chapter/part/book). Drag-and-drop reordering/reparenting. Nav context menu, inline rename, F2 rename. Soft-delete Trash. Auto chapter/part numbering with blank-title fallbacks. Chapter reset numbering. Auto-create initial scene. Word count tracking.

### Editor and formatting

TipTap rich text editor. Paragraph styles, headings, lists, block quotes, scene breaks, inline formatting. Base64 in-editor images. Book page layout preview. Cover/part templates with token insertion and preview. Search and replace (scene/chapter/part/book scope).

### Import/export

DOCX import and export (book/part/chapter/scene). PDF export (book/part/chapter/scene) via OpenHTMLtoPDF, mirroring DOCX's standard-manuscript formatting rather than the live Style cascade. Uses PDFBox standard fonts (WinAnsi-only; no embedded font file). Markdown and ePub planned; ePub menu wiring needs verification.

### AI workflow

- BYOK credentials, three providers: OpenAI (`gpt-5.4`), Anthropic (`claude-sonnet-4-6`), Gemini (`gemini-2.5-flash`). Per-user keys encrypted at rest. Shared `AiProvider` interface.
- Chapter/scene review → persistent AI review artifacts. Recommendation lifecycle: OPEN → DONE/DISMISSED/DEFERRED/PROMOTED. Review rail with click-to-scroll via anchor text. Codex promotion.
- System prompt split: **form** (author-editable, four-scope cascade) + **functional** (constant JSON contract). Provenance stamped per review. Current: `chapter-review-v7`.
- **Chapter memory documents** — per-chapter structured context for continuity. Template cascade. Pre-review staleness gating.
- **Chapter & book summaries** — independent of memory docs. Chapter summary = one paragraph per chapter. Book summary = synopsis from chapter summaries (never raw prose). Coverage gating via `PreBookSummaryDialog`.
- **Editorials** — per-chapter impressionistic reading (tone, genre drift, arcs, storyline). Never consumed by other AI functions. `chapter-editorial-v1`.
- **One-time author guidance** — optional free-text note per generation call, stored as provenance, not auto-cleared.
- **Per-provider AI document variants** — each AI doc family stores one document per provider. Editor toolbar provider selector. Coverage/staleness surfaces remain default-provider only (Phase 3 deferred).
- **Codex entry DOCX export/import** — individual entries exported to Word (H1 title, H3 per schema field, H2 "Description," body paragraphs) and imported back via the same round-trip contract. Direct-save, no preview. Endpoints: `GET/POST /api/scenes/{sceneId}/codex-docx`.
- **Codex entry AI fill-in** — fills structured fields and body using chapter summaries as manuscript context and pinned codex entries as reference. Scope-aware: book-scoped codex uses that book's summaries; project-scoped (series-wide) codex uses all books in the project. Chapter summaries required — hard-fail with `no_chapter_summaries` if absent. Result applied to form without intermediate save. Endpoint: `POST /api/scenes/{sceneId}/codex-fill`. Prompt version: `codex-fill-v1`.

### Codex and Trash

Codex categories/entries used as AI promotion targets. Structured fields (V33): `field_schema` JSON on categories, `structured_data` on scenes, `feedsAi` flag. Trash: soft-delete/restore/purge/empty-all. Parts keep hard-delete/promote-children behavior.

### Settings architecture

Three setting categories with uniform scope model and per-tab override toggles:

- **Document settings**: `BOOK → PROJECT → USER → SYSTEM`. Live editor + export.
- **Page layout**: `BOOK → PROJECT → SYSTEM`. Export/preview only.
- **AI form instructions**: `BOOK → PROJECT → USER → SYSTEM`.

AppBar gear → context settings (project/book) or global defaults. `EditorSettingsDialog` (three tabs). `SettingsDialog` (user-level defaults). AI settings reorganized into four subtabs (Reviews, Memory, Summary, Editorial).

### In-app help system

Help Center modal, Markdown topics auto-discovered at build time, cross-linking via `#help:topic.id`, `HelpButton` component, static validator (`npm run check-help`). 26 starter topics across 7 sections. Zero-dependency renderer.

### Artifacts (per-project file store)

Per-project file/folder tree for non-manuscript material. External blob store on host volume. Case-preserving/case-insensitive names. Streaming upload with SHA-256, 50 MB cap, per-user quota (1 GB default). Integrated Trash. Nav tree folders + center-pane Explorer. Isolated dnd-kit DndContext. Image preview modal.

### Human Review Network — Phase 1A (profiles)

Opt-in public identity for the review network (`review_profile`). Handle is the gate for all
participation. Case-preserving handle + case-insensitive unique `handle_lower`; reserved-handle
list; live availability check sharing one rule set with the write path. Genres packed as one
comma-separated column, exposed as a list. PUBLIC/HIDDEN visibility; ACTIVE/SUSPENDED moderation
status (not user-settable). Cross-user reads return 404, never 403. Full Phase 1 schema (8 tables)
landed in V38. Surface at `/app/community` with the five Phase 1 tabs; only My Profile is built.

### Human Review Network — Phase 1B (publish + My Requests)

Author publishes a chapter for human review: `POST /chapters/{chapterId}/review-requests` freezes an
immutable `review_snapshot` (scenes joined by a bare `<hr>`, word count recomputed from the assembled
HTML) and opens a `review_request` over it, in one transaction. Publish hangs off the *chapter* path
because `TenantAuthorizationFilter` only authorizes UUIDs following known manuscript segments — a
body-carried `chapterId` would be unauthorized. Lifecycle via explicit action endpoints
(pause/resume/close/withdraw); metadata editable while not WITHDRAWN/REMOVED. Republish = new request
+ new snapshot. V39 adds `review_snapshot.source_updated_at`, the spec §8.2 version marker that powers
the CURRENT/CHANGED/DELETED source state.

Frontend: "Publish for Human Review…" on the chapter nav context menu (manuscript chapters only);
`ReviewRequestDialog` (publish + edit, profile-gated); **My Requests** tab at `/app/community?tab=requests`
with status/source-state chips, snapshot viewer, and lifecycle actions. **Path-specificity fix (shipped
with 1D):** `ReviewRequestResource` moved from `@Path("/")` to `@Path("/review")` so Jersey's
class-level prefix matching no longer shadows it behind `ReviewQueueResource`; the publish endpoint
(`POST /chapters/{chapterId}/review-requests`) split into `ReviewPublishResource @Path("/")` because
the tenant filter's `chapters` segment authorization is load-bearing.

### Human Review Network — Phase 1C (reviewer queue + package + snapshot reader)

Reviewer read path — the first cross-user read in NovelKMS. `ReviewAccessService`
authorizes each read explicitly (404 never 403; author reads own any status).
`GET /review/queue` (filters genre/min-max words/sort; offset paging), `GET /review/packages/{id}`,
`GET /review/packages/{id}/snapshot`. `ReviewQueueDao` applies all exclusions in SQL
(OPEN+PUBLIC, not-own, author ACTIVE, not past `closes_at`, below `max_reviews` cap, no
block either direction); `max_reviews` enforced from here. `UserBlockDao` read-only ahead
of 1F. DTOs expose `authorHandle` only — never `authorUserId`/`sourceEntityId`; package
view carries no `contentHtml`. Frontend Review Queue tab (`/app/community?tab=queue`,
`ReviewQueuePanel`/`QueueEntryCard`); `ReviewPackageDialog` renders the frozen chapter in a
sandboxed iframe (`SnapshotFrame`) — the first cross-user render boundary. Two JUnit suites
(access matrix + HTTP/serialization contract).

### Human Review Network — Phase 1D (write / submit / receive reviews)

Completes the Phase 1 review loop: a reviewer can write, save, submit, and withdraw a
review, and an author can read the feedback they receive.

**Migration:** V41 adds `human_review.author_read_at TIMESTAMP` (nullable; NULL = unread).
This single column is the whole of Phase 1's notification model — the Reviews Received badge
is `COUNT(submitted reviews of my requests WHERE author_read_at IS NULL)`. No notification
table, no email (deferred to 1F when close/withdraw/moderation events make an inbox
worthwhile).

**Backend:** `HumanReview` model + `ReviewWritingSummary` / `ReviewReceived` DTOs.
`HumanReviewDao` — DRAFT/SUBMITTED/WITHDRAWN machine, block-filtered writing & received list
reads, `countSubmitted` (cap), `countUnreadForAuthor` (badge), ownership-guarded
`markAuthorRead`. `HumanReviewService` — two gates: `ensureCanStart` (new review: OPEN+PUBLIC,
not self, not blocked, author active) and `ensureCanWrite` (existing draft: OPEN or PAUSED).
404 for every cross-user denial; 403 only for the caller's own suspension.
`HumanReviewResource @Path("/review")`: `GET|PUT /packages/{id}/review`,
`POST .../review/submit`, `.../review/withdraw`, `GET /reviews/writing`,
`GET /reviews/received`, `GET /reviews/received/unread`,
`POST /reviews/received/{reviewId}/read`.

**Frontend:** Review editor inside `ReviewPackageDialog` (plain-text body wrapped in `<p>`,
AI-assist self-disclosure checkbox, Save/Submit/Withdraw, Revise for submitted);
`MyWritingPanel` and `ReviewsReceivedPanel`; unread badge on the Reviews Received tab.
Received review bodies render as plain text (`htmlToPlain`) — a cross-user render boundary
that needs no iframe while reviews are plain-text only.

### Human Review Network — Phase 1E (contribution metrics)

Public contribution figures (§13), derived at read time — no migration. Words reviewed =
`SUM(review_snapshot.word_count)` over the user's SUBMITTED reviews; review words written =
`SUM(human_review.word_count)`, SUBMITTED; reviews completed = `COUNT` SUBMITTED by user;
reviews received = `COUNT` SUBMITTED against the user's requests; member since =
`review_profile.created_at`. Everything comes from columns V38/V41 already froze.
`human_review.word_count` is server-computed via `WordCount.fromHtml` at save/submit, so the
metric cannot be inflated from the wire.

New `ReviewMetricsDao` (two plain-standard SELECTs) + `ProfileMetrics` DTO. `ReviewProfileResource`
gains `GET /review/profile/metrics` (self; 404 `no_profile` when absent) and
`GET /review/profiles/{handle}/metrics` (cross-user; 404 for absent/hidden/suspended). Both
cross-user reads share one private `readableByHandle` gate so the profile view and its metrics
cannot drift — the single place a 1F block-hide rule slots in.

Figures are **objective, not viewer-relative** (§6.5): reviews-received is NOT block-filtered,
unlike the Reviews Received list. Public/private split and recent-activity are deferred — every
Phase 1 review is PRIVATE (`HumanReviewService.submit` hardcodes it), so a public count would read
zero for everyone. Frontend: Contribution stat block on My Profile (self only) via
`useMyReviewProfileMetrics`; the cross-user endpoint ships backend-ready and tested but has no UI
consumer until a public-profile page or queue click-through exists.

### Human Review Network — Phase 1F (blocking, reporting, admin removal)

The final Phase 1 slice. No migration — V38 already froze `user_block` and
`content_report`; 1F is the code that finally writes and reads them.

**User-facing safety.** `ReviewSafetyResource @Path("/review")` (active-profile gated:
409 `profile_required` / 403 suspended): `GET/POST /review/blocks`,
`DELETE /review/blocks/{handle}` (block idempotent both ways, block-self 400), and
`POST /review/reports`. A report targets REQUEST | REVIEW | PROFILE (PROFILE by
`targetHandle`, resolved to a profile id server-side; no bare USER target); reasons are
SPAM | HARASSMENT | COPYRIGHT | HATE | EXPLICIT | OTHER; `detail` ≤ 2000 chars. Reports
are file-and-forget — the reporter cannot list their own.

**Admin moderation.** `AdminModerationResource @Path("/admin/moderation")
@RolesAllowed(ADMIN)`: `GET /reports?status=&limit=` (rows are `ContentReportView`,
reporter shown by handle only), `POST /reports/{id}/resolve|dismiss`,
`POST /requests/{id}/remove`, `POST /reviews/{id}/remove`,
`POST /profiles/{handle}/suspend|reinstate` — all `{reason, note}`, all audited.
Removing a request/review or suspending a profile auto-resolves that target's OPEN
reports. Cross-user denial is 404 everywhere; everything on the wire is by handle.

**Frontend.** `api/reviewSafety.js` + `hooks/useReviewSafety.js` (blocks list;
block/unblock/report mutations, block-invalidation scoped to blocks + queue + received +
writing); `utils/reviewReportReasons.js` (stable-key → label map); `ReportDialog`
(shared, reason select + 2000-char detail); `ReviewCardMenu` (the ⋯ overflow — block +
report — dropped into all four card surfaces: queue, package dialog, Reviews I'm
Writing, Reviews Received). The ⋯ trigger is a glyph, not an icon import (no MoreVert
proven present; an absent icon fails the Rolldown build). My Profile gains a self-only
"Blocked writers" list with unblock. The admin console gains a Moderation tab
(`AdminModerationPanel`): a status-filtered report queue with resolve/dismiss and
content removal, plus a handle-keyed profile suspend/reinstate tool. Received-review and
report bodies stay plain text; no new render boundary.

### Extensible Codex

E6 shipped (2026-07-21). Type-editor field removal is now
non-destructive — removing a field hides it from the entry form while its
values are preserved in structured_data, with a "Removed fields" area and
Restore. Endpoints: DELETE/POST .../fields/{key}[/restore], GET .../fields/usage
(all fields + entry counts). Remaining Extensible Codex phases: E7 (per-instance
seeding + type→Trash), E8 (DOCX/AI-promotion against per-instance types),
E9 (terminology sweep + full living-doc pass).

E8 shipped (2026-07-21). DOCX round-trip and AI-promotion now
honor per-instance Types. Export/import resolve fields from the entry's own Type
(codex_type_field), not the retired global schema. Promotion accepts an optional
codexTypeId so the author can promote into any project Type (including
author-created ones); without it, the AI's broad category maps to the seeded Type
by system_key. The promotion path now seeds per-instance fields when it creates a
Type (E7 parity). No migration. Remaining: E9 (terminology sweep + full living-doc
pass).

## Known issues / watchlist

- Billing: extend trial, revoke-family semantics, plan mapping, webhook diagnostics, Stripe reconciliation.
- ePub export menu/API wiring needs verification.
- Style-editor UI deferred.
- Full-book TipTap performance unverified with large manuscripts.
- `SearchResource`/`SearchService` may be obsolete; verify before removing.
- AI review is synchronous; async execution needed before part/book-level reviews.
- Deferred AI findings need a deliberate view.
- Codex promotion is direct-to-Codex; consider a draft/suggestion layer.
- Frontend Phase 2 cleanup: book-aware editor render, remove DocSettingsPopover, reduce global dialog, clean Properties panel.
- Delete confirmation dialog wording ("cannot be undone" → "move to trash"; codex entries called "scenes").
- Help topics are seed content; expand as product matures.
- Artifacts: blob dir must be in backup set; restore de-dup appends "(n)" to whole name; nav-pane folder drag deferred.
- Review network: slices 1A–1F shipped. Phase 1 is complete (profiles, publish + My
  Requests, reviewer queue + package + snapshot reader, write/submit/receive,
  contribution metrics, blocking/reporting/admin removal). `user_block` and
  `content_report` are now written/read by the safety + moderation endpoints.
  `review_context_item` remains unpopulated until Phase 2. **Follow-up:** snapshot HTML
  is still sanitized only at the render boundary (sandboxed iframe); consider
  capture-time sanitization once a safelist can be validated against real TipTap markup.
- Review network: §30.2 Q5 participant-read of paused/closed packages still not wired —
  `ReviewAccessService.authorizeRead` requires OPEN+PUBLIC for non-authors. Own slice.
  
## Near-term next actions

1. Admin billing: revoke-family semantics, plan mapping, webhook diagnostics.
2. Frontend Phase 2 cleanup.
3. ePub export repair.
4. Deferred AI findings view.
5. Style-editor UI.
6. Provider-variants Phase 3: provider-aware coverage/staleness, review-history grouping, fallback note.

## Documentation maintenance rule

Keep this file short. Detailed code, schema, and architectural rationale go in `Architecture_and_Design_Decisions_by_Phase.md`.
