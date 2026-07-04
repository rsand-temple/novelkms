package com.richardsand.novelkms.ai;

/**
 * Input record for a codex-entry fill request. The caller assembles manuscript
 * context (from chapter summaries), pinned codex entries as reference, a
 * human-readable schema description, and the entry's current partial data
 * before dispatching to an {@link AiProvider}.
 *
 * <p>Prompt version: {@code codex-fill-v1}.
 */
public record CodexFillRequest(
        /** Decrypted API key for the target provider. */
        String apiKey,
        /** Model identifier (may be null/blank; providers fall back to their default). */
        String model,
        /** The entry's title as authored by the writer, e.g. "Elena Vasquez". */
        String entryTitle,
        /** Human-readable category label, e.g. "Character". */
        String categoryLabel,
        /**
         * Human-readable field description block listing field keys, types, options,
         * and help text. Built from the category's {@link com.richardsand.novelkms.model.CodexSchema}.
         */
        String schemaDescription,
        /**
         * Text representation of the entry's current field values that the author has
         * already filled in. The AI is expected to respect these values and not
         * contradict them. Null or "none" when no fields have been filled.
         */
        String existingFields,
        /**
         * Manuscript context: chapter summaries in book order, capped at ~40 000
         * characters. Null when the codex is project-scoped and no book is associated,
         * or when no summaries exist.
         */
        String manuscriptContext,
        /**
         * Pinned codex entries formatted for prompt injection, or null when no entries
         * are pinned. Used as reference canon the AI should not contradict.
         */
        String referenceContext,
        /**
         * Optional one-time author guidance for this single generation — not stored
         * persistently. Null when the author supplied none.
         */
        String userGuidance) {
}
