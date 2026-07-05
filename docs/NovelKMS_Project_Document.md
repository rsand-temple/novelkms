# NovelKMS — Running Project Document

This is the current project-status document. Keep it concise. Historical implementation details belong in `Architecture_and_Design_Decisions_by_Phase.md`; deployment details belong in the README operation documents.

## Vision

NovelKMS is a novel knowledge-management system with a manuscript editor attached. Manuscript text, Codex, project metadata, templates, styles, AI review history, and future character/canon/timeline records are first-class project entities.

The immediate goal is practical validation: determine whether NovelKMS manages a real long-form novel project better than Dabble, NovelCrafter, Scrivener, or ad hoc Markdown/Word workflows.

## Repository

- Repository: `https://github.com/rsand-temple/novelkms`
- Branch: `master`
- Architecture: Maven multi-module (backend, frontend, distro).
- Dev workflow: Eclipse for backend, Vite dev server for frontend, H2 locally, PostgreSQL hosted.

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

DOCX import and export (book/part/chapter/scene). Markdown and ePub planned; ePub menu wiring needs verification.

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

## Near-term next actions

1. Admin billing: extend trial, revoke-family semantics, plan mapping, webhook diagnostics.
2. Frontend Phase 2 cleanup.
3. ePub export repair.
4. Deferred AI findings view.
5. Style-editor UI.
6. Provider-variants Phase 3: provider-aware coverage/staleness, review-history grouping, fallback note.

## Documentation maintenance rule

Keep this file short. Detailed code, schema, and architectural rationale go in `Architecture_and_Design_Decisions_by_Phase.md`.
