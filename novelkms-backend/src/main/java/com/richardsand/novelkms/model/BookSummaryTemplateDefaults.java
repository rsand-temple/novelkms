package com.richardsand.novelkms.model;

/**
 * Factory-default system prompt for book-summary generation — the instruction
 * the AI follows when it synthesizes the per-chapter summaries into a whole-book
 * synopsis.
 *
 * <p>This is the SYSTEM default in the template resolution chain
 * ({@code book -> project -> user global -> system}), mirroring
 * {@link ChapterMemoryTemplateDefaults} and {@link AiFormInstructionsDefaults}.
 * Like those constants, the system default has no database row and is therefore
 * uneditable by construction.
 *
 * <p>The word-count ceiling (1 000 words) is stated explicitly in the text below.
 * Authors who want a different limit or style supply a user-global, project, or
 * book override; when an override is active the limit stated in that override
 * governs instead. The override is used verbatim by the provider — no token
 * substitution is applied.
 */
public final class BookSummaryTemplateDefaults {

    private BookSummaryTemplateDefaults() {}

    /** Source scope label returned when this constant is the governing template. */
    public static final String SYSTEM_SCOPE = "SYSTEM";

    /**
     * The factory book-summary prompt. Matches the behavior of the hard-coded
     * {@code BOOK_SUMMARY_WRAPPER} constant in {@code OpenAiProvider}: a cohesive
     * prose overview built from the supplied chapter summaries, capped at
     * 1 000 words, with no invented material.
     */
    public static final String SYSTEM_DEFAULT = """
            You are writing a synopsis of an entire novel from the per-chapter \
            summaries provided below, which are given in reading order. Synthesize \
            them into one cohesive, human-readable overview of the whole book — the \
            overall arc, the principal characters, and how the story resolves. Write \
            in flowing prose with no headings, bullet points, or lists, and no \
            preamble or labels. Use no more than 1000 words; do not exceed this \
            limit. Base everything strictly on the supplied chapter summaries; do not \
            invent material not present in them. Output only the synopsis.""";
}
