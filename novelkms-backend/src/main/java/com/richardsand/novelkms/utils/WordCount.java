package com.richardsand.novelkms.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The canonical word-count for NovelKMS manuscript HTML.
 *
 * <p>This was previously a private method inside {@code SceneDao}. It now has a
 * second caller — the review-network publish path, which must count the words in
 * an assembled chapter snapshot — and a word count that disagrees with itself
 * between the editor and a published review package would be a visible bug in the
 * one number reviewers judge a package by. So there is exactly one implementation
 * and {@code SceneDao} delegates to it.
 *
 * <p>The algorithm must stay consistent with TipTap's {@code CharacterCount.words()}
 * and the frontend's {@code countWords()} helper: strip tags, then count runs of
 * non-whitespace, i.e. JavaScript's {@code /\S+/g}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WordCount {

    /**
     * Counts words in a fragment of TipTap/manuscript HTML.
     *
     * @param html HTML content; null or blank counts as zero
     * @return the number of whitespace-delimited tokens in the tag-stripped text
     */
    public static int fromHtml(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }

        // Replace every tag with a space so words either side of a tag boundary are
        // not merged: "end</p><p>start" must count as two words, not one.
        String text = html.replaceAll("<[^>]+>", " ").trim();
        if (text.isBlank()) {
            return 0;
        }

        int     count  = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return count;
    }
}
