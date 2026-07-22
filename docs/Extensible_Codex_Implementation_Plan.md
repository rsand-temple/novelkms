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
| E5 | Type-editor UI: Manage Types surface, type editor, field editor, entry-create type picker | Frontend | **Done** |
| E6 | Field soft-remove / restore (non-destructive), removed-fields area, entry-count warning | Backend + Frontend | **Done** |
| E7 | New-codex seeding stamps per-instance fields; type→Trash carries fields+entries; restore together | Backend | **Done** |
| E8 | DOCX round-trip + AI promotion against per-instance types (`system_key` mapping + author picks type) | Backend + small Frontend | **Done** |
| E9 | Terminology sweep (UI "Category"→"Type") + full DELIVERED living-doc pass | Frontend + docs | **Done** |
| E10 | Close-out: wire Type delete + reorder, retire dead/duplicate paths, surface Type description | Backend + Frontend + docs | Not started |

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

### E10 — Close-out. Backend + Frontend + docs + help files.

- **Goal:** the Extensible Codex has no half-wired surfaces and no second way to do
  anything. E1–E9 shipped the model and the editor; E10 closes the gaps between what
  the backend can do and what the UI offers, and deletes the paths the feature
  superseded but never removed.

- **Why this exists:** E9's sweep surfaced that several capabilities are fully built
  server-side and hook-side but have no caller. This was verified by grep against
  `master` @ `8cdca5c`, not inferred — four exported hooks in `useCodex.js` have **zero
  consumers anywhere in `src/`**: `useDeleteCodexChapter`, `useReorderCodexChapters`,
  `useCreateCodexChapter`, `useCodexCategories`. Each is a separate story below.

#### Decisions to confirm at thread start

1. **Does AI promotion resurrect a deliberately deleted seeded Type?** Today
   `AiReviewService.getOrCreateCategoryChapter` creates the Type when the `system_key`
   map finds no live match. Once A1 lets an author trash CHARACTER, the next promotion
   with `category: CHARACTER` silently rebuilds it. Options: (a) resurrect, current
   behavior, no code; (b) fall back to NOTES when the mapped Type is absent; (c) fall
   back to the author's explicit `codexTypeId` only, and 400 otherwise.
   *Recommend (b)* — it respects the author's deletion without failing the promotion,
   and NOTES is already the documented safe fallback.

2. **Keep or delete `POST /codex/{codexId}/chapters`?** See B3 — it is a live,
   unauthenticated-by-the-feature second Type-creation path that does **not** seed
   fields, so it produces exactly the field-less Type that E7 and E8 worked to prevent.
   *Recommend delete the endpoint*, since the frontend hook that called it is also
   dead. If any external caller is suspected, the fallback is to route it through the
   E4 create-type path so it seeds.

3. **Where does the Type description surface?** V42 added
   `chapter.codex_type_description` and E4/E5 write it, but nothing reads it back to
   the author. Options: Manage Types list secondary line; the entry form header; the
   `CodexTypeProperties` panel. *Recommend all three are wrong to do at once — pick
   `CodexTypeProperties`*, which is the panel that already exists for exactly "tell me
   about this Type" and is currently a name chip plus one toggle.

4. **Does `GET /codex/categories` stay?** After B1 removes the frontend consumer, the
   route has no caller — seeding and promotion mapping both use `CodexCategoryDao`
   directly in-process. *Recommend keep the route, remove the frontend client.* It is
   harmless, read-only, and is the only external view of the seed template.

5. **Scope discipline.** Everything under *Explicitly out of scope* below stays out,
   including anything that looks like a two-line win. E10 is the last phase; it should
   end with nothing half-done, not with a new deferral list.

#### Track A — Close the E5/E7 UI gaps

- **A1. Type deletion in the nav.** `getDeleteContext` has no `codex-category` case in
  `NavContextMenu.jsx`, and `NavToolbar.jsx` returns null whenever `selection.codexId`
  is set. So a Type can be created (E5) and is fully trashable server-side (E7) with no
  way to ask for it. Add the case to both, wire `useDeleteCodexChapter`, and write the
  confirm-dialog copy to state the actual E7 contract: the Type's fields and all of its
  entries go to Trash with it and come back together on restore. Do **not** say "cannot
  be undone" — that wording is already on the known-issues list for other dialogs and
  would be newly wrong here.

- **A2. Type reordering in the nav.** `PUT /codex/{codexId}/chapters/reorder` exists and
  `useReorderCodexChapters` exists; nothing calls either. `canReorder` in
  `NavContextMenu.jsx` is `type === 'scene' || 'chapter' || 'part'`. Add
  `'codex-category'`, source siblings from `useCodexChapters(menuNode.codexId)`, and
  dispatch the existing hook. `NavToolbar` needs the matching sibling/index wiring so
  the arrows and the menu agree — they must, per the existing comment on
  `outlineSiblings`. Move Up/Down only; **no DnD** — `CodexItem` renders its children in
  a plain `Box` with no `SortableContext`, and adding one is a bigger change than this
  phase wants.

- **A3. Surface the Type description.** Per Decision 3. Read-only display; editing stays
  in the Type editor where E4/E5 put it.

#### Track B — Retire dead and duplicate paths

- **B1. `useCodexCategories` + `codexApi.getCategories`.** Dead since the E3 cutover
  moved schema resolution onto the per-instance Type. Remove both, plus the
  `CODEX_KEYS.categories()` key factory entry if nothing else uses it. Backend route
  per Decision 4.

- **B2. `useCreateCodexChapter`.** Superseded by `useCreateCodexType` →
  `POST /codex/{codexId}/types`, which is the path that seeds fields. Remove.

- **B3. `POST /codex/{codexId}/chapters`.** Per Decision 2. This is the one with real
  consequences: it calls `chapterDao.createCodexChapter` directly and never touches
  `codex_type_field`, so anything reaching it creates a Type with no fields — the
  precise failure E7's seeding and E8's promotion-path parity were built to eliminate.

#### Track C — Verification and audit

- **C1. Confirm `codex_category.field_schema` (V33) has no live reader** outside seeding
  and promotion mapping. E3 demoted it from schema authority to seed template; E8
  dropped `CodexExportService`'s dependency on it. Grep `CodexCategoryDao` callers and
  add a class-level comment stating it is seed-template-and-promotion-mapping only, so a
  future reader does not mistake it for the live schema again.

- **C2. Run the suite.** `CodexTypeDaoTest`, `CodexTypeWriteDaoTest`,
  `CodexTypeTrashDaoTest`, `CodexResourceTest`, `CodexFieldUsageServiceTest` all exist
  and have never been run in-thread — Maven is blocked in the build environment. Run
  `mvn test` locally first thing; do not write E10 code on top of an unverified E9 tree.

- **C3. Tenant auth — already verified, do not re-litigate.**
  `TenantAuthorizationFilter.authorizePathIds` has `case "types" -> access.ownsChapter(
  userId, id)`, so `/codex/types/{typeId}` and everything nested under it is guarded by
  chapter ownership. The E2 handoff note asking for this confirmation is discharged.
  A1/A2 add no new path segments, so no filter change is needed — but if that stops
  being true, remember the filter's `default -> true`.

- **C4. Trash restore collision.** `TrashPanel` surfaces a 409 when a restore collides.
  Check what happens restoring a Type whose title now matches a live Type in the same
  codex, and that the message names the Type rather than saying "chapter".

#### Track D — Docs

- **D1.** Extend `help/content/codex-types.md` with reordering and deletion once A1/A2
  land — the topic currently says deletion goes through "the normal Codex delete flow",
  which becomes true only after A1.

- **D2.** Not codex, logged here so it is not lost: `help/content/artifacts.md` uses a
  Markdown table and `miniMarkdown.js` has no table syntax, so it renders as literal
  pipe text. Either add table support to the renderer or rewrite that topic. Separate
  change; do not bundle it into an E10 commit.

#### Explicitly out of scope (Decision 9 deferrals — still deferred after E10)

Cross-project type copy (§19); shared type library (§20); changing an existing entry's
Type (§10.7); field types beyond SHORT_TEXT / LONG_TEXT / SELECT (§21); permanent purge
of soft-removed field values; "AI prompt includes the project's actual types". None of
these block calling the feature complete.

#### Done-when

- A Type can be created, renamed, reordered, trashed, and restored entirely from the UI,
  with its fields and entries following it in both directions.
- No exported hook in `useCodex.js` lacks a consumer.
- Exactly one code path creates a Type, and it seeds fields.
- The Type description the author typed is visible somewhere they did not type it.
- `mvn test` green; `npm run check-help` green.

#### Verification

Static, per the standing constraint (Maven and H2 blocked in-thread): Python brace/paren
balance plus package-path check on Java; `esbuild --bundle=false` transform on JSX;
`node scripts/check-help.mjs`; grep sweep for consumers of every symbol removed in
Track B. No migration is expected — if one becomes necessary, next free is **V43**.
Run `mvn test` and `mvn package` locally before deploy, and manually exercise:
create Type → add fields → add entry → reorder Types → trash Type → restore Type →
confirm fields and entries returned.

#### Handoff notes

- A1 and Decision 1 are coupled. Do not ship Type deletion without deciding what
  promotion does when the mapped seeded Type is gone, or the first author to delete
  CHARACTER will watch it reappear on the next promote.
- B3 is the only item with a live correctness consequence; the rest of Track B is
  hygiene. If E10 has to be cut short, ship A1, A2, and B3.
- E10 is the last phase. On completion, set every dashboard row to **Done**, append the
  Changelog entry, and do the final DELIVERED pass across the three living docs.

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
- **Feature complete 2026-07-22 (E1–E9).** The two deliberate overrides stand as shipped:
  conceptual §8's standalone `codex_type` table (the category chapter row is the Type
  instead) and conceptual §3/§21's two-type field boundary (the live SHORT_TEXT /
  LONG_TEXT / SELECT set with `options` / `help` / `feedsAi` was kept). Still deferred
  and unbuilt: §19 cross-project copy, §20 type library, §10.7 entry type-change, §21
  new field types, permanent field-value purge, and "prompt includes the project's
  actual types".
  
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
- **2026-07-20 — E5 done (frontend, type-editor UI).** Authors can create/rename
  types, edit descriptions, and add/edit/reorder fields entirely from the UI.
  - **API (`api/codex.js`):** added `createType`, `updateType`, `addField`,
    `updateField` (key percent-encoded), `reorderFields` ({ fieldKeys }). Field
    write bodies use `inputType` (mapped from `CodexField.type`); reads keep
    `type`.
  - **Hooks (`hooks/useCodex.js`):** `useCreateCodexType`, `useUpdateCodexType`
    (invalidates the type read model + owning codex chapter list, since name is
    chapter.title on the nav row), `useAddCodexTypeField`,
    `useUpdateCodexTypeField`, and `useReorderCodexTypeFields` (optimistic
    reorder of the cached `type.fields` by key, rollback on error, invalidate on
    settle — component stays a pure function of the query).
  - **Components:** `ManageCodexTypesDialog` (lists types via `useCodexChapters`,
    per-type field counts via `useQueries` on `CODEX_KEYS.type` so open types
    cost no extra request; New Type → create → chain into editor),
    `CodexTypeEditorDialog` (name+description saved together via header PUT;
    fields in a self-contained dnd-kit `DndContext` separate from the nav tree;
    string IDs = field keys), `CodexFieldEditorDialog` (Label, Input style →
    SHORT_TEXT/LONG_TEXT/SELECT, Choices for SELECT, Help, feeds-AI switch —
    Help+feedsAi round-tripped so editing seeded fields never blanks them).
  - **Nav wiring (`NavContextMenu.jsx`):** "Manage Codex Types…" on the codex
    node, "Edit Type…" on a codex-category node; both capture their target
    before `closeMenu()`. `AddCodexEntryDialog` now takes `typeName` so author
    types read "New {TypeName}".
  - **Scope held to E5:** no field-delete control (soft-remove is E6); no type
    reorder / type-Trash surface (nav + Codex Trash / E7); per-type entry counts
    deferred. Decision-8 "Type" wording used on new surfaces; full sweep is E9.
  - **Icons:** only repo-proven icons (`TuneOutlined`, `EditOutlined`, `Add`);
    drag handle is a grip glyph, no new icon dependency. No React Compiler in
    this project, so manual hooks are used normally.
  - **Verification:** esbuild JSX transform on all 7 files; hook/API export vs.
    consumer cross-check; menuNode payload confirmed against CodexItem /
    CodexCategoryItem. Frontend-only — no Maven/H2 in-thread; run a build and
    walk the create-type→add-entry→fill flow before deploy.
  - **Next:** E6 — field soft-remove/restore (backend `deleted_at` + restore;
    "Removed fields" area in the type editor; entry-count warning). The editor's
    FieldRow is where the per-field Delete/Restore affordance lands.
- **2026-07-21 — E6 done (backend + frontend, non-destructive field removal).**
  Authors can remove a Type field without losing data and restore it later; the
  type editor gained a "Removed fields" area and a data-aware removal warning.
  No migration — `deleted_at` already exists on `codex_type_field` from V42.
  - **DAO (`CodexTypeFieldDao`):** `softRemoveField` (stamps `deleted_at`,
    guarded `deleted_at IS NULL` so re-removing / a foreign key is a no-op),
    `restoreField` (clears `deleted_at`, guarded `IS NOT NULL`, returns the
    field; original `display_order` preserved so it slots back into place —
    key collision on restore is impossible because `addField` generates keys
    against the full key set including removed rows), and `findUsage` (every
    field, active and removed, with a `removed` flag; `entryCount` left 0 for
    the service to fill).
  - **DTO (`CodexFieldUsage`):** flat `{ key, label, type, options, help,
    feedsAi, removed, entryCount }`. Deliberately separate from `CodexField` /
    `CodexType` so the entry-form and AI-reference contracts keep seeing only
    active fields with no editor-only noise.
  - **Service (`CodexFieldUsageService`):** overlays scene-derived counts onto
    `findUsage`. Counting runs in Java over each entry's `structured_data`
    (portable across H2 DEFAULT mode and PostgreSQL — no dialect JSON
    operator). "Contains information" = key present with a non-null value that
    is non-blank after trim (empty string / whitespace / missing key → not
    counted); a malformed blob is skipped, not fatal. Values under a removed
    field are still counted, proving they survive removal.
  - **Endpoints (`CodexResource`):** `DELETE /codex/types/{typeId}/fields/{fieldKey}`
    (204 / 404), `POST .../fields/{fieldKey}/restore` (200 field / 404),
    `GET .../fields/usage` (200 list; 404 when the Type isn't live). All hang
    off the tenant-authorized `types` segment (`ownsChapter`); the bodyless
    DELETE and restore POST never trip `authorizeSensitiveJsonBody`. `usage`
    resolves ahead of `{fieldKey}` by the same literal-over-template precedence
    E4 relies on for `/fields/order`. Constructor gains the service; wired +
    bound in `NovelKmsServer`.
  - **API (`api/codex.js`):** `removeField`, `restoreField` (empty-body POST),
    `getFieldUsage`.
  - **Hooks (`hooks/useCodex.js`):** `useCodexFieldUsage` (new `usage` query
    key; `staleTime: 0` so counts are never stale when the editor opens),
    `useRemoveCodexTypeField`, `useRestoreCodexTypeField` (both invalidate the
    Type read model + the usage view).
  - **Component (`CodexTypeEditorDialog`):** per-row Delete affordance; removal
    warning quoting the entry count (§10.5 wording, singular/plural, and a
    defensive generic variant when usage hasn't loaded); a dashed "Removed
    fields" area showing "N entries" + Restore. Active editable list still
    sourced from `useCodexType().fields`, so E5's optimistic reorder is
    undisturbed.
  - **Scope held to E6:** soft-remove/restore only — no permanent value purge
    (§10.7/Decision 9 deferred); type-level Trash is E7. Icons: only
    repo-proven (`Delete`, plus E5's `Add`/`EditOutlined`); Restore is a text
    button (no restore icon in the repo).
  - **Verification:** `CodexTypeWriteDaoTest` extended (hide-but-survive,
    no-op re-remove / foreign key, restore-in-order, active/unknown restore
    empty, `findUsage` flags); new `CodexFieldUsageServiceTest` (per-key
    counts incl. values under removed fields, whitespace/empty excluded,
    malformed skipped, field-less type empty); `CodexResourceTest` builder +
    endpoint tests (204/404 remove, restore reappears in slot, usage counts).
    Static: Java brace/package check, esbuild JSX transform. Maven/H2 not run
    in the authoring environment.
- **2026-07-21 — E7 done (backend, seeding + type Trash).** New codexes now seed
  per-instance field rows, and the Type Trash carry contract is locked in.
  - **Seeding (`CodexTypeFieldDao.seedFields` + `CodexResource.seedDefaultChapters`):**
    creating a project/book codex stamps each seeded default Type with its own
    `codex_type_field` rows, copied VERBATIM from the `codex_category` master
    schema — keys (`role`, `age`, …), labels, input types, SELECT options, help,
    `feeds_ai`, and array order all preserved, `display_order` = array index. So a
    brand-new CHARACTER type owns the same 12-key set as the V42-backfilled
    instances (VOICE 10); the five schema-less defaults (PLOT/WORLD/TIMELINE/
    CANON/NOTES) seed no field rows. `seedFields` uses verbatim keys, NOT the
    author path's `slug_4hex` generator — this shared key set is what E8 AI-
    promotion mapping and Decision 3 depend on. One batched, transactional insert
    per Type; null/empty field list is a no-op. `addField` unchanged.
  - **Type Trash — no production code change, confirmed by design.** `trashChapter`
    stamps only the chapter root's `deleted_at`; it never touches
    `codex_type_field`, so a trashed Type keeps its field rows and its entry
    scenes (entries hidden transitively, not deleted). `restoreChapter` clears the
    flag and fields + entries go live together. `purgeChapter` hard-deletes the
    chapter, and only then does the V42 `ON DELETE CASCADE` FK remove the field
    rows (and the entry scenes). This is exactly the handoff-note contract: fields
    survive Trash, cascade only on hard purge.
  - **Tests:** `CodexResourceTest.createProjectCodex_seedsTypesWithVerbatimPerInstanceFields`
    (7 seeded types; CHARACTER 12 / VOICE 10 verbatim keys in order; schema-less
    five own 0). New `CodexTypeTrashDaoTest` (trash leaves fields+entry; restore
    brings both back; purge cascades both). `NovelKmsServer` DI unchanged.
  - **Verification:** Java brace/package static checks on all four files; DAO/
    getter signatures cross-checked. No migration → no H2 replay. Maven/H2 not run
    in-thread — run `mvn test` locally before deploy.
  - **Next:** E8 — DOCX round-trip + AI promotion against per-instance types
    (`system_key` mapping + author picks type).
- **2026-07-21 — E8 done (backend + small frontend; no migration).** DOCX round-trip
  and AI-promotion now honor per-instance Types.
  - **DOCX (`CodexExportService`):** schema resolution moved off the retired global
    `codex_category` master onto the entry's own Type — `codexTypeFieldDao
    .findActiveByType(chapter.getId())` wrapped into a `CodexSchema` (null when the
    Type has zero active fields, preserving the plain title+body branch). Dropped the
    now-dead `codexCategoryDao` dependency (constructor + `NovelKmsServer` wiring).
    So a renamed/removed field affects only that project's round-trip, and the
    importer's case-insensitive H3-label match now resolves against the Type's active
    fields (unknown labels still skipped).
  - **Promotion (`AiReviewService` / `AiReviewResource`):** `PromoteRequest` gains
    optional `codexTypeId`. Present → promote directly under that Type, validated via
    `findTypeInCodex` to be a live chapter of the review's PROJECT codex (else
    `400 type_not_in_project`, D4) — the only way to reach author-created Types (NULL
    system_key). Absent → unchanged §14 system_key map (`resolveCodexCategory` →
    `getOrCreateCategoryChapter`). E7-parity seeding added to this path:
    `getOrCreateProjectCodex` and on-demand category-chapter creation now stamp
    per-instance fields (`seedTypeFields` + `findDefaultCategory`, mirroring
    `CodexResource.seedDefaultChapters`), so a first promotion into a fresh project
    doesn't land under a field-less Type. `AiReviewService` now takes
    `CodexTypeFieldDao`. `codex-fill-v1` prompt unchanged (prompt-includes-types still
    deferred).
  - **Frontend:** promote dialog lists the project's actual Types (label = title,
    value = type id), resolved in `ReviewRail` via `useProjectCodex → useCodexChapters`
    (project scope, matching the backend's `getOrCreateProjectCodex`) and passed to
    `ReviewCard`. Pre-selects the Type whose `systemKey` matches the AI category; falls
    back to the seven broad seed categories when the project has no codex yet (sends
    `codexCategory`, backend seeds). New pure helpers `buildPromoteOptions` /
    `defaultPromoteValue` / `promoteTarget` in `recommendationUtils.js`; `onPromote` now
    carries `{ codexTypeId, codexCategory }`; picker relabeled "Codex type" (Decision 8).
    `EMPTY_TYPES` hoisted for referential stability. `usePromoteRecommendation` +
    `aiApi.promoteRecommendation` thread `codexTypeId`.
  - **Verification:** static only (Maven/H2 blocked in-thread). Java brace-balance +
    package-path (4 files); esbuild transform (5 files); no other callers of the changed
    signatures. Recommend a build + manual DOCX round-trip of a custom Type and a
    promotion into an author-created Type before deploy.
  - **Next:** E9 — terminology sweep (UI "Category" → "Type") + full DELIVERED pass
    across the three living docs and this dashboard.
- **2026-07-22 — E9 done (frontend + docs; no migration). Extensible Codex is COMPLETE.**
  Decision 8 executed: user-facing wording now says "Type" everywhere; every code
  identifier, column, route, cache key, node type, and filename keeps its historic
  `codex_category` / `codex-category` / `codexCategory` name on purpose.
  - **Wording changed (6 files):** `PropertiesPanel` (overline `Codex Category` →
    `Codex Type`; label fallback `'Category'` → `'Type'`; empty state → "No entries of
    this type yet."; internal `CODEX_CATEGORY_LABELS` → `CODEX_TYPE_LABELS`,
    `CodexCategoryProperties` → `CodexTypeProperties`); `TrashPanel`
    (`CODEX_CATEGORY.label` → `Codex Type`, while the backend trash `type` string stays
    `CODEX_CATEGORY`); `ManageAiContextDialog` (group label fallback → `'Type'`);
    `CodexCategoryItem` (nav label fallback → `'Type'`; `CATEGORY_ICONS` → `TYPE_ICONS`);
    `NavContextMenu` (both AI-context snackbar fallbacks `'category'` → `'type'`);
    `NavToolbar`.
  - **Stale-comment correction.** Three files still asserted categories were fixed /
    hardcoded / unrenameable — false since E4/E5. Comments now describe the live model:
    seven seeded Types carry a system key, authors create Types with a NULL key, each
    Type owns its own `codex_type_field` set, renaming happens in the Type editor, and
    Type deletion is not yet wired into the nav.
  - **`NavToolbar` Add-button + entry dialog (Decision 4).** `NavContextMenu` already
    passed `typeName` to `AddCodexEntryDialog`, so a right-click on an author-created
    "Dragon" Type read "New Dragon" while the toolbar's Add button read "Add Entry" and
    its dialog read "New Entry". `NavToolbar` now resolves the selected Type's title via
    `useCodexChapters(selection.codexId)` — the same cache key `CodexItem` already
    populates, so no extra request — and threads it into both `getAddLabel(selection,
    typeName)` and the dialog's `typeName` prop. Seeded Types still win from
    `ADD_ENTRY_LABELS`; author Types fall back to `Add {name}` / `New {name}`.
  - **Help (3 files).** `codex-overview.md` rewritten to say types, note the seven seeds
    are only a starting point, and link the new topic. `ai-promotion.md` now describes
    the real E8 promote dialog (author picks the Type, pre-selected from the finding)
    rather than "suggests a category". New `codex-types.md` (`id: codex.types`, section
    `codex`, order 20) — the first help for the entire E4–E6 surface: managing types,
    the type editor, the three field input styles, help text and share-with-AI, the
    non-destructive remove/restore contract, type Trash, and how types drive DOCX
    round-trip and AI promotion. `npm run check-help` passes: 29 topics, 7 sections.
  - **Deliberately NOT done — carried forward.** `NavContextMenu.getDeleteContext` has
    no `codex-category` case and `NavToolbar.getDeleteContext` returns null when
    `selection.codexId` is set, so a Type can be created (E5) and trashed by the backend
    (E7) but there is no nav affordance to trash it. This is a behavior slice, not
    terminology; E9 corrected the comments to say so and left the gap. **Next slice.**
  - **Also noticed, pre-existing, out of scope:** `help/content/artifacts.md` uses a
    Markdown table, which `miniMarkdown.js` does not support (no table syntax in the
    renderer) — it renders as literal pipe text. Unrelated to the Codex; logged on the
    watchlist.
  - **Verification:** static only. esbuild transform-only on all 6 JSX files (pass);
    `node scripts/check-help.mjs` against the merged tree (pass, 0 errors 0 warnings);
    grep sweep confirming no dangling `CODEX_CATEGORY_LABELS` / `CATEGORY_ICONS` /
    `CodexCategoryProperties` references and no residual user-facing "Category" string.
    No migration → no H2 replay. Run `mvn package` before deploy.