package com.richardsand.novelkms.ai;

import com.richardsand.novelkms.ai.impl.OpenAiProvider;

/**
 * Abstraction over an AI provider. The rest of the application is
 * provider-agnostic: it resolves a provider by key from the registry and calls
 * the appropriate method. The first implementation is {@link OpenAiProvider};
 * Anthropic, Gemini, etc. can be added later without touching callers.
 *
 * <p>A chapter review and a scene review use the same call; they differ only in
 * the {@link ReviewRequest}'s scope.
 */
public interface AiProvider {

    /** Stable registry key, e.g. {@code "OPENAI"}. Matches {@code ai_credential.provider}. */
    String providerKey();

    /** Fallback model used when neither the request nor the credential specifies one. */
    String defaultModel();

    /** Executes a review synchronously. */
    ReviewResult review(ReviewRequest request) throws AiProviderException;

    /**
     * Generates one chapter's memory document synchronously by filling the
     * supplied template from the chapter text. Unlike {@link #review}, this
     * produces a free-text document (no recommendations, no JSON contract).
     */
    MemoryResult generateMemory(MemoryRequest request) throws AiProviderException;

    /**
     * Generates one chapter's summary synchronously: a single human-readable
     * paragraph distilled from the chapter prose. Free-text output, no contract.
     * Independent of memory documents.
     */
    SummaryResult generateChapterSummary(SummaryRequest request) throws AiProviderException;

    /**
     * Generates a whole-book summary synchronously from the supplied chapter
     * summaries (in book order) — never from manuscript prose. Free-text output,
     * bounded to {@link BookSummaryRequest#maxWords()}.
     */
    SummaryResult generateBookSummary(BookSummaryRequest request) throws AiProviderException;

    /**
     * Generates one chapter's editorial synchronously: a short editorial reading
     * of the chapter (tone, genre drift, character arcs, storyline evolution).
     * Free-text output, no JSON contract, and deliberately not a line-level
     * review. Uses the same context inputs as a chapter review (preceding memory
     * documents and pinned Codex reference), but its output is never consumed by
     * any other AI function.
     */
    EditorialResult generateEditorial(EditorialRequest request) throws AiProviderException;

    /**
     * Fills in the structured fields and body of a single codex entry
     * synchronously, using manuscript context (chapter summaries) and pinned
     * codex entries as grounding. Returns suggested field values and a prose body
     * description; the caller is responsible for merging them into the stored
     * entry. Prompt version: {@code codex-fill-v1}.
     */
    CodexFillResult fillCodexEntry(CodexFillRequest request) throws AiProviderException;

    /**
     * Converts supplied, structured weather facts into author-facing scene guidance.
     * The AI must not become the weather authority; callers provide the weather facts
     * and source metadata that ground the answer.
     */
    WeatherInterpretationResult interpretWeather(WeatherInterpretationRequest request) throws AiProviderException;
}
