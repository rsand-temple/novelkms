package com.richardsand.novelkms.ai;

/**
 * Result of generating one chapter's memory document.
 *
 * @param content       the filled-in memory document text, stored verbatim
 * @param promptVersion the memory-generation prompt version, e.g. {@code memory-v1}
 */
public record MemoryResult(String content, String promptVersion) {
}
