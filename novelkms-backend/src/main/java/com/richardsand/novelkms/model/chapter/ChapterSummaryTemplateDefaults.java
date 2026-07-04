package com.richardsand.novelkms.model.chapter;

import com.richardsand.novelkms.model.ai.AiFormInstructionsDefaults;

/**
 * Factory-default system prompt for chapter-summary generation — the instruction
 * the AI follows when it writes a single-paragraph summary of a chapter.
 *
 * <p>This is the SYSTEM default in the template resolution chain
 * ({@code book -> project -> user global -> system}), mirroring
 * {@link ChapterMemoryTemplateDefaults} and {@link AiFormInstructionsDefaults}.
 * Like those constants, the system default has no database row and is therefore
 * uneditable by construction.
 *
 * <p>Authors who want a different summarization style — for instance, a
 * third-person point-of-view requirement, or a word-count ceiling — supply that
 * via a user-global, project, or book override through
 * {@code AiPromptTemplateDao}.
 */
public final class ChapterSummaryTemplateDefaults {

    private ChapterSummaryTemplateDefaults() {}

    /** Source scope label returned when this constant is the governing template. */
    public static final String SYSTEM_SCOPE = "SYSTEM";

    /**
     * The factory chapter-summary prompt. Matches the behavior of the hard-coded
     * {@code CHAPTER_SUMMARY_WRAPPER} constant in {@code OpenAiProvider}: one
     * clear, flowing prose paragraph; no headings or lists; strictly grounded in
     * the supplied chapter text.
     */
    public static final String SYSTEM_DEFAULT = """
            You are summarizing one chapter of a novel. Write a single, clear, \
            human-readable paragraph that captures what happens in the chapter: the \
            key events, the characters involved, and how the chapter moves the story \
            forward. Write in flowing prose — no headings, no bullet points, no lists, \
            no preamble or labels. Base every statement strictly on the chapter text; \
            do not invent or speculate. Output only the summary paragraph.""";
}
