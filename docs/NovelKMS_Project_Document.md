# NovelKMS — Running Project Document

This is the current project-status document. It should stay concise and be updated after substantial implementation sessions. Historical implementation details belong in `Architecture_and_Design_Decisions_by_Phase.md`; deployment details belong in the README operation documents.

## Vision

NovelKMS is a novel knowledge-management system with a manuscript editor attached. The manuscript, Codex, project metadata, templates, styles, AI review history, and future character/canon/timeline records are intended to be first-class project entities.

The immediate goal is still practical validation: determine whether NovelKMS manages a real long-form novel project better than Dabble, NovelCrafter, Scrivener, or ad hoc Markdown/Word workflows.

## Repository

- Repository: `https://github.com/rsand-temple/novelkms`
- Branch: `master`
- Architecture: Maven multi-module project with backend, frontend, and distro modules.
- Development workflow: Eclipse for Dropwizard/backend, Vite dev server for frontend, H2 for local development, PostgreSQL for the hosted deployment.

## Technology stack

### Backend

- Java 17+
- Dropwizard / Jersey
- Flyway migrations
- JDBC DAO layer with explicit SQL and row mappers
- Apache DBCP2 connection pooling
- H2 for local development
- PostgreSQL 17 for hosted deployment

### Frontend

- React + Vite
- Material UI v6
- React Router
- TanStack Query v5
- Axios via shared API client
- TipTap 3.x editor packages pinned to matching versions
- Redux intentionally avoided unless future complexity justifies it

## Current production/deployment status

- Public deployment runs on the Fedora media server as rootless Podman Quadlet/systemd user services.
- Services include PostgreSQL, NovelKMS, and Caddy.
- Caddy terminates HTTPS and reverse-proxies to the NovelKMS container over the private Podman network.
- PostgreSQL is the hosted production database; H2 remains useful for local development and migration source/backup scenarios.
- Persistent state is outside disposable containers: PostgreSQL volume, config/secrets, Caddy ACME state, logs, and Quadlet definitions.
- Backups target Dropbox as completed archives, not live database files.
- The canonical public domain should be `https://novelkms.com`; legacy aliases such as `www.novelkms.com` and `novelkms.richardsand.com` should redirect to the canonical domain when Caddy is configured that way.

## Completed feature areas

### Billing and subscriptions

- Stripe-hosted Checkout creates subscriptions from authenticated NovelKMS sessions.
- Stripe Customer Portal supports user-managed billing and cancellation.
- Webhooks synchronize Stripe lifecycle events into local `user_subscription` state with `stripe_webhook_event` idempotency/audit.
- Subscription enforcement is controlled by `billing.enforceSubscriptions`; blocked users receive `402 subscription_required` and the frontend routes them to Settings → Billing.
- Current entitlement statuses include `active`, `active_canceling`, `trialing`, `past_due`, `canceled`, `unpaid`, `paused`, `incomplete`, `incomplete_expired`, `none`, and manual `family`.
- Active scheduled cancellation is represented locally as `active_canceling`; cancellation date, feedback, comment, reason, and request timestamp are preserved.
- Detailed billing implementation notes are in `README.billing_and_subscriptions.md`.

### Admin support console and admin security

- Admin authorization now uses first-class backend role infrastructure: `user_role`, `NovelKmsPrincipal`, JAX-RS `SecurityContext`, and `@RolesAllowed(Roles.ADMIN)`.
- `/api/auth/status` returns the authenticated user's roles so the frontend can expose admin navigation only to administrators.
- Admin audit logging is implemented through `admin_audit_log`, `AdminAuditDao`, and `/api/admin/audit/*` endpoints. Admin mutations are expected to write old/new values and a reason.
- Admin user lookup is implemented through `/api/admin/users`, including identity, roles, subscription summary, and usage counts.
- Admin billing support currently includes `/api/admin/billing/users/{userId}` and `POST /api/admin/billing/users/{userId}/family-access`.
- The minimal frontend admin support console lives at `/admin`: search users, inspect identity/billing/usage/audit, and grant family access with reason/note.
- Tests now use Flyway-backed schema via `NovelKmsTestBase` where practical; the base truncation order includes admin audit and role tables.

### Manuscript structure and editing

- Project, book, part, chapter, and scene hierarchy.
- Scene-level persistence with aggregate editing for chapter, part, and book scopes.
- Drag-and-drop reordering/reparenting for parts, chapters, and scenes.
- Nav context menu, inline rename, F2 rename support.
- Delete workflow evolved into soft-delete Trash for supported root types.
- Automatic chapter/part numbering with blank-title fallbacks.
- Chapter reset numbering.
- Auto-create initial scene when a chapter is created.
- Word count tracking and repair endpoint.

### Editor and formatting

- TipTap rich text editor.
- Paragraph styles and style-keyed paragraphs.
- Headings, lists, block quotes, scene breaks, inline formatting.
- In-editor manuscript images using base64 image nodes.
- Book page layout settings and page previews.
- Cover and part templates with token insertion and preview.
- Cover image storage on books.
- Search and replace across scene/chapter/part/book scope.

### Import/export

- DOCX import.
- DOCX export for book, part, chapter, and scene.
- Markdown import/export remains planned.
- ePub export is a current priority; verify current backend/menu wiring before marking complete.

### AI workflow

- BYOK AI credentials.
- OpenAI provider first, behind provider abstraction.
- Per-user API keys encrypted at rest.
- Chapter and scene review workflows create persistent AI review artifacts.
- Recommendation lifecycle: `OPEN`, `ACCEPTED`, `REJECTED`, `FUTURE`, `DELETED`, `PROMOTED`.
- AI review rail in the editor area with click-to-scroll via anchor text.
- Recommendations can be promoted into Codex entries.
- AI review system prompt split into **form** (editorial persona/constraints, author-editable) and **functional** (JSON output contract, constant). Form instructions are independently overridable at book, project, user-global, and system-default scopes with single-block selection (no inheritance, no concatenation). Each review records the form text and source scope as immutable provenance.
- Prompt version `chapter-review-v4` marks the form/functional externalization.
- **Chapter & book summaries (V25).** A new AI artifact family, fully independent of memory documents (V24) — separate tables, prompts, DAOs, generation paths, and UI. 
  - A **chapter summary** is one human-readable paragraph per chapter (`chapter-summary-v1`), generated from the chapter prose and optionally hand-edited (marks it `EDITED`). 
  - A **book summary** is a whole-book synopsis of up to ~1000 words (`book-summary-v1`), generated *entirely* from the chapter summaries concatenated in book order — never the manuscript prose, since a full book is too large to summarize reliably in one pass. Both are one-per-parent and overwrite on regenerate, like memory docs. 
  - Right-clicking a book → **View chapter summaries…** opens a dialog with the read-only aggregated chapter summaries (each with a per-chapter staleness chip) plus the book-summary panel (generate / regenerate / edit). 
  - Generating the book summary is gated by a coverage warning (`PreBookSummaryDialog`) when chapters are missing or stale, offering to fill the gaps first. Chapter summaries are created/edited/cleared from the chapter nav context menu.
- **One-time author guidance (V26).** Every generation flow — chapter/scene review, memory document, chapter summary, book summary — now takes an optional free-text guidance note for that single run only (e.g. "the letter in this chapter is canonically a forgery"), separate from the persistent form/template overrides and from the still-future Codex-context increment. Stored as provenance on the resulting artifact; the UI field pre-fills from whatever guidance produced the current artifact and is never auto-cleared, so guidance can be repeated or refined across runs. Prompt versions bumped accordingly (`chapter-review-v6`, `memory-v2`, `chapter-summary-v2`,`book-summary-v2`).


### Codex and Trash

- Codex categories/entries exist and are used as promotion targets for AI findings.
- Trash supports soft-delete, restore, purge, and empty-all semantics for the supported root types.
- Parts intentionally keep their special hard-delete/promote-children behavior and are not Trash roots.

### Settings architecture

- Three categories of project/book settings share a uniform scope model with per-tab override toggles (off = inherit, on = copy-on-write override):
  - **Document settings** (font, line height, indents, spacing, scene break): `BOOK → PROJECT → USER → SYSTEM`. Renders live in the editor and affects export.
  - **Page layout** (paper size, margins, enabled flag): `BOOK → PROJECT → SYSTEM`. Affects export/preview only — never the live editor. Stored as typed columns in a dedicated `page_layout` table (V22); the former `book` columns were migrated and dropped.
  - **AI form instructions**: `BOOK → PROJECT → USER → SYSTEM`. System default is a non-editable Java constant; user global is editable; project and book are optional standalone overrides.
- AppBar gear opens a menu: context settings (project or book by current selection) and global defaults.
- The context `EditorSettingsDialog` has three tabs (Document / Page Layout / AI), each with its own override toggle.
- The global `SettingsDialog` holds user-level defaults (Document, AI credentials + global review instructions, Other).

## Known issues / watchlist

- Billing/admin support now has a minimal console and family-access grant flow. Remaining billing work: extend trial, revoke/remove family access with a defined restoration policy, plan mapping, webhook diagnostics, and eventual Stripe reconciliation.
- ePub export menu/API wiring needs verification because the menu entry appears to have regressed.
- Style-editor UI is deferred; styles exist but need full edit/override/reset UX.
- Full-book TipTap performance should be profiled with large and image-heavy manuscripts.
- Backend `SearchResource` / `SearchService` may be obsolete for editor search; remove only after verifying no callers remain.
- AI review execution is synchronous; part/book-level review workflows should use async execution and polling.
- Deferred AI findings need a deliberate future/revisit view.
- Codex promotion is currently direct-to-Codex; consider a draft/suggestion layer before entries become canonical.
- Backup/restore should be periodically tested, not merely scheduled.
- Frontend Phase 2 cleanup remains: point EditorPanel at book-resolved document settings for live render, remove DocSettingsPopover + its toolbar trigger, reduce the global SettingsDialog Document tab to user-only, strip Properties panel to book metadata + cover (remove Page Layout and AI sections), drop ignored page-layout fields from BookResource.UpdateRequest.
- Delete confirmation dialog wording update: change "cannot be undone" language to "move to trash," and fix codex entries being referred to as "scenes" in that dialog.

## Near-term next actions

1. Continue admin billing functionality: extend trial, decide revoke-family/manual-override semantics, add plan mapping, webhook diagnostics, and subscription reconciliation.
2. Frontend Phase 2: book-aware editor render, remove old doc-settings popover, reduce global dialog to user-only, clean Properties panel to metadata + cover.
3. Restore or finish ePub export in the menus and verify the backing endpoint.
4. Add a Future/deferred AI findings view.
5. Add style-editor UI.
6. Begin enriching AI prompts with selected Codex/context data (Phase C per-chapter memory artifact).
7. Summary-prompt template editors (four-scope, like memory templates) were deliberately deferred — V25 uses fixed system-default prompts.

## Documentation maintenance rule

Keep this file short. When a session produces detailed code, schema, or architectural rationale, add that material to `Architecture_and_Design_Decisions_by_Phase.md` and summarize only the resulting state here.
