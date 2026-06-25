package com.richardsand.novelkms.ai;

/**
 * Abstraction over an AI provider. The rest of the application is
 * provider-agnostic: it resolves a provider by key from the registry and calls
 * {@link #review(ReviewRequest)}. The first implementation is
 * {@link OpenAiProvider}; Anthropic, Gemini, etc. can be added later without
 * touching callers.
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
}
