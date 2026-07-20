# Extensible Codex Design

## 1. Overview

NovelKMS should allow authors to define their own Codex types and the fields associated with those types.

A Codex type represents a reusable structure for one kind of project knowledge. For example, an author building a fantasy world might define a `Dragon` type with fields such as:

- Name
- Color
- Breath
- Wingspan
- Temperament

A Codex entry is an instance of that type. A specific dragon could therefore have values such as:

```text
Name: Aranthor
Color: Crimson
Breath: White-hot fire
Wingspan: 82 feet
Temperament: Proud and territorial
```

The Codex type definition belongs to the project. Every book within that project can use the same set of types. A future feature may allow Codex types, or complete Codex structures, to be copied from one project to another.

This feature builds on the existing NovelKMS Codex implementation, which already supports structured fields and structured entry data. The principal change is to make schema definition a first-class user-managed capability.

## 2. Product Goals

The extensible Codex system should:

1. Allow authors to model project knowledge using terminology appropriate to their fictional world.
2. Support user-defined Codex types at the project level.
3. Provide a simple form builder that does not require technical knowledge.
4. Generate entry forms dynamically from each type definition.
5. Preserve existing entry data when type fields are renamed, reordered, or removed.
6. Integrate with existing Codex features such as Trash, DOCX import/export, AI fill-in, and AI recommendation promotion.
7. Establish a clean foundation for future schema features without implementing unnecessary complexity in the initial release.

## 3. Initial Feature Boundary

The first release should support:

- Codex type name
- Optional Codex type description
- Ordered field definitions
- Single-line text fields
- Multi-line text fields
- Entry name or title
- Existing rich-text description body
- Adding fields
- Renaming fields
- Reordering fields
- Removing fields
- Soft-deleting Codex types and entries

The first release should not support:

- Checkboxes
- Dropdown lists
- Multi-select lists
- Numeric validation
- Date fields
- Repeating groups
- Nested schemas
- References to other Codex entries
- Required fields
- Conditional fields
- Computed fields
- Field inheritance
- Type inheritance
- Custom validation expressions

All structured values should initially be stored as text.

Even fields that appear numeric, such as wingspan, should remain text fields. This lets an author enter values such as `82 feet`, `approximately 25 metres`, `unknown`, or `varies by age` without introducing units, conversion, or validation rules.

## 4. Terminology

The recommended user-facing terminology is:

- **Codex** — the project’s complete knowledge base.
- **Codex Type** — an author-defined schema, such as Dragon, Character, Planet, or Spell.
- **Codex Entry** — one instance of a Codex type, such as Aranthor.
- **Field** — one structured attribute, such as Wingspan.
- **Description** — the existing rich-text narrative body associated with an entry.

The term **Codex category** should be retired from the user-facing interface where practical. Category implies classification or folder organization, while type more accurately communicates that the definition controls the structure of an entry.

## 5. Example Codex Types

Possible author-defined types include:

| Codex Type | Example Fields |
|---|---|
| Character | Full name, appearance, personality, background |
| Dragon | Color, breath, wingspan, temperament |
| Planet | Star system, climate, population, government |
| Magic Spell | School, effect, cost, limitations |
| Historical Event | Date, location, participants, consequences |
| Organization | Purpose, leadership, headquarters, membership |
| Artifact | Origin, powers, owner, limitations |

## 6. User Experience

### 6.1 Managing Codex Types

At the project Codex level, NovelKMS should provide a **Manage Codex Types** action.

The management surface should list the types defined for the current project and allow the user to:

- Create a type
- Edit a type
- Reorder types
- Move a type to Trash
- Restore a deleted type
- Inspect the number of entries using a type

### 6.2 Type Editor

A type editor may use a layout similar to:

```text
Type name
[ Dragon                                      ]

Description
[ Records the traits of dragon species and    ]
[ individual dragons in this world.           ]

Fields

☰  Color           Single line       [Edit] [Delete]
☰  Breath          Multi-line        [Edit] [Delete]
☰  Wingspan        Single line       [Edit] [Delete]
☰  Temperament     Multi-line        [Edit] [Delete]

[+ Add field]

                               [Cancel] [Save]
```

### 6.3 Field Editor

Creating or editing a field should require only:

```text
Field label: Wingspan
Input style: Single line / Multiple lines
```

The user should not be asked to define a database key, JSON property name, identifier, or validation rule.

NovelKMS should generate an immutable internal key for each field.

Example:

```text
wingspan_7f3a
```

The visible label may then be renamed without affecting stored entry data.

### 6.4 Creating Entries

When creating a new Codex entry, the user should choose a type:

```text
New Codex Entry

Character
Dragon
Location
Historical Event
```

After selecting a type, NovelKMS dynamically generates the entry form from that type’s active field definitions.

The entry form should contain:

1. Entry name or title
2. Structured fields in configured order
3. Existing rich-text Description editor

## 7. Project Scope

Codex types should be owned by a project rather than by a book.

This provides the expected behavior for series and multi-book projects:

- A Character type is shared across books.
- A Dragon type created for the fictional world is available throughout the project.
- A project-level Codex remains the canonical knowledge base for the series.
- Book-specific entries may still be supported through existing Codex scope rules where needed.

User-global type libraries should not be part of the initial release. Cross-project reuse should be implemented later as an explicit copy operation.

## 8. Data Model

The preferred long-term model separates Codex type definitions from the existing Codex container or category concept.

Conceptually:

```text
Codex
  └── Codex Type
        ├── Field Definitions
        └── Codex Entries
```

### 8.1 `codex_type`

Suggested columns:

```text
id
project_id
name
name_lower
description
display_order
system_key
created_at
updated_at
deleted_at
```

Notes:

- `project_id` establishes project ownership.
- `name_lower` supports case-insensitive uniqueness within a project.
- `system_key` is nullable and may identify seeded types with special semantic meaning.
- `deleted_at` supports Trash and restoration.

### 8.2 `codex_type_field`

Suggested columns:

```text
id
codex_type_id
field_key
label
input_type
display_order
created_at
updated_at
deleted_at
```

Initial `input_type` values:

```text
SINGLE_LINE
MULTI_LINE
```

Notes:

- `field_key` is generated once and never changes.
- `label` is user-editable.
- `display_order` controls form presentation.
- `deleted_at` allows a field to be removed without destroying existing values.

### 8.3 Codex Entry

Each Codex entry should reference its type:

```text
codex_type_id
structured_data
```

Structured values may continue to be stored as JSON.

Example:

```json
{
  "color_a18f": "Crimson",
  "breath_9d20": "White-hot fire",
  "wingspan_7f3a": "82 feet",
  "temperament_b341": "Proud and territorial"
}
```

The JSON keys correspond to immutable field keys rather than visible field labels.

## 9. Normalized Fields Versus JSON Schema

The existing Codex implementation stores field schema as JSON on the category record. The initial feature could extend that design with relatively little database restructuring.

However, first-class type and field records are recommended because the feature is intended to become a major part of the Codex system.

Normalized field records provide a stronger foundation for:

- Field-level soft deletion
- Field restoration
- Usage counts
- Copying schemas between projects
- Schema versioning
- Type export and import
- Entry-to-entry references
- Future validation
- Migration tooling
- Field-level auditing
- Identifying fields in AI prompts
- Detecting stale or orphaned values

The structured values themselves can remain JSON because entries may have different schemas and the system does not initially need to query individual values relationally.

## 10. Schema Evolution Rules

Schema changes must preserve user data whenever possible.

### 10.1 Adding a Field

Adding a field is safe.

Existing entries show the new field with an empty value. No entry data migration is required.

### 10.2 Renaming a Field

Renaming a field is safe provided the immutable internal `field_key` does not change.

Example:

```text
Breath → Breath weapon
```

Existing values remain attached to the original field key and appear under the new label.

### 10.3 Reordering Fields

Reordering fields is safe.

Only `display_order` changes. Entry data remains unchanged.

### 10.4 Changing Input Style

Changing a field between single-line and multi-line is safe because both store string values.

The change affects only how the value is edited and displayed.

### 10.5 Removing a Field

Removing a field must not immediately destroy stored values.

Before removal, NovelKMS should warn the user when entries contain values for the field.

Example:

> Twelve Dragon entries contain information in this field. Removing it will hide that information from the entry form. The values will be preserved and can be restored later.

The field should be soft-deleted.

Existing values remain in `structured_data`, but the form no longer displays them unless the field is restored.

A removed-fields section may allow the user to:

- Restore the field
- Inspect how many entries contain values
- Permanently purge the field in a future advanced operation

### 10.6 Deleting a Codex Type

Deleting a type should move the type and its entries to Trash.

The entries must not become detached or silently reassigned.

Restoring the type should restore its schema and entries together.

Permanent purge should follow the existing NovelKMS irreversible Trash workflow.

### 10.7 Changing an Entry’s Type

Changing an existing entry from one type to another should not be included in the first release.

This requires a mapping workflow because source and destination schemas may differ.

A future conversion dialog could support:

- Mapping source fields to destination fields
- Preserving unmapped values
- Previewing the converted entry
- Converting one entry or all entries of a type

## 11. Seeded and Custom Types

New and existing projects should receive a practical set of seeded types.

Possible defaults:

- Character
- Location
- Organization
- Object
- Event
- Canon Note
- General Note

From the user’s perspective, seeded types should behave like custom types:

- Fields can be added.
- Labels can be changed.
- Types can be reordered.
- New custom types can be created.

Where NovelKMS requires stable semantic behavior, seeded types may carry an internal `system_key`.

Example:

```text
system_key = CHARACTER
```

The visible name and schema can still be edited while NovelKMS retains enough semantic information to support specialized behavior.

A type created entirely by the user would normally have no `system_key`.

## 12. Existing Codex Migration

Existing Codex categories and entries must be migrated without data loss.

A migration should:

1. Create a Codex type for each existing project Codex category.
2. Convert the category’s existing `field_schema` into field records.
3. Preserve or generate immutable field keys.
4. Associate existing entries with the newly created type.
5. Preserve all existing `structured_data`.
6. Preserve existing Codex scope, order, pinning, Trash state, and AI-related metadata.
7. Assign `system_key` values to known built-in categories where appropriate.

If existing structured data is keyed by schema field keys, those keys should be preserved during migration.

## 13. Integration with AI Codex Fill-In

The existing AI Codex fill-in flow already consumes a field schema and returns values keyed to the schema.

The extensible type model should become the source of that schema.

For a Dragon entry, the AI prompt could include:

```text
Codex type: Dragon

Fields:
- color_a18f: Color
- breath_9d20: Breath
- wingspan_7f3a: Wingspan
- temperament_b341: Temperament
```

The AI response remains structurally similar:

```json
{
  "fields": {
    "color_a18f": "Crimson",
    "breath_9d20": "White-hot fire",
    "wingspan_7f3a": "82 feet",
    "temperament_b341": "Proud and territorial"
  },
  "body": "Aranthor guards the ruined citadel..."
}
```

The backend should ignore response keys that do not correspond to active fields in the selected type.

Removed fields should not be populated by AI unless explicitly restored.

## 14. Integration with AI Recommendation Promotion

The current AI review system suggests broad Codex categories such as Character, Voice, Plot, World, Timeline, Canon, and Notes.

An extensible Codex requires promotion to target project-specific types.

The first implementation may use a compatibility layer:

1. AI continues returning a broad semantic category.
2. NovelKMS maps that category to a seeded type with a matching `system_key`.
3. The promotion dialog allows the author to choose a different project type.
4. NovelKMS creates the entry under the selected type.

A later prompt version should include the project’s available types and field definitions directly.

Example:

```text
Available Codex types:

Dragon
- Color
- Breath
- Wingspan
- Temperament

Character
- Appearance
- Personality
- Background
```

The AI could then suggest:

```json
{
  "codexType": "Dragon",
  "codexTitle": "Aranthor"
}
```

Stable type IDs or keys should ultimately be preferred over labels in machine-readable contracts.

## 15. DOCX Import and Export

The existing Codex entry DOCX round-trip format can continue to work.

Suggested export structure:

```text
Heading 1: Entry title

Heading 3: Color
Normal: Crimson

Heading 3: Breath
Normal: White-hot fire

Heading 3: Wingspan
Normal: 82 feet

Heading 2: Description
Normal: Aranthor guards the ruined citadel...
```

Import should resolve field headings against the selected type.

Preferred matching order:

1. Exact field key stored in hidden or document metadata, when available in a future format.
2. Case-insensitive active field label.
3. Known previous labels, if field-label history is later implemented.

For the initial release, case-insensitive label matching is sufficient.

Unrecognized headings should be skipped rather than causing the entire import to fail.

## 16. Search and Discovery

The initial release should support searching:

- Codex type names
- Entry titles
- Entry description text
- Structured field values

Results should identify the type of each entry.

Example:

```text
Aranthor
Dragon
Breath: White-hot fire
```

Advanced field-specific queries are not required initially.

A later release could support filters such as:

```text
Type: Dragon
Color contains: Crimson
```

## 17. Trash and Recovery

Codex types and fields should follow NovelKMS’s durable-data principles.

Recommended behavior:

- Deleting an entry moves it to Trash.
- Deleting a type moves the type and its entries to Trash.
- Removing a field soft-deletes the field definition.
- Removed field values remain preserved in entry JSON.
- Restoring a type restores its fields and entries.
- Restoring a field makes preserved values visible again.
- Permanent purge is explicit and irreversible.

The Trash interface should clearly distinguish:

- Codex Type
- Codex Entry

Field removal may be managed within the type editor rather than appearing as a top-level Trash item.

## 18. Permissions and Ownership

Codex type definitions are private project data.

The existing project ownership and tenant authorization rules should apply.

A user may manage a type only if they own the associated project.

Custom type names, field definitions, and entry data must never become public merely because a related manuscript chapter is published for human review.

Future context-package sharing must explicitly snapshot selected Codex entries. Sharing a Codex entry does not share its type definition or other entries unless explicitly included.

## 19. Future Cross-Project Copying

A future **Copy Codex From Project** workflow should allow the user to select:

- Type definitions only
- Type definitions and entries
- Selected types
- Selected entries
- Whether to merge with compatible destination types
- Whether to create independent copies

The safest initial copy behavior is to create independent destination records.

Copied types and fields receive new IDs.

The copy process maintains temporary mappings:

```text
source type ID → destination type ID
source field key → destination field key
source entry ID → destination entry ID
```

If entries are copied, their structured JSON keys are rewritten using the field-key mapping.

This prevents later changes in one project from affecting another project.

## 20. Possible Type Library

After cross-project copying is proven, NovelKMS may add a personal type library.

Examples:

- Save Dragon type to My Type Library
- Add Character Sheet type to another project
- Export a type definition as JSON
- Import a shared type definition

This should remain separate from the initial project-scoped implementation.

A type library introduces additional questions:

- Versioning
- Updating installed types
- Name collisions
- Trust of imported schemas
- Sharing and publication
- Whether copied types remain linked to their source

The first release should avoid these questions by treating every project type as independent.

## 21. Future Field Types

The model should allow additional field types later, but the first release should expose only text.

Possible future types include:

```text
NUMBER
DATE
BOOLEAN
SELECT
MULTI_SELECT
ENTRY_REFERENCE
URL
IMAGE
RICH_TEXT
REPEATING_GROUP
```

Adding a new field type should be treated as a product feature, not merely a new enum value. Each type may affect:

- Storage representation
- Validation
- Search
- Export
- Import
- AI prompts
- Display
- Copy behavior
- Accessibility
- Schema migration

## 22. Non-Goals

The extensible Codex should not become a general-purpose relational database builder.

NovelKMS should not initially attempt to provide:

- Arbitrary tables
- SQL-like queries
- User-defined formulas
- Complex relationships
- Schema scripting
- Database triggers
- Polymorphic inheritance
- Visual entity-relationship diagrams
- General workflow automation

The feature should remain optimized for fiction authors managing structured worldbuilding information.

## 23. Recommended Implementation Slice

The minimum coherent implementation is:

1. Add project-owned Codex types.
2. Add ordered Codex type fields.
3. Support `SINGLE_LINE` and `MULTI_LINE`.
4. Generate immutable internal field keys.
5. Seed default types for new and existing projects.
6. Migrate existing Codex categories and schemas.
7. Generate entry forms dynamically from type definitions.
8. Support adding, renaming, reordering, and soft-removing fields.
9. Move deleted types and entries through Trash.
10. Drive Codex AI fill-in from the selected type’s schema.
11. Allow AI recommendation promotion to select a project type.
12. Preserve the existing rich-text Description body.
13. Design identifiers and ownership so cross-project copying can be added later.

## 24. Principal Design Decisions

### Decision 1: Types are project-scoped

A Codex models the fictional world or body of work associated with a project. Types therefore belong to the project rather than an individual book or the user account.

### Decision 2: Field keys are immutable

Visible field labels may change. Internal keys must not.

This is the central mechanism that makes schema editing safe.

### Decision 3: Entry values remain JSON

The schema is normalized, but entry values remain flexible JSON keyed by immutable field keys.

### Decision 4: Field removal is non-destructive

Removing a field hides it but preserves its values.

### Decision 5: All initial values are text

Single-line and multi-line are presentation distinctions over the same string storage model.

### Decision 6: Built-in types become seeded types

NovelKMS should provide useful defaults without restricting authors to a fixed ontology.

### Decision 7: Cross-project reuse is explicit copying

Projects remain isolated. Future copying creates independent destination types rather than shared mutable schemas.

## 25. Product Outcome

This feature changes the Codex from a predefined set of authoring buckets into a project-specific worldbuilding database.

An author writing contemporary fiction may use Character, Location, and Organization.

A fantasy author may add Dragon, Kingdom, Magic Spell, Prophecy, and Artifact.

A science-fiction author may add Planet, Species, Starship, Technology, and Political Faction.

NovelKMS provides the structure and durability, while the author defines the ontology appropriate to the story.
