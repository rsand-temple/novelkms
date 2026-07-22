---
id: codex.types
title: Codex Types and Fields
section: codex
order: 20
---
# Codex Types and Fields

A **type** is a kind of [Codex](#help:codex.overview) entry — Characters, Voices, Plot, and so on. Every type owns its own set of **fields**, and those fields are what an entry of that type asks you to fill in. A Character might carry Role, Age, and Motivation; a type you invent called Dragon might carry Wingspan, Hoard, and Temperament.

Types belong to the Codex they live in, so changing one project's Characters type never touches another project's.

## Managing types

Right-click a Codex in the [navigation tree](#help:manuscript.nav-tree) and choose **Manage Codex Types…**. The dialog lists every type in that Codex with its field count, opens the type editor, and creates new types.

To jump straight to one type's editor, right-click the type itself and choose **Edit Type…**.

## The type editor

The type editor holds the type's name, an optional description, and its field list. From here you can:

- **Add a field** — give it a label and an input style.
- **Rename a field** — the label changes everywhere; values already saved in your entries are untouched.
- **Reorder fields** — drag them into the order you want entries to read.
- **Change a field's input style** — switch between the styles below.
- **Remove a field** — see *Removing and restoring fields*.

Renaming a type or a field never loses data. Behind the scenes each field keeps a permanent internal key that your entries are stored against, so labels are free to change.

## Field input styles

- **Short text** — a single line, for names, ages, and short labels.
- **Long text** — a multi-line box, for descriptions, history, and notes.
- **Choice** — a dropdown you supply the options for.

Each field can also carry **help text**, shown under the field in the entry form, and a **share with AI** setting that controls whether the field's value is offered to the AI as reference context.

## Removing and restoring fields

Removing a field is **not** destructive. The field disappears from the entry form, but every value already saved in your entries is kept. If entries have values in the field, the editor warns you and tells you how many before you confirm.

Removed fields collect in a **Removed fields** area at the bottom of the type editor, with the number of entries still holding a value. **Restore** brings the field back to the form with all those values intact.

## Deleting a type

A type can be moved to [Trash](#help:manuscript.trash) through the normal Codex delete flow. Its fields and all of its entries go with it and come back together when you restore it.

## Types, DOCX, and the AI

Your types drive the rest of the Codex too. A Codex entry [exported to DOCX](#help:import-export.export) writes one heading per active field, and re-importing that document reads the same headings back — so a field you renamed round-trips under its new label. When you [promote an AI review finding](#help:ai.promotion), the promotion dialog lists your project's actual types, including ones you created yourself.
