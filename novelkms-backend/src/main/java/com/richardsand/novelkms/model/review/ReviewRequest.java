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
 * The mutable half of a Review Package (spec §8): title, description, visibility,
 * status, dates. The manuscript text it publishes lives in an immutable
 * {@link ReviewSnapshot} and is never reachable from here.
 *
 * <p>That split is the whole point. Request metadata is naturally editable while a
 * request is open; the text under review must never change once a reviewer has
 * read it. Keeping them in one object would force a choice between those two, and
 * the wrong choice is invisible until a reviewer's feedback stops matching the
 * words they read.
 *
 * <p>{@code sourceEntityId} is provenance only — a bare UUID with no foreign key.
 * The request must survive its source chapter being trashed or deleted, so nothing
 * here may depend on the chapter still existing.
 *
 * <p>No {@code isX} boolean fields, per the Lombok/Jackson is-prefix collision
 * rule: {@code status} and {@code visibility} carry the states instead.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID authorUserId;

    /** CHAPTER in Phase 1. The column carries the full enum; the resource rejects the rest. */
    @JsonProperty
    private String sourceScope;

    /** Provenance only — deliberately not a foreign key. May point at a deleted chapter. */
    @JsonProperty
    private UUID sourceEntityId;

    /** The package title the author chose. Distinct from the snapshot's frozen source title. */
    @JsonProperty
    private String title;

    @JsonProperty
    private String description;

    /** What the author specifically wants the reviewer to look at. */
    @JsonProperty
    private String authorQuestions;

    @JsonProperty
    private String genre;

    /** Requested feedback types, e.g. developmental, line, continuity. */
    @JsonProperty
    private List<String> feedbackTypes;

    /**
     * Spec §8.2 lists content warnings under Context Item. They live on the request
     * instead, because a reviewer needs to see them <em>before</em> deciding to open
     * the package — and Context Items are inside it.
     */
    @JsonProperty
    private String contentWarnings;

    /** PUBLIC or INVITE. */
    @JsonProperty
    private String visibility;

    /** DRAFT, OPEN, PAUSED, CLOSED, WITHDRAWN, REMOVED. */
    @JsonProperty
    private String status;

    /** Optional cap on accepted reviews. Stored from Phase 1B; enforced from 1C. */
    @JsonProperty
    private Integer maxReviews;

    @JsonProperty
    private Instant publishedAt;

    /** Optional author-set closing date. Advisory in Phase 1 — nothing sweeps it. */
    @JsonProperty
    private Instant closesAt;

    /** When the request actually left the queue, whether closed or withdrawn. */
    @JsonProperty
    private Instant closedAt;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
