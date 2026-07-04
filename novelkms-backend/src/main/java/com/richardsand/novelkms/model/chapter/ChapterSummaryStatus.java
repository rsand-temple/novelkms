package com.richardsand.novelkms.model.chapter;

import java.time.Instant;
import java.util.UUID;

/**
 * One chapter's row in the book's aggregated chapter-summary view, carrying both
 * the summary text (for the read-only "view chapter summary" dialog) and a
 * staleness {@code state} (for the pre-book-summary coverage warning).
 *
 * <p>{@code state} is computed per chapter, independently — unlike memory
 * documents, chapter summaries are standalone paragraphs with no inter-chapter
 * ordering semantics, so there is no {@code OUT_OF_SEQUENCE} state:
 * <ul>
 *   <li>{@code MISSING} — the chapter has no summary.</li>
 *   <li>{@code STALE_CONTENT} — the summary predates the chapter's latest scene
 *       edit ({@code generatedAt < contentEditedAt}); it no longer reflects the
 *       prose.</li>
 *   <li>{@code OK} — present and current.</li>
 * </ul>
 * Precedence when more than one applies: MISSING &gt; STALE_CONTENT &gt; OK.
 */
public record ChapterSummaryStatus(
        UUID chapterId,
        int chapterNumber,
        String title,
        boolean hasDoc,
        String content,
        Instant generatedAt,
        Instant contentEditedAt,
        String source,
        String state) {

    public static final String OK            = "OK";
    public static final String MISSING       = "MISSING";
    public static final String STALE_CONTENT = "STALE_CONTENT";
}
