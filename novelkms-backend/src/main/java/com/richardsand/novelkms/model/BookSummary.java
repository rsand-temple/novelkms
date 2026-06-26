package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A per-book "book summary": a synopsis of the whole book of no more than ~1000
 * words, generated explicitly by the author and then optionally hand-edited.
 * There is at most one current summary per book ({@code book_summary.book_id} is
 * unique); regenerating overwrites it and refreshes {@code generatedAt}.
 *
 * <p>The book summary is generated <em>entirely</em> from the book's chapter
 * summaries, concatenated in linear book order — never from the manuscript prose
 * directly. A full-length manuscript is too large to summarize reliably in one
 * pass, so the chapter summaries act as a compact, already-distilled input. The
 * summary is therefore only as current as the chapter summaries it consumed:
 * staleness is reported when a chapter has gained/changed a summary since (see
 * {@code BookSummaryStatus}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSummary {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private String content;

    /** Word count of {@code content}, computed on generation and on edit. */
    @JsonProperty
    private int wordCount;

    /** {@code AI} (provider-generated) or {@code EDITED} (author hand-edited). */
    @JsonProperty
    private String source;

    /** Summary-generation prompt version, e.g. {@code book-summary-v1}; null for a purely hand-authored edit. */
    @JsonProperty
    private String promptVersion;

    /** Model that generated the summary; null for a hand-authored edit. */
    @JsonProperty
    private String model;

    @JsonProperty
    private Instant generatedAt;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
