package com.richardsand.novelkms.ai;

import java.util.Map;

/**
 * Result of a codex-entry fill request ({@link AiProvider#fillCodexEntry}).
 *
 * <p>{@code fields} maps each schema field key to the AI-suggested value.
 * Fields the AI could not determine are present with an empty string value.
 * {@code body} is a plain-text prose description of the entry overall; the
 * caller is responsible for wrapping it in {@code <p>} tags before storing as
 * TipTap HTML. {@code rawJson} retains the raw provider response for debugging.
 */
public record CodexFillResult(
        /** AI-suggested structured field values, keyed by schema field key. Never null. */
        Map<String, String> fields,
        /**
         * Plain-text prose paragraph(s) describing the entry. May be empty if the
         * provider returned nothing useful. The caller wraps each line in {@code <p>}
         * tags before storing as TipTap-compatible HTML.
         */
        String body,
        /** Raw JSON string returned by the provider (for diagnostics). */
        String rawJson,
        /** Prompt version that produced this result, e.g. {@code "codex-fill-v1"}. */
        String promptVersion) {
}
