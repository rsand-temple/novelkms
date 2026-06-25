package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The memory-document status of one chapter within a book, used to warn the
 * author before a review whether the preceding chapters' continuity context is
 * trustworthy.
 *
 * <p>{@code state} is computed over the chapters in linear book order:
 * <ul>
 *   <li>{@code MISSING} — the chapter has no memory document.</li>
 *   <li>{@code STALE_CONTENT} — the document predates the chapter's latest scene
 *       edit ({@code generatedAt < contentEditedAt}); the summary no longer
 *       reflects the prose. This is the robust signal.</li>
 *   <li>{@code OUT_OF_SEQUENCE} — the document is older than that of a chapter
 *       earlier in book order ({@code generatedAt} dips below the running max);
 *       a likely "refreshed later chapters but skipped this one" gap. A softer
 *       advisory, since a deliberate single-chapter refresh can trip it.</li>
 *   <li>{@code OK} — present and current.</li>
 * </ul>
 * Precedence when more than one applies: MISSING &gt; STALE_CONTENT &gt;
 * OUT_OF_SEQUENCE &gt; OK.
 */
public record ChapterMemoryStatus(
        UUID chapterId,
        int chapterNumber,
        String title,
        boolean hasDoc,
        Instant generatedAt,
        Instant contentEditedAt,
        String source,
        String state) {

    public static final String OK              = "OK";
    public static final String MISSING         = "MISSING";
    public static final String STALE_CONTENT   = "STALE_CONTENT";
    public static final String OUT_OF_SEQUENCE = "OUT_OF_SEQUENCE";
}
