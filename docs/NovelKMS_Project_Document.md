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

- Review network: only slice 1A (profiles) shipped; requests/snapshots/queue/reviews/metrics/moderation
  tables exist but are unused. `review_context_item` is unpopulated until Phase 2.
- `UserPreferenceResource` uses `@Context` field injection — works only because it is registered by
  class; would fail any resource test. Convert to method-parameter form if it ever gets one.

## Near-term next actions

1. Admin billing: extend trial, revoke-family semantics, plan mapping, webhook diagnostics.
2. Frontend Phase 2 cleanup.
3. ePub export repair.
4. Deferred AI findings view.
5. Style-editor UI.
6. Provider-variants Phase 3: provider-aware coverage/staleness, review-history grouping, fallback note.
7. Review network slice 1B: publish chapter → request + snapshot; My Requests.

## Documentation maintenance rule

Keep this file short. Detailed code, schema, and architectural rationale go in `Architecture_and_Design_Decisions_by_Phase.md`.
