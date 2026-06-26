package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Status of a book's summary, plus its chapter-summary coverage, used to drive
 * the book-summary card and the pre-generation warning. Returned even when no
 * book summary exists yet, so the UI can show coverage before the first run.
 *
 * <p>{@code stale} is true only when a book summary exists <em>and</em> the
 * chapter summaries it was built from have moved on since: a chapter now lacks a
 * summary ({@code missingCount > 0}), a chapter summary drifted from its prose
 * ({@code staleChapterCount > 0}), or some chapter summary was (re)generated
 * after the book summary ({@code latestChapterSummaryAt > generatedAt}).
 */
public record BookSummaryStatus(
        UUID bookId,
        boolean hasDoc,
        Instant generatedAt,
        int wordCount,
        String source,
        boolean stale,
        int chapterCount,
        int summarizedCount,
        int missingCount,
        int staleChapterCount) {
}
