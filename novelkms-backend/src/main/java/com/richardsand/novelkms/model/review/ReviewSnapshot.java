package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The immutable half of a Review Package (spec §8.3): a frozen copy of the
 * manuscript at the moment of publication. Written once, never updated. There is
 * deliberately no update method anywhere in the DAO.
 *
 * <p>Every displayable string is denormalized onto the row — {@code sourceTitle},
 * {@code bookTitle}, {@code projectTitle} — so a reviewer's view survives the
 * author trashing the chapter, the book, and the project. Nothing here reads
 * through to the live manuscript.
 *
 * <p>{@code sourceUpdatedAt} is the version marker (§8.2): the source chapter's
 * {@code updated_at} at capture time. Compared against the live chapter it tells
 * the author whether the published text still matches what they are writing.
 *
 * <p>{@code contentHtml} is the whole chapter and can be large, so list reads use
 * {@link ReviewRequestSummary} and never load it.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSnapshot {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID requestId;

    @JsonProperty
    private String sourceScope;

    /** Provenance only. Not serialized — a reviewer has no use for a manuscript UUID. */
    private UUID sourceEntityId;

    /** The chapter's title as it read at publication, or "Chapter N" if it had none. */
    @JsonProperty
    private String sourceTitle;

    @JsonProperty
    private String bookTitle;

    @JsonProperty
    private String projectTitle;

    /** Scenes concatenated, separated by a bare {@code <hr>}. Frozen. */
    @JsonProperty
    private String contentHtml;

    @JsonProperty
    private int wordCount;

    /**
     * The source chapter's updated_at at capture time. Not serialized: it is an
     * internal comparison input, and {@link ReviewRequestSummary#getSourceState()}
     * is the answer the frontend actually wants.
     */
    private Instant sourceUpdatedAt;

    @JsonProperty
    private Instant createdAt;
}
