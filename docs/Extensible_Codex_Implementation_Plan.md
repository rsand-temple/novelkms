# Extensible Codex — Implementation Plan & Living Memory

**This is the authoritative working document for building the Extensible Codex feature.**
It is a companion to `Extensible Codex Design.md` (the conceptual design). Where the two
conflict, **this document wins** — the conceptual doc predates the live V33 schema and two of
its recommendations were deliberately overridden (see *Reconciliation* below).

The feature ships in small, self-contained phases (E1…E9) so each can be completed inside a
single thread without hitting resource limits. **After finishing any phase, update the Status
Dashboard and append a dated entry to the Changelog at the bottom.** A future thread should be
able to start any phase from this file alone.

---

## How to use this doc

1. Read *Confirmed Decisions*, *Live Model (as-is)*, and *Target Model* once — they are the
   invariants for the whole feature.
2. Find the first phase whose status is not **Done** in the Status Dashboard.
3. Execute exactly that phase using its *Goal / Changes / Files / Done-when / Verification /
   Handoff notes* block. Do not scope-creep into the next phase.
4. Follow the standard workflow: propose numbered decisions if anything is genuinely open →
   fetch current files from `master` before editing → deliver **complete files** →
   static-verify → stage under repo-relative paths → zip to `/mnt/user-data/outputs/`.
5. Update this doc (dashboard + changelog). At the end of the feature (E9), do the full
   DELIVERED pass across the three living docs.

Repo: `https://github.com/rsand-temple/novelkms`, branch `master`. Next free migration: **V42**.

---

## Status Dashboard

| Phase | Scope | Layer | Status |
| ----- | ----- | ----- | ------ |
| E1 | Migration V42: `codex_type_field` table + `chapter.codex_type_description`; backfill fields from seeded schemas | Backend (SQL only) | **Done** |
| E2 | `CodexTypeFieldDao` + `CodexType` DTO + `GET /codex/types/{typeId}` (read-only) | Backend | **Done** |
| E3 | Cutover: entry form + AI fill read the per-instance Type schema; `/codex/categories` becomes seed/promotion-only | Backend + Frontend | **Done** |
| E4 | Type-editor write path: create/edit type, add/rename/reorder/change-style fields; immutable-key generator | Backend | **Done** |
| E5 | Type-editor UI: Manage Types surface, type editor, field editor, entry-create type picker | Frontend | Not started |
| E6 | Field soft-remove / restore (non-destructive), removed-fields area, entry-count warning | Backend + Frontend | Not started |
| E7 | New-codex seeding stamps per-instance fields; type→Trash carries fields+entries; restore together | Backend | Not started |
| E8 | DOCX round-trip + AI promotion against per-instance types (`system_key` mapping + author picks type) | Backend + small Frontend | Not started |
| E9 | Terminology sweep (UI "Category"→"Type") + full DELIVERED living-doc pass | Frontend + docs | Not started |

Legend: **Not started** → **In progress** → **Done**. Record commit note in the Changelog when a phase reaches Done.

---

## Confirmed Decisions (locked)

1. **The category chapter row *is* the Type.** No standalone `codex_type` table. A category
   instance is already a `chapter` row (`codex_id` set, `book_id` NULL), already project/book
   scoped, ordered, Trash-rooted, and the parent of its entry scenes. We give it its own schema
   instead of borrowing the global one. *Overrides conceptual §8's standalone-table model.*
2. **Fields are normalized rows** in a new `codex_type_field` table (FK to the category chapter
   id), replacing `field_schema` JSON as the source of truth. This is what enables field-level
   soft-delete/restore, usage counts, and future cross-project copy.
3. **Immutable field keys; existing keys preserved verbatim.** New fields get `slug + '_' +
   4-hex` (e.g. `wingspan_7f3a`). The migration keeps existing keys (`role`, `age`, `summary`,
   …) **exactly** or every existing `scene.structured_data` value orphans.
4. **Keep the existing three field types**, not the doc's two: `SHORT_TEXT`, `LONG_TEXT`,
   `SELECT` (+ `options`, `help`, `feedsAi`). Map doc SINGLE_LINE→SHORT_TEXT,
   MULTI_LINE→LONG_TEXT. `feedsAi` is load-bearing for AI reference context and stays.
   *Overrides conceptual §3's two-type boundary.*
5. **`system_key` = existing `chapter.codex_category`.** Seeded types carry their key
   (CHARACTER, VOICE, …) for AI-promotion mapping; author-created types have it NULL. Renaming
   a type changes its title, never its `codex_category`. **No new column** is added for this.
6. **Migration copies the current global schema onto each instance** as its own editable
   normalized field set, preserves keys, leaves all entries / `structured_data` / order /
   pinning / Trash untouched. The master `codex_category` table demotes from live-schema
   authority to *seed template + promotion-mapping source*.
7. **Field soft-remove/restore lives in the type editor, not top-level Trash.** `deleted_at`
   on `codex_type_field`; values preserved in `structured_data`; restore re-shows; entry-count
   warning when values exist. Type *deletion* still uses existing Codex Trash.
8. **UI says "Type"; code/columns stay `codex_category` / `codex_id`.** No rename migration;
   only user-facing labels change on surfaces we touch.
9. **Slice boundary — ship the coherent minimum.** In scope: project-scoped editable types,
   normalized fields + immutable keys, add/rename/reorder/soft-remove fields, dynamic entry
   form, type Trash, migration, AI-fill/promotion/DOCX re-pointed at per-instance schema.
   **Deferred:** cross-project copy (§19), type library (§20), new field types beyond the three
   (§21), entry type-change (§10.7), permanent field-value purge.
   **AI promotion keeps the §14 compatibility layer:** AI returns a broad category, we map it to
   the project's type by `system_key`, and the promotion dialog lets the author pick a different
   project type. "Prompt includes the project's actual types" is deferred to a later prompt
   version.

---

## Live Model (as-is) — facts a phase must not break

- **Container:** `codex` table, scoped to exactly one project **or** one book (CHECK; unique
  index per scope). One codex per project, one per book. `CodexDao`, model `Codex`.
- **Category instance = chapter row:** `chapter` with `codex_id` set, `book_id` NULL,
  `codex_category VARCHAR(50)` = key into the master lookup, `title` = the type name. Added by
  V13. Chapter also has `deleted_at`/`deleted_batch_id` (Trash, V16).
- **Master category lookup:** `codex_category` table (V13) — `category_key` PK, `label`,
  `display_order`, `icon`, `is_default`; **`field_schema TEXT` added by V33**. Seven seeded
  keys: CHARACTER, VOICE, PLOT, WORLD, TIMELINE, CANON, NOTES. **Schema here is system-global
  by key** — the exact limitation this feature removes. `CodexCategoryDao` is **read-only**
  today, so no per-instance schema customization exists in any database yet — only the two
  seeded schemas (CHARACTER, VOICE) are live.
- **Entry = scene row:** `scene.structured_data TEXT` (V33) = JSON object keyed by field key,
  e.g. `{"role":"Protagonist","age":"42",...}`. Keys are today human-readable and static.
- **Field types in production:** `SHORT_TEXT`, `LONG_TEXT`, `SELECT` with `options`, `help`,
  `feedsAi`. Models: `CodexSchema`, `CodexField`. Only CHARACTER (12 fields) and VOICE
  (10 fields) ship a schema; the exact field definitions live in
  `db/migration/{h2,postgresql}/V33__codex_schema.sql` — treat that file as the source of the
  backfill literals in E1.
- **Schema reaches the entry form globally:** frontend `CodexEntryFields.jsx` takes a `schema`
  prop; today it comes from `GET /codex/categories` (all master rows, with parsed schema)
  matched by the chapter's `codex_category` key. The form is *schema-source-agnostic* — it
  renders whatever `schema` object it's handed, so E3's cutover is a resolution change in the
  hooks, not a rewrite of the form.
- **Seeding:** `CodexResource.seed…` iterates `codexCategoryDao.findDefaults()` and creates one
  category chapter per default with `chapterDao.createCodexChapter(codexId, key, label)`.
- **AI fill** reads the schema and returns `{"fields":{key:value},"body":"..."}` (`codex-fill-v1`,
  `CodexAiService`). **AI promotion** targets broad categories (CHARACTER…NOTES).
- **DOCX round-trip** (`CodexExportService`): H1 title → H3 per field label → Normal values →
  H2 "Description" → body; import matches H3 labels case-insensitively, skips unknown.

### Key source files (verified against `master`)
```
backend model      model/codex/{Codex,CodexCategory,CodexField,CodexSchema}.java
backend dao        dao/codex/{CodexDao,CodexCategoryDao}.java
backend resource   resource/codex/{CodexResource,CodexEntryResource}.java
backend service    service/{CodexAiService,CodexExportService}.java
backend ai dto     ai/{CodexFillRequest,CodexFillResult}.java
migrations         resources/db/migration/{h2,postgresql}/{V13,V15,V33}__*.sql
frontend api       src/api/{codex.js,codexEntry.js}
frontend hooks     src/hooks/{useCodex.js,useCodexEntry.js}
frontend codex     src/components/codex/{CodexEntryFields,CodexFillDialog}.jsx
frontend nav       src/components/nav/{CodexItem,CodexSection,CodexCategoryItem}.jsx
frontend nav dlg   src/components/nav/dialogs/AddCodexEntryDialog.jsx
```

---

## Target Model

```
codex (project- or book-scoped)                       [unchanged]
  └── Codex Type  = chapter row (codex_id set)         [unchanged storage; now owns its schema]
        ├── system_key = chapter.codex_category        [existing column; NULL for author types]
        ├── name       = chapter.title
        ├── description= chapter.codex_type_description [NEW column, V42]
        ├── fields     = codex_type_field rows          [NEW table, V42 — source of truth]
        └── entries    = scene rows                      [unchanged; structured_data keyed by field_key]
```

`codex_category` (master) survives as: the list of **seedable default types** and the
**AI-promotion `system_key` map**. It is no longer read as live schema after E3.

### New table (spec — implement in E1, both dialects byte-identical)
```
codex_type_field (
  id            UUID        NOT NULL PRIMARY KEY,
  chapter_id    UUID        NOT NULL,      -- the Type (category chapter row)
  field_key     VARCHAR(80) NOT NULL,      -- immutable; existing keys preserved verbatim
  label         VARCHAR(200) NOT NULL,     -- user-editable
  input_type    VARCHAR(20) NOT NULL,      -- SHORT_TEXT | LONG_TEXT | SELECT
  options       TEXT,                      -- JSON array for SELECT; NULL otherwise
  help          TEXT,
  feeds_ai      BOOLEAN     NOT NULL DEFAULT TRUE,
  display_order INT         NOT NULL DEFAULT 0,
  created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    TIMESTAMP                  -- soft-remove (E6); NULL = active
)
FK  chapter_id -> chapter(id) ON DELETE CASCADE
UNIQUE (chapter_id, field_key)
INDEX  (chapter_id)
```
Plus: `ALTER TABLE chapter ADD COLUMN codex_type_description TEXT;` (one ALTER per column, H2 rule).

---

## Cross-cutting invariants (hold in every phase)

- Both dialect migration files identical where every type is common to H2 + PostgreSQL (V38
  pattern). H2 test DB is DEFAULT mode (no `MODE=PostgreSQL`) — keep SQL plain-standard.
- One `ALTER TABLE … ADD COLUMN` per column (H2).
- Boxed types for required update DTO fields; every update path echoes all persisted fields.
- Never trust `scene.word_count`; never let a codex category (`codex_id` set / `book_id` NULL)
  leak into a chapter-consuming manuscript feature — the existing `not_manuscript` and
  `isManuscriptNode` guards must keep rejecting types.
- `feeds_ai` filtering: private fields (`feeds_ai=false`, e.g. author notes) must stay out of
  every AI prompt, exactly as today.
- Tenant authorization: all codex paths route through project/book/`scenes/{id}` segments
  already covered by `TenantAuthorizationFilter`; new type endpoints must hang off an
  authorized segment (`/codex/{id}` or `/codex/types/{typeId}` where typeId is a chapter id
  guarded like any chapter). Confirm the filter's segment handling when adding routes.
- Immutable-key rule (Decision 3) is the safety mechanism for the whole feature — never
  regenerate an existing key on rename/reorder/type-change.

---

## Phases

### E1 — Migration V42 (schema + backfill). Backend, SQL only. No behavior change.
- **Goal:** land `codex_type_field` + `chapter.codex_type_description`, and backfill one field
  row per existing category instance from the seeded schemas — without changing any app read path.
- **Changes:** create both dialect `V42__codex_types.sql` files. DDL per the table spec above.
  Backfill by **INSERT…SELECT with literal field values** (do *not* parse JSON in SQL): for each
  existing `chapter` row with `codex_id IS NOT NULL AND codex_category = 'CHARACTER'`, insert the
  12 CHARACTER fields (keys/labels/types/options/help/feedsAi/order taken verbatim from
  `V33__codex_schema.sql`); same for VOICE's 10 fields. Other categories seed no field rows
  (they were schema-less). Preserve `display_order` = array order from V33.
- **Files:** `resources/db/migration/{h2,postgresql}/V42__codex_types.sql`.
- **Done-when:** H2 V1→V42 replay clean; after replay, each CHARACTER instance has 12 active
  field rows and each VOICE instance has 10, keys matching V33 exactly; `structured_data`
  untouched.
- **Verification:** download H2 jar, replay all migrations, `SELECT chapter_id, COUNT(*)` per
  type; spot-check keys equal V33. Static SQL bind check.
- **Handoff notes:** record actual field counts and any category with pre-existing instances in
  the Changelog. Nothing reads the new table yet — that is E2/E3.

### E2 — `CodexTypeFieldDao` + `CodexType` DTO + read endpoint. Backend, read-only.
- **Goal:** expose per-instance fields without cutting over the live form yet.
- **Changes:** `CodexTypeFieldDao.findActiveByType(chapterId)` /
  `findAllByType(chapterId)` (active vs including removed), mapping `options` JSON → `List<String>`.
  New DTO `CodexType { UUID id; String name; String description; String systemKey; List<CodexField> fields; }`
  assembled from the chapter row + its active fields. New `GET /codex/types/{typeId}` on
  `CodexResource` (or a new `CodexTypeResource`) returning `CodexType`. `CodexField` already
  exists and is reused verbatim.
- **Done-when:** endpoint returns fields identical to what `/codex/categories` yields today for
  the same seeded type.
- **Verification:** DAO unit test on a Flyway-backed schema (`NovelKmsTestBase`); assert
  round-trip of a SELECT field's options.
- **Handoff notes:** confirm the chosen route segment is tenant-authorized (typeId is a chapter
  id). Old global path is still authoritative until E3.

### E3 — Cutover to per-instance schema. Backend + Frontend.
- **Goal:** the live entry form and AI fill read the **instance's** fields; `/codex/categories`
  schema becomes seed/promotion-only.
- **Changes (frontend):** `useCodex`/`useCodexEntry` resolve the entry's schema from
  `GET /codex/types/{typeId}` (E2) rather than matching the global categories lookup by key;
  `CodexEntryFields` keeps its `schema` prop contract (no form rewrite).
- **Changes (backend):** `CodexAiService` assembles field labels/values from the instance's
  fields, not the global master. Keep `feeds_ai` filtering intact.
- **Done-when:** existing CHARACTER/VOICE entries render the same fields as before; AI fill
  returns the same shape; schema-less types still render title+body.
- **Verification:** esbuild transform on touched JS/JSX; backend static check; manual entry
  render diff.
- **Handoff notes:** after E3 the per-instance table is the source of truth. Note in Changelog
  that `/codex/categories` is now consumed only for seeding + promotion mapping.

### E4 — Type-editor write path. Backend.
- **Goal:** author can create/edit types and add/rename/reorder/change-style fields via API.
- **Changes:** immutable-key generator util (`slug(label) + '_' + 4-hex`, unique within type,
  never regenerated on edit). `CodexTypeFieldDao` write methods: `addField`, `renameLabel`,
  `reorder`, `changeInputType` (SHORT_TEXT↔LONG_TEXT↔SELECT; SELECT carries `options`).
  Type-level: create a type = create a category chapter row with `codex_category` NULL
  (author type) + optional seeded fields; update `title`/`codex_type_description`. Endpoints on
  `CodexTypeResource`. Update DTOs use boxed types; echo all fields.
- **Done-when:** rename keeps `field_key` stable and existing `structured_data` values still
  resolve; reorder only changes `display_order`; switching to SELECT persists `options`.
- **Verification:** DAO tests for key-stability-across-rename and options round-trip.
- **Handoff notes:** soft-remove is NOT here — it is E6. Author-created types have NULL
  `system_key`; make sure promotion/seed code tolerates NULL.

### E5 — Type-editor UI. Frontend.
- **Goal:** "Manage Codex Types" surface + type editor (§6.2) + field editor (§6.3) +
  entry-create type picker (§6.4, if the current AddCodexEntry flow doesn't already pick).
- **Changes:** new dialogs/panels wired to E4 endpoints; drag-reorder fields (dnd-kit, string
  IDs); field editor asks only Label + Input style (+ options for SELECT). MUI notes: flat
  `MenuItem` children; Stack `alignItems`/`justifyContent` in `sx`; only icons proven present;
  no manual memo if React Compiler active.
- **Done-when:** create a Dragon type with fields, add an entry, fill fields, values persist.
- **Verification:** esbuild transform; manual flow.
- **Handoff notes:** keep "Type" wording here (Decision 8); full terminology sweep is E9.

### E6 — Field soft-remove / restore. Backend + Frontend.
- **Goal:** non-destructive field removal (§10.5/§17).
- **Changes:** `deleted_at` writes + restore on `codex_type_field`; active reads exclude
  removed; a "Removed fields" area inside the type editor lists soft-deleted fields with
  entry-count and Restore. Entry-count = COUNT of entries whose `structured_data` contains the
  key. Warning dialog before removal when count > 0. Values are never stripped from
  `structured_data`.
- **Done-when:** remove hides the field from the form but the value survives in
  `structured_data`; restore re-shows it with its value.
- **Verification:** DAO test (remove→value retained→restore→visible); esbuild transform.

### E7 — Seeding + type Trash. Backend.
- **Goal:** new codexes get **per-instance** field rows; type deletion carries fields+entries.
- **Changes:** `CodexResource` seeding stamps each seeded default type with its field rows
  (from the `codex_category` master as template) at creation, instead of relying on global
  schema. Type delete → existing Codex Trash flow now also covers `codex_type_field` (fields
  ride along via the chapter; verify Trash/restore restores fields + entries together, §10.6).
- **Done-when:** a brand-new project codex has seeded types each owning their own field rows;
  trashing then restoring a type round-trips its fields and entries.
- **Verification:** integration-style DAO test on new-codex creation; Trash round-trip check.
- **Handoff notes:** confirm `ON DELETE CASCADE` vs. soft-delete interaction — Trash is
  soft-delete on the chapter, so `codex_type_field` rows must **remain** (not cascade) while a
  type sits in Trash; cascade only fires on hard purge.

### E8 — DOCX + AI promotion against per-instance types. Backend + small Frontend.
- **Goal:** round-trip and promotion honor project-specific types.
- **Changes:** `CodexExportService` import resolves H3 labels against the **type's active
  fields** (case-insensitive), unknown skipped (existing contract). AI promotion (§14
  compatibility layer): map AI's broad category → the project's type whose `system_key`
  matches; promotion dialog lets the author pick a different project type; entry created under
  the chosen type. `codex-fill-v1` prompt unchanged (defer "prompt includes types").
- **Done-when:** DOCX export→edit→import round-trips a custom type; a promoted recommendation
  lands under the mapped-or-chosen project type.
- **Verification:** DOCX round-trip test; promotion path test.

### E9 — Terminology sweep + DELIVERED docs. Frontend + docs.
- **Goal:** finish the feature and preserve memory.
- **Changes:** UI "Category" → "Type" on all touched surfaces (nav, dialogs, help text); keep
  code/columns as-is. Full DELIVERED pass: `NovelKMS_Project_Document.md`,
  `NovelKMS_AI_Design.md`, `Architecture_and_Design_Decisions_by_Phase.md`, and this file's
  Status Dashboard → all Done. Note the conceptual-doc reconciliations (§3/§8 overridden).
- **Done-when:** `npm run check-help` passes; docs consistent; dashboard all Done.

---

## Reconciliation with `Extensible Codex Design.md` (conceptual)

- **§8 standalone `codex_type` / `codex_type_field` two-table model** → overridden by Decision 1:
  the category chapter row is the Type; only fields are normalized into `codex_type_field`.
  Everything §9 lists as a benefit of normalization (field-level soft-delete, restore, usage
  counts, copy foundation) is still achieved.
- **§3 / §21 two-type field boundary (SINGLE_LINE / MULTI_LINE only)** → overridden by
  Decision 4: keep the live `SHORT_TEXT` / `LONG_TEXT` / `SELECT` set with `options` / `help` /
  `feedsAi`. Doc's SINGLE_LINE≡SHORT_TEXT, MULTI_LINE≡LONG_TEXT.
- **§7 project scope, §10 evolution rules, §12 migration intent, §13–§15 AI/DOCX integration,
  §17 Trash, §18 privacy, §24 principal decisions** → adopted as written.
- **§19 copy, §20 type library, §10.7 entry type-change, §21 new field types** → deferred
  (Decision 9).

---

## Changelog

_Append a dated entry when a phase reaches Done: phase, what shipped, migration/version, any
surprises, and anything the next phase must know._

- **2026-07-19 — E1 done (migration V42).** Shipped `codex_type_field` (normalized
  per-instance fields, FK to the category chapter, `ON DELETE CASCADE`; `uq_codex_type_field_key
  (chapter_id, field_key)`, `idx_codex_type_field_chapter`, `deleted_at` reserved for E6) and
  `chapter.codex_type_description TEXT`. Backfill is `INSERT…SELECT … UNION ALL` per field,
  generated straight from V33's JSON so keys/labels/input types/SELECT options/help/`feeds_ai`/
  order are verbatim — CHARACTER 12 fields, VOICE 10, one field row per existing instance of
  those types; all other categories were schema-less and get none. Trashed instances ARE
  backfilled (fields must survive Trash for E7; cascade fires only on hard purge). Two dialect
  files, differing only in `RANDOM_UUID()` vs `gen_random_uuid()` (V22/V19 precedent). No app read
  path touched; `codex_category` untouched and still authoritative until E3.
  - **Verification note:** no live H2 jar available in-thread (Maven blocked, GitHub API
    rate-limited), so the standard Flyway replay was substituted with static SQL checks + a
    SQLite simulation of V42 (per-instance counts, key/order fidelity to V33, options round-trip,
    `feeds_ai`, unique constraint). Recommend a local V1→V42 replay before deploy.
  - **Field-count facts for E2/E3:** active CHARACTER instance → 12 field rows; VOICE → 10.
    `authorNotes` is the only `feeds_ai=false` field in both. SELECT fields: CHARACTER `role`
    (9 options), VOICE `register` (6 options).
  - **Next:** E2 — `CodexTypeFieldDao` + `CodexType` DTO + read-only `GET /codex/types/{typeId}`.
 - **2026-07-19 — E2 done (backend, read-only).** Added `CodexTypeFieldDao`
  (`findActiveByType` / `findAllByType` → `List<CodexField>`; `options` JSON →
  `List<String>`, fail-soft; ORDER BY `display_order, field_key`), a thin
  `CodexTypeDao.findType(typeId)` assembling the new `CodexType`
  `{ id, name, description, systemKey, fields }` read model, and
  `GET /codex/types/{typeId}` on `CodexResource`. `CodexField` reused verbatim.
  - **D2 chosen (structural):** the Type header (title / codex_category /
    codex_type_description) is read directly from `chapter` by `CodexTypeDao` as a
    purpose-built projection, NOT threaded through `ChapterDao.map()`. `ChapterDao`
    and the `Chapter` model are untouched — the `Chapter` projection already omits
    codex-only columns, so this is consistent, not a shortcut. Header guarded to
    `codex_id IS NOT NULL AND deleted_at IS NULL` → manuscript chapter / plain codex
    chapter / trashed Type / unknown id all 404.
  - **D1 chosen (security):** `GET /codex/types/{typeId}` was NOT auto-authorized —
    the `types` segment preceded the UUID and hit `default -> true`. Added
    `case "types" -> ownsChapter` to `TenantAuthorizationFilter.authorizePathIds`
    (typeId is a chapter id; `ownsChapter` resolves codex chapters via their codex's
    project/book). Grep confirmed no other route uses a `types/{uuid}` segment.
  - **DI:** `CodexTypeFieldDao` + `CodexTypeDao` constructed and bound in
    `NovelKmsServer`; `CodexResource` constructor now takes `CodexTypeDao`.
  - **Verification:** static (brace/package/signature) + SQLite read-query
    simulation + a Flyway-backed `CodexTypeDaoTest`. No in-thread H2 replay
    (Central blocked) — run `mvn test` locally before deploy.
  - **Next:** E3 — cut the live entry form + `CodexAiService` over to
    `GET /codex/types/{typeId}`; after E3, `/codex/categories` is seed/promotion-only.
- **2026-07-19 — E3 done (backend + frontend cutover).** The live entry form and
  AI fill now read the entry's own Type fields, not the global category master.
  - **Backend (`CodexAiService`):** injects `CodexTypeFieldDao` (dropped
    `CodexCategoryDao`). The filled entry's schema = `findActiveByType(chapterId)`;
    label = Type name (`chapter.title`, fallback category key). `buildSchemaDescription`
    / `buildExistingFieldsText` / `renderStructuredFields` now take `List<CodexField>`.
    Reference-context renders each pinned entry from its own Type
    (`AiContextEntry.chapterId()`), cached per Type; `feeds_ai` filtering intact.
    Removed `resolveCategory` + `schemaByKey` + `CodexCategory`/`CodexSchema` imports.
    `NovelKmsServer` passes `codexTypeFieldDao` to the service.
  - **Frontend:** added `codexApi.getType(typeId)` + `useCodexType(typeId)`
    (`['codex','type',typeId]`). `EditorPanel` resolves `codexEntrySchema` from
    `useCodexType(chapterData.id)` → `{ fields }` instead of matching
    `useCodexCategories()` by key. `CodexEntryFields` unchanged (`schema?.fields`).
  - **Parity:** V42-backfilled instance fields == the old global set, so existing
    CHARACTER/VOICE entries render identically and AI fill keeps the same shape;
    schema-less Types render plain title+body.
  - **`/codex/categories` is now consumed only for seeding new codexes + AI
    promotion mapping** (`useCodexCategories` remains defined for that; no longer
    imported by `EditorPanel`).
  - **Verification:** Java brace balance; esbuild transform on the 3 JS/JSX files;
    grep for removed-symbol residue. No in-thread build — run `mvn test` + a manual
    entry-render diff before deploy.
  - **Tests (E1–E3 coverage):** added `TenantIsolationDaoTest.ownsChapter_projectScopedCodexChapter_scopedToOwner`
    and `..._bookScopedCodexChapter_scopedToOwner` (security basis of the
    `types -> ownsChapter` route), and `CodexResourceTest` for `GET /codex/types/{typeId}`
    (200 header+active fields; 404 for manuscript chapter / unknown id). Cross-tenant
    denial is covered at the DAO layer since ResourceExtension doesn't register the
    tenant filter. `CodexAiServiceTest` (fake AiProvider) deferred to E4.
  - **Next:** E4 — the Type editor (create/rename Type, edit description; write path
    to `chapter.codex_type_description` + `codex_type_field`).
- **2026-07-20 — E4 done (backend, type-editor write path).** Author can now
  create/rename types, edit descriptions, and add/update/reorder fields via API.
  - **New `util/CodexFieldKeys`:** immutable key = `slug(label) + '_' + 4-hex`
    (slug = lowercased `[a-z0-9]` squeeze, ≤60 chars, `field` fallback),
    regenerated-on-collision against ALL of a Type's keys incl. soft-removed
    (the unique index spans deleted rows). Existing V42 keys untouched.
  - **`CodexTypeFieldDao` writes:** `addField` (generates key, appends after
    MAX(display_order) over all rows), `updateField` (label/inputType/options/
    help/feedsAi; **key never in the SET clause**; SELECT-only options, cleared
    on switch to text; refuses soft-removed rows), `reorderFields` (batch 0..n-1),
    `findField`. Every write guarded `WHERE chapter_id = ?` — a foreign key
    matches nothing (same isolation as `reorderInCodex`'s codex_id guard).
  - **`CodexTypeDao` writes:** `createType` (author type, `codex_category` NULL,
    reuses `ChapterDao.createCodexChapter` for row + codex-scoped display_order,
    then stamps description) and `updateHeader` (title + codex_type_description,
    guarded to `codex_id IS NOT NULL AND deleted_at IS NULL`, never touches
    codex_category). `CodexTypeDao` ctor now takes `ChapterDao` (DI + 2 tests
    updated).
  - **Endpoints on `CodexResource`** (NOT a new `CodexTypeResource` — the class is
    `@Path("/")` and already owns `GET /codex/types/{typeId}`, so a
    `@Path("/codex/types")` resource would shadow it via Jersey prefix
    resolution): `POST /codex/{codexId}/types`, `PUT /codex/types/{typeId}`,
    `POST /codex/types/{typeId}/fields`, `PUT /codex/types/{typeId}/fields/{fieldKey}`,
    `PUT /codex/types/{typeId}/fields/order`. `CodexResource` ctor gains
    `CodexTypeFieldDao` (already bound; `CodexResourceTest` builder updated).
  - **Decision refinement:** field identity in the write API is the immutable
    `field_key`, not the row id — it's what the client already holds
    (`CodexField.key`), unique-per-type, and keeps `CodexField` unchanged.
    Reorder uses `/fields/order` (not `/reorder`) so `authorizeSensitiveJsonBody`
    doesn't reject field keys as un-owned entity UUIDs.
  - **Validation:** name/label blank → 400; inputType ∉ {SHORT_TEXT,LONG_TEXT,
    SELECT} → 400; SELECT with empty options allowed; type/field not a live codex
    Type → 404, never 403; `feedsAi` omitted → TRUE.
  - **Verification:** Java brace/package static checks; SQLite simulation of the
    write-SQL guards (cross-type isolation, soft-removed non-editable, options
    clearing, key-uniqueness across deleted rows, reorder isolation, header
    guard); DAO signatures + codex_category nullability confirmed vs master. No
    in-thread H2/Maven — run `mvn test` locally before deploy.
  - **Next:** E5 — Type-editor UI (Manage Types, type/field editors, dnd-kit
    reorder by key, entry-create type picker) wired to these endpoints.    

