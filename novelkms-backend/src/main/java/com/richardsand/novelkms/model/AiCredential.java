package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-user AI provider credential. Multiple credentials may exist per user,
 * across providers and even multiple per provider (distinguished by {@code label}).
 *
 * <p>This model is intentionally free of the API key and its ciphertext so it is
 * always safe to serialize to the client. The only key-related field exposed is
 * {@code keyLast4}, a non-secret display hint.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCredential {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID userId;

    /** Provider key, e.g. {@code "OPENAI"}. */
    @JsonProperty
    private String provider;

    /** User-supplied label, lets a user hold more than one key per provider. */
    @JsonProperty
    private String label;

    /** Default model used when a review does not specify one, e.g. {@code "gpt-5.4"}. */
    @JsonProperty
    private String defaultModel;

    /** True when this is the default credential for its provider. */
    @JsonProperty
    private boolean defaultCredential;

    @JsonProperty
    private String status;

    /** Non-secret display hint — the last few characters of the API key. */
    @JsonProperty
    private String keyLast4;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
