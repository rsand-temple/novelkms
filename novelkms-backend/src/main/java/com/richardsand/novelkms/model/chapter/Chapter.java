package com.richardsand.novelkms.model.chapter;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @JsonProperty
    private UUID id;

    /**
     * Nullable. Set for manuscript chapters (which belong to a book, directly
     * or via a part). NULL for codex chapters, which belong to a codex instead.
     */
    @JsonProperty
    private UUID bookId;

    /** Nullable — chapter may belong directly to a book without a part. */
    @JsonProperty
    private UUID partId;

    /**
     * Nullable. Set for codex chapters (a category folder inside a codex). NULL
     * for manuscript chapters. A chapter belongs to a book XOR a codex.
     */
    @JsonProperty
    private UUID codexId;

    /**
     * For codex chapters, the category key (CHARACTER, PLOT, NOTES, ...) from
     * codex_category. NULL for manuscript chapters and for plain (uncategorized)
     * codex chapters.
     */
    @JsonProperty
    private String codexCategory;

    /**
     * Nullable. Set for the one Scratchpad chapter of a book — a holding pen for
     * scenes that are not part of the manuscript. NULL for every other chapter.
     *
     * <p>A Scratchpad chapter has {@code bookId}, {@code partId} and
     * {@code codexId} all NULL, which is what keeps it out of every book-rooted
     * query (numbering, outline, exports, rollups, AI context) without those
     * queries needing to know it exists. Its content is never rendered into the
     * book and never fed to any AI workflow.
     */
    @JsonProperty
    private UUID scratchpadBookId;

    @JsonProperty
    private String title;

    /** Optional subtitle — may be null or blank. */
    @JsonProperty
    private String subtitle;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String notes;

    /**
     * When true, this chapter's computed chapterNumber resets to 1, and every
     * subsequent chapter in book order continues counting from here until the
     * next reset point. Persisted; defaults to false. Not meaningful for codex
     * chapters (which are not numbered).
     */
    @JsonProperty
    private boolean resetsNumbering;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;

    /** computed, not stored. 0 for codex chapters (they are not numbered). */
    @JsonProperty
    private int chapterNumber;

    public void setChapterNumber(int chapterNumber) {
        this.chapterNumber = chapterNumber;
    }
}
