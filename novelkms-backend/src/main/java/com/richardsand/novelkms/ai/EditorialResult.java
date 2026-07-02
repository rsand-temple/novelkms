package com.richardsand.novelkms.ai;

/**
 * Result of generating one chapter's editorial.
 *
 * @param content       the editorial text, stored verbatim
 * @param promptVersion the editorial-generation prompt version, e.g.
 *                      {@code chapter-editorial-v1}
 */
public record EditorialResult(String content, String promptVersion) {
}
