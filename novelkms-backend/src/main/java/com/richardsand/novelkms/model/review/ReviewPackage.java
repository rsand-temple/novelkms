package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A reviewer's view of one Review Package before reading it (spec §24): the
 * request's public metadata, the author's public identity, and the snapshot's
 * metadata — but not the frozen text itself.
 *
 * <p>The content is fetched separately (a whole chapter is large, and the package
 * view renders instantly from metadata while the reader loads on demand). So there
 * is deliberately no {@code contentHtml} field here; that lives on
 * {@link ReviewSnapshot}, behind its own authorized endpoint.
 *
 * <p>Like {@link ReviewQueueEntry}, this exposes the author's handle and never
 * their {@code authorUserId} or the manuscript's {@code sourceEntityId}. Snapshot
 * metadata is flattened onto the package rather than nested, matching
 * {@link ReviewRequestSummary}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPackage {

    /** The review request id. */
    @JsonProperty
    private UUID id;

    @JsonProperty
    private String title;

    @JsonProperty
    private String description;

    /** What the author specifically wants feedback on (§17). */
    @JsonProperty
    private String authorQuestions;

    @JsonProperty
    private String genre;

    @JsonProperty
    private List<String> feedbackTypes;

    @JsonProperty
    private String contentWarnings;

    @JsonProperty
    private String sourceScope;

    @JsonProperty
    private Instant publishedAt;

    /** Advisory closing date, if the author set one. */
    @JsonProperty
    private Instant closesAt;

    // ---- Author public identity --------------------------------------------

    @JsonProperty
    private String authorHandle;

    @JsonProperty
    private String authorDisplayName;

    // ---- Frozen snapshot metadata (no content) ------------------------------

    @JsonProperty
    private String sourceTitle;

    @JsonProperty
    private String bookTitle;

    @JsonProperty
    private String projectTitle;

    @JsonProperty
    private int wordCount;

    @JsonProperty
    private Instant snapshotCreatedAt;

    // ---- Derived ------------------------------------------------------------

    /** Submitted reviews already received. Zero until slice 1D. */
    @JsonProperty
    private int reviewCount;

    /** Optional author cap on accepted reviews. */
    @JsonProperty
    private Integer maxReviews;
}
