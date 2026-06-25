package com.richardsand.novelkms.model;

/**
 * Factory-default <em>memory document template</em> — the section structure the
 * AI fills in when generating a chapter's memory document.
 *
 * <p>This is the SYSTEM default in the template resolution chain
 * ({@code book -> project -> user global -> system}), mirroring how
 * {@link AiFormInstructionsDefaults} seeds AI form instructions. Like that
 * constant, the system default has no database row and is therefore uneditable
 * by construction.
 *
 * <p>Deliberately generic: it carries only the section headers and their intent,
 * never a project-specific cast. An author who wants a fixed character roster in
 * section 2 supplies it via a user-global, project, or book override — that
 * roster is project knowledge, not a system default.
 */
public final class ChapterMemoryTemplateDefaults {

    private ChapterMemoryTemplateDefaults() {}

    /** Source scope label for a memory document generated against this constant. */
    public static final String SYSTEM_SCOPE = "SYSTEM";

    /**
     * The factory template. An author's user global is seeded from this in the
     * template editor, but they are free to replace it entirely (for instance,
     * to pin their cast under "Character State Changes").
     */
    public static final String SYSTEM_DEFAULT = """
            CHAPTER {N}

            1. Key Events
            - What actually happens this chapter, as cause -> effect.

            2. Character State Changes
            - One line per character who changes this chapter. Include emotional,
              informational, or physical changes. Omit characters who do not change.

            3. New Information Revealed
            - Plot-critical facts the reader learns.

            4. Open Questions / Unresolved Threads
            - Mysteries, risks, or unknowns introduced or advanced.

            5. Timeline / Location Anchors
            - Where and when the chapter occurs, if relevant.

            6. Canon Flags (if any)
            - Any tension with, or deviation from, established canon.""";
}
