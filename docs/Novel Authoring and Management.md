# NovelKMS Product Scope

NovelKMS is a novel knowledge-management system with a manuscript editor attached. Its purpose is to help an author draft long-form fiction while preserving the surrounding project knowledge: manuscript structure, characters, canon, timelines, research, review history, exports, and eventually website/publication metadata.

This document is the product-scope document, not an implementation log. Detailed architecture, deployment, and AI design notes live in the companion documents.

## Core product principles

- The manuscript is organized as `Project -> Book -> Part -> Chapter -> Scene`.
- Scenes are the primary persistence and editing boundary, even when the editor displays a full chapter, part, or book.
- Project knowledge should be durable, searchable, and linked to the manuscript where possible.
- AI output is treated as reviewable project data, not as transient chat.
- The author remains in control; NovelKMS does not automatically rewrite or modify the manuscript.
- Portability matters: export, backups, and avoiding vendor lock-in are part of the product identity.

## Current implemented foundation

### Manuscript authoring

- Multiple projects and books.
- Parts, chapters, and scenes.
- Drag-and-drop nav tree reordering and reparenting for parts, chapters, and scenes.
- Inline rename and nav context menu.
- Single-scene, chapter, part, and book editing scopes.
- Continuous part/book draft editing while preserving scene-level persistence.
- Auto-created initial scene when creating a chapter.
- Live and aggregate word counts.
- Resettable chapter numbering.
- Soft delete / Trash for projects, books, chapters, scenes, codex entries, codex categories, and AI reviews.

### Rich text editor

- TipTap-based editor.
- Paragraph styles and headings.
- Inline bold, italic, underline, and strike.
- Lists, block quotes, horizontal scene breaks, and in-editor base64 images.
- Book-level page layout preview for cover/part pages and drafting aids.
- Cover and part templates with field tokens.
- Search and replace across the current scene, chapter, part, or book scope.

### Scratchpad

Every book has a Scratchpad: somewhere to put scenes you have written but do not want in the
book. Cut a scene that is not working, park an alternate opening, keep a fragment you might use
later — drag it into the Scratchpad and it leaves the manuscript without being deleted.

Nothing in the Scratchpad counts. It does not appear in exports, it is not included in your word
count, and it is never sent to the AI for review, memory, summaries, or editorials. Drag a scene
back into a chapter whenever you want it again.

The Scratchpad sits near the bottom of each book in the navigation tree, below the Codex. You
can add new scenes directly to it, rename and reorder them, and delete them individually — but
the Scratchpad itself is a permanent part of the book and cannot be renamed or removed.

### Import and export

- DOCX import into a new book.
- DOCX export for book, part, chapter, and scene scopes.
- Markdown import/export is planned.
- ePub export is planned or partially in progress; verify current menu/API wiring before documenting it as complete.

### Codex / project knowledge

- Codex category and entry model exists and is used by AI recommendation promotion.
- Character, canon, timeline, and richer worldbuilding entities remain lower-priority future work until the core authoring and AI review workflow stabilizes.

### AI review workflow

- Bring-your-own-key AI settings.
- OpenAI provider first.
- Per-user credentials encrypted at rest.
- Chapter review creates persistent review artifacts.
- Recommendations support lifecycle states: `OPEN`, `ACCEPTED`, `REJECTED`, `FUTURE`, `DELETED`, and `PROMOTED`.
- Review rail lives beside the editor with click-to-scroll using recommendation anchor text.
- Recommendations can be promoted into Codex entries.

## Near-term priorities

1. Keep the manuscript workflow smooth: editing, navigation, search, import, export, and Trash.
2. Finish/repair ePub export menu and endpoint wiring if it has regressed.
3. Add a deliberate view/filter for deferred AI findings.
4. Improve AI prompt context by selectively adding Codex/character/canon/timeline context.
5. Add a style-editor UI for paragraph style definitions.

## Later product areas

- Markdown import/export.
- ePub and PDF export polish.
- Hugo/static-site generation.
- Git integration.
- Version history and manuscript snapshots.
- Continuity reports and “where is this mentioned?” discovery.
- AI continuity, voice, and spoiler checks.
- Collaboration and multi-author support.

## Explicitly not current priorities

- General-purpose AI chat inside the app.
- Automatic prose generation or manuscript rewriting.
- Multi-provider orchestration.
- Collaboration/multi-author workflows.
- Full custom publishing/typesetting engine inside the live editor.
