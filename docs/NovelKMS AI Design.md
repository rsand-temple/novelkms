# NovelKMS AI Design

## Purpose

AI in NovelKMS is an editorial-review system, not a manuscript-generation system. The AI layer critiques, analyzes, organizes, and helps triage revision work while keeping the manuscript under the author's control. AI output is stored as first-class NovelKMS data, not treated as a transient chat transcript.

## Current AI model

- Bring-your-own-key model.
- OpenAI, Anthropic (Claude), and Google Gemini providers behind the shared `AiProvider` interface.
- Per-user AI credentials encrypted at rest, multiple providers/labels per user.
- Review artifacts and recommendations persisted.
- No automatic manuscript modification. No general chat UI.

## Credential model

`ai_credential` stores provider, label, encrypted API key, key-last-four hint, default model, default flag, and status. Keys are write-only from the frontend; only masked hints returned. `SecretCipher` encrypts with AES-GCM using `NOVELKMS_ENCRYPTION_KEY`.

## Provider abstraction

`AiReviewService` holds a `Map<String, AiProvider>` keyed by provider string and resolves the right instance from the stored credential at call time. All methods synchronous.

```java
public interface AiProvider {
    String providerKey();
    String defaultModel();
    ReviewResult review(ReviewRequest request) throws AiProviderException;
    MemoryResult generateMemory(MemoryRequest request) throws AiProviderException;
    SummaryResult generateChapterSummary(SummaryRequest request) throws AiProviderException;
    SummaryResult generateBookSummary(BookSummaryRequest request) throws AiProviderException;
    EditorialResult generateEditorial(EditorialRequest request) throws AiProviderException;
    CodexFillResult fillCodexEntry(CodexFillRequest request) throws AiProviderException;
    WeatherInterpretationResult interpretWeather(WeatherInterpretationRequest request) throws AiProviderException;
}
```

Current providers:

- `OpenAiProvider` — key `OPENAI`, default `gpt-5.4`. `Authorization: Bearer`, `response_format: json_object` for JSON calls.
- `AnthropicProvider` — key `ANTHROPIC`, default `claude-sonnet-4-6`. `x-api-key` + `anthropic-version` headers. System prompt as top-level `"system"` field. `max_tokens` required. No `response_format` equivalent; `stripCodeFences` handles edge cases.
- `GeminiProvider` — key `GEMINI`, default `gemini-2.5-flash`. API key as `?key=` URL param, model in URL path. `systemInstruction.parts` structure. `responseMimeType: "application/json"` for JSON calls. Response from `candidates[0].content.parts[0].text` (skips `thought: true` parts from reasoning models).

### Adding a new provider

1. Implement `AiProvider` in a new class.
2. Add it to the registry in `NovelKmsServer`.
3. Add provider key/label to `aiProviders.js` and `AiCredentialsPanel.jsx`.
4. No migration, no new endpoints, no DAO changes.

## Review artifacts

A review run creates an immutable `ai_review` row. Recommendations are individually triageable:

| Status      | Meaning                   | Tab      |
| ----------- | ------------------------- | -------- |
| `OPEN`      | Undecided                 | Active   |
| `DEFERRED`  | Valid, not now            | Active   |
| `DONE`      | Acted on / addressed      | Resolved |
| `DISMISSED` | Disagree / not applicable | Resolved |
| `PROMOTED`  | Converted to Codex entry  | Resolved |
| `DELETED`   | Admin cleanup only        | Hidden   |

Review rail triages bug-tracker style: Active (OPEN+DEFERRED), Resolved (DONE+DISMISSED+PROMOTED), History. Per-finding hard delete removed.

## Review rail UX

Collapsible right-side rail beside the editor. Review history selector, run controls, recommendation cards with Mark Done / Dismiss / Defer / Reopen actions. Add to Codex in overflow. Open-findings badge when collapsed. Anchor text click-to-scroll via transient ProseMirror highlights.

## Codex promotion

Recommendations may include `codexCategory`/`codexTitle` suggestions. Promotion creates a Codex entry, records the target ID, and marks the recommendation `PROMOTED`. Categories: CHARACTER, VOICE, PLOT, WORLD, TIMELINE, CANON, NOTES. A draft/suggestion layer should be considered before AI findings become canonical automatically.

Promotion targets a per-instance project Type (E8). The promote dialog lists the
project codex's actual Types by id; the author's pick is sent as codexTypeId and the
entry is created under that Type after server-side validation that it belongs to the
review's project codex. When no type is chosen, the AI's broad category is mapped to
the seeded Type by system_key (§14 compatibility). The codex-fill-v1 prompt is
unchanged — including the project's Types in the prompt remains deferred.

E9 (2026-07-22) closes the Extensible Codex feature. No prompt, contract, or provider
behavior changed. The §14 compatibility layer is unchanged and is now the permanent
Phase-1 arrangement: `codex-fill-v1` still receives the resolved per-instance Type
schema (E3), the model still returns a broad category, and the backend still maps that
category to the project's Type by `system_key` unless the author supplied an explicit
`codexTypeId` (E8). "Prompt includes the project's actual types" remains deferred.

User-facing wording in the AI surfaces was updated: the promote picker reads "Codex
type" (E8), the AI-context dialog's group heading falls back to "Type" rather than
"Category", and the bulk share/unshare snackbars say "type" rather than "category". The
help topic `ai.promotion` now describes the real dialog — the author chooses the target
Type from the project's actual Types, pre-selected to match the finding's category, with
a Notes fallback — and cross-links the new `codex.types` topic.

## Prompt/versioning policy

Every meaningful prompt behavior change bumps the version. Version tracks content, not transport. Shared across providers.

| Version                | Purpose                                                   |
| ---------------------- | --------------------------------------------------------- |
| `chapter-review-v7`    | Current review (JSON contract unchanged from v1 shape).   |
| `memory-v2`            | Memory doc with author guidance fence.                    |
| `chapter-summary-v2`   | Chapter summary with guidance fence.                      |
| `book-summary-v2`      | Book synopsis from chapter summaries with guidance fence. |
| `chapter-editorial-v1` | Per-chapter editorial reading (free-text, no findings).   |
| `codex-fill-v1`        | Codex entry AI fill-in (JSON: `{fields, body}`).          |

## Form/functional architecture

Review system prompt = `form + "\n\n" + functional`:

- **Form** — editorial persona, author-editable at four scopes (book → project → user → system default). System default is a Java constant. Provenance stamped per review.
- **Functional** — JSON output contract (field requirements, severity/codex enums, anchorText spec). Scope-aware via `%unit%`/`%categories%` substitution. Non-editable. Duplicated self-contained in each provider.

## Book review workflow

Memory documents summarize preceding chapters as continuity context. Generated per chapter from a template. Aggregated into the review prompt as "story so far." Pre-review staleness gating warns when preceding chapters have missing/stale/out-of-sequence memory documents.

## Chapter & book summaries

Independent of memory documents — separate tables, prompts, DAOs, UI.

- **Chapter summary** — one paragraph per chapter (`chapter-summary-v2`), optionally hand-edited.
- **Book summary** — synopsis ≤ ~1000 words (`book-summary-v2`), built from chapter summaries in book order (never raw prose).
- **Coverage gate** — `PreBookSummaryDialog` warns on missing/stale chapters before book-summary generation.

## Memory/summary editing surface

Edited in EditorPanel via fixed nav-tree leaves (Memory/Summary per chapter, Summary per book). HTML storage; `AiReviewService` strips to plain text before prompts. Regeneration warns when content exists. ReviewRail Memory tab and "View chapter summaries…" dialog remain as read-only peek surfaces.

## Editorials

Per-chapter editorial reading — tone, genre drift, character arcs, storyline evolution. Never consumed by other AI functions. `chapter-editorial-v1`, free-text, ~half a page. Uses same context as a chapter review (preceding memory + pinned Codex). One per chapter, overwrite on regenerate, optionally hand-edited. Edited via third fixed chapter nav leaf. No ReviewRail tab, no staleness view.

## One-time author guidance

All generation flows accept an optional free-text guidance note for a single run. Distinct from persistent form/template cascades. Stored as provenance on the resulting artifact. Appended to the user message (never the system prompt) as a clearly fenced block. UI field pre-fills from previous guidance and is not auto-cleared.

## Per-provider AI document variants

Each AI doc family (memory, chapter summary, chapter editorial, book summary) stores one document per provider. Editor toolbar provider selector; instant switching (all variants fetched once). Generate/clear per provider. Coverage/staleness surfaces remain default-provider only (Phase 3 deferred).

## Codex entry AI fill-in

A per-entry AI generation flow that fills in a codex entry's structured fields and body from the manuscript. The author starts an entry with just a name, clicks Generate, and gets a first draft to edit. Not a stored AI artifact family — the result is transient, applied to the form (triggering autosave) without an intermediate save.

`AiProvider.fillCodexEntry` (prompt `codex-fill-v1`) returns `{"fields":{"key":"value"},"body":"plain text"}`. The frontend wraps body paragraphs in `<p>` tags for the TipTap editor.

**Context is scope-aware.** A codex is scoped to one book or one whole project (`Codex.bookId` / `Codex.projectId`). Book-scoped: that book's chapter summaries. Project-scoped (series-wide): every book in the project, each preceded by `## Book Title` when multi-book. Pinned codex entries from the same codex always included as reference (the entry being filled is excluded). Chapter summaries **required** — without them the model has no context and returns empty fields; the backend hard-fails with `no_chapter_summaries`. Context capped at 40,000 characters. One-time author guidance supported.

**Codex entry DOCX export/import.** Round-trip contract: H1 title, H3+Normal per schema field, H2 "Description," body paragraphs. Import is direct-save, no preview. Endpoints: `GET/POST /api/scenes/{sceneId}/codex-docx`.

## Slice 1D — reviewer AI-assist self-disclosure

Reviews carry an `ai_assisted` boolean the reviewer sets themselves (spec §30.2
Q15/Q16). It is self-disclosure only — no detection, no enforcement. Surfaced as an
"AI-assisted" chip on the author's Reviews Received card so the author can weigh the
feedback accordingly. Stored on `human_review.ai_assisted`; recomputed nowhere (it is
the reviewer's assertion, not a derived signal). No AI provider is involved in 1D.

## Non-goals for the current AI slice

- General chat interface.
- Automatic manuscript rewriting or prose insertion.
- Streaming chat UX or autonomous agents.
- Multi-chapter/book orchestration without async execution.
- Provider billing/cost reporting.

## Near-term AI priorities

1. Deliberate deferred-findings view.
2. Move long-running review workflows to async execution.
3. Provider-variants Phase 3: provider-aware coverage/staleness, review-history grouping.
4. Codex draft/suggestion layer.
5. Preserve prompt versioning for every output-shape change.
