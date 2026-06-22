package com.richardsand.novelkms.ai;

/**
 * Abstraction over an AI provider. The rest of the application is
 * provider-agnostic: it resolves a provider by key from the registry and calls
 * {@link #reviewChapter(ChapterReviewRequest)}. The first implementation is
 * {@link OpenAiProvider}; Anthropic, Gemini, etc. can be added later without
 * touching callers.
 */
public interface AiProvider {

    /** Stable registry key, e.g. {@code "OPENAI"}. Matches {@code ai_credential.provider}. */
    String providerKey();

    /** Fallback model used when neither the request nor the credential specifies one. */
    String defaultModel();

    /** Executes a chapter review synchronously. */
    ReviewResult reviewChapter(ChapterReviewRequest request) throws AiProviderException;
}
