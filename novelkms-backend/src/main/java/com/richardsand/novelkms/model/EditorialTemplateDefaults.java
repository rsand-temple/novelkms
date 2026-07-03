package com.richardsand.novelkms.model;

/**
 * Factory-default system prompt for chapter-editorial generation — the
 * developmental-editor persona the AI adopts when it gives its overall
 * impressionistic read of a chapter.
 *
 * <p>This is the SYSTEM default in the template resolution chain
 * ({@code book -> project -> user global -> system}), mirroring
 * {@link ChapterMemoryTemplateDefaults} and {@link AiFormInstructionsDefaults}.
 * Like those constants, the system default has no database row and is therefore
 * uneditable by construction.
 *
 * <p>An editorial is deliberately NOT a line-level review (no findings list,
 * no spelling/grammar unless egregious) and its output is never consumed by any
 * other AI function. Authors who want a different editorial voice or focus supply
 * a user-global, project, or book override.
 */
public final class EditorialTemplateDefaults {

    private EditorialTemplateDefaults() {}

    /** Source scope label returned when this constant is the governing template. */
    public static final String SYSTEM_SCOPE = "SYSTEM";

    /**
     * The factory editorial prompt. Matches the behavior of the hard-coded
     * {@code EDITORIAL_WRAPPER} constant in {@code OpenAiProvider}: a warm,
     * direct developmental-editor impression of tone, genre consistency, character
     * arcs, and storyline evolution; kept to roughly half a page; no findings list.
     */
    public static final String SYSTEM_DEFAULT = """
            You are an experienced developmental editor giving the author your \
            overall editorial impression of a single chapter of their novel. Read \
            the chapter and share what you think of it as a whole: its tone and \
            mood, whether the genre feels consistent or is drifting, how the \
            characters and their arcs are developing, and how the storyline is \
            evolving. Where prior-chapter context is provided, judge continuity \
            and momentum against it. Write in warm, direct prose addressed to the \
            author — a few short paragraphs at most, roughly half a page; less is \
            more. Do NOT produce a list of findings, and do NOT point out \
            spelling, grammar, punctuation, or other line-level issues unless \
            something is truly egregious — those belong to a separate review. Do \
            not restate the plot back as a summary; give your editorial read on \
            it. Output only the editorial.""";
}
