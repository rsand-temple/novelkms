package com.richardsand.novelkms.model;

import java.util.List;

/**
 * The fixed roster of style keys and their factory-default definitions.
 *
 * The roster is intentionally closed — NovelKMS is a manuscript tool, not a
 * general word processor, and book printers won't honor arbitrary styles. Only
 * the {@link StyleDefinition} values are editable (per scope); the set of keys
 * is not user-extensible here. A future "add custom style" form can append to
 * this list without changing the resolution model.
 */
public final class StyleDefaults {

    private StyleDefaults() {}

    public static final String NORMAL           = "normal";
    public static final String H1               = "h1";
    public static final String H2               = "h2";
    public static final String H3               = "h3";
    public static final String BLOCKQUOTE       = "blockquote";
    public static final String EMPHASIS         = "emphasis";
    public static final String REPORT           = "report";
    public static final String CHAPTER_TITLE    = "chapter_title";
    public static final String CHAPTER_SUBTITLE = "chapter_subtitle";
    public static final String COVER_TITLE      = "cover_title";
    public static final String COVER_SUBTITLE   = "cover_subtitle";
    public static final String PART_TITLE       = "part_title";
    public static final String PART_SUBTITLE    = "part_subtitle";

    /** Ordered roster — drives the Style dropdown and "seed all globals". */
    public static final List<String> STYLE_KEYS = List.of(
            NORMAL, H1, H2, H3, BLOCKQUOTE, EMPHASIS, REPORT,
            CHAPTER_TITLE, CHAPTER_SUBTITLE, COVER_TITLE, COVER_SUBTITLE,
            PART_TITLE, PART_SUBTITLE
    );

    private static final String SERIF   = "Georgia, serif";
    private static final String MONO    = "\"Courier New\", Courier, monospace";

    public static boolean isValidKey(String key) {
        return key != null && STYLE_KEYS.contains(key);
    }

    /** Factory default for a style key. Throws for unknown keys (validate first). */
    public static StyleDefinition defaultFor(String key) {
        switch (key) {
            case NORMAL:
                return def(SERIF, "1rem",     false, false, "1.5em", "0",   "0",   "0");
            case H1:
                return def(SERIF, "1.6rem",   true,  false, "0",     "0",   "0",   "0.5em");
            case H2:
                return def(SERIF, "1.3rem",   true,  false, "0",     "0",   "1em", "0.5em");
            case H3:
                return def(SERIF, "1.1rem",   true,  false, "0",     "0",   "1em", "0.5em");
            case BLOCKQUOTE:
                return def(SERIF, "1rem",     false, true,  "0",     "2em", "1em", "1em");
            case EMPHASIS: // David's notebook, song lyrics — italic body
                return def(SERIF, "1rem",     false, true,  "1.5em", "0",   "0",   "0");
            case REPORT:   // a "typed report" — indented Courier block
                return def(MONO,  "0.95rem",  false, false, "0",     "2em", "1em", "1em");
            case CHAPTER_TITLE:
                return def(SERIF, "2rem",     true,  false, "0",     "0",   "0",   "1em");
            case CHAPTER_SUBTITLE:
                return def(SERIF, "1.3rem",   false, true,  "0",     "0",   "0",   "1em");
            case COVER_TITLE:
                return def(SERIF, "3rem",     true,  false, "0",     "0",   "0",   "0.5em");
            case COVER_SUBTITLE:
                return def(SERIF, "1.5rem",   false, true,  "0",     "0",   "0",   "1em");
            // Part title/subtitle default to the same values as chapter, but are
            // independent rows so they can be tuned separately later.
            case PART_TITLE:
                return def(SERIF, "2rem",     true,  false, "0",     "0",   "0",   "1em");
            case PART_SUBTITLE:
                return def(SERIF, "1.3rem",   false, true,  "0",     "0",   "0",   "1em");
            default:
                throw new IllegalArgumentException("Unknown style key: " + key);
        }
    }

    private static StyleDefinition def(String fontFamily, String fontSize, boolean bold, boolean italic,
            String firstLineIndent, String textIndent, String spacingBefore, String spacingAfter) {
        return StyleDefinition.builder()
                .fontFamily(fontFamily)
                .fontSize(fontSize)
                .bold(bold)
                .italic(italic)
                .firstLineIndent(firstLineIndent)
                .textIndent(textIndent)
                .spacingBefore(spacingBefore)
                .spacingAfter(spacingAfter)
                .build();
    }
}
