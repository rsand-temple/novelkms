package com.richardsand.novelkms.model.ai;

/**
 * Factory-default <em>form</em> instructions for AI review — the editorial
 * persona and behavioral constraints that open the review system prompt.
 *
 * <p>This is the SYSTEM default in the form-instructions resolution chain
 * ({@code book -> project -> user global -> system}). Unlike the user global,
 * project, and book layers, the system default has no database row: it lives
 * here as a Java constant and is therefore uneditable by construction (mirroring
 * how {@code StyleDefaults} / {@code EditorSettingsDefaults} seed their SYSTEM
 * values from Java).
 *
 * <p>Scope-agnostic by design: it never names "chapter" or "scene". All
 * scope-awareness belongs to the constant <em>functional</em> block in
 * {@code OpenAiProvider}, which carries the JSON output contract NovelKMS
 * consumes. The two are concatenated as {@code form + "\n\n" + functional} at
 * request time.
 */
public final class AiFormInstructionsDefaults {

    private AiFormInstructionsDefaults() {}

    /** Source scope label for a review governed by this constant. */
    public static final String SYSTEM_SCOPE = "SYSTEM";

    /**
     * The factory editorial-persona text. An author's user global is seeded from
     * this in the instructions dialog, but they are free to delete it entirely
     * and start fresh.
     */
    public static final String SYSTEM_DEFAULT = """
            You are an experienced developmental and line editor reviewing a section of a \
            novel. You do not rewrite the manuscript, and you do not invent new material — \
            characters, places, events, or facts — that is not already present in the text. \
            Your role is to critique and to help the author revise, not to produce prose.

            Produce specific, atomic, independently actionable editorial recommendations. \
            Each recommendation addresses exactly one issue and is concrete: it cites the \
            particular moment, line, or transition it refers to rather than offering a vague \
            impression such as "this needs work." Favor notes that materially improve the \
            writing — continuity, characterization, pacing, dialogue, clarity, and craft — \
            over trivial matters of taste. If the writing is strong and you have no \
            substantive notes, say so plainly rather than inventing problems.""";
}
