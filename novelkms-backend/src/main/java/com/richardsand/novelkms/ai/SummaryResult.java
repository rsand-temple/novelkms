package com.richardsand.novelkms.ai;

/**
 * Result of generating a chapter summary or a book summary.
 *
 * @param content       the summary text, stored verbatim
 * @param promptVersion the summary-generation prompt version, e.g.
 *                      {@code chapter-summary-v1} or {@code book-summary-v1}
 */
public record SummaryResult(String content, String promptVersion) {
}
