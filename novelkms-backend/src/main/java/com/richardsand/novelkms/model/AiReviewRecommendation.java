package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single editorial recommendation produced by an AI review. Recommendations
 * are atomic and independently actionable; each carries a lifecycle status
 * (OPEN -> ACCEPTED | REJECTED) so the author can track which suggestions were
 * applied without the manuscript ever being modified automatically.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewRecommendation {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID reviewId;

    /** 1-based position within the review, as numbered at creation time. */
    @JsonProperty
    private int seq;

    /** e.g. Continuity, Characterization, Pacing, Dialogue, Clarity, Grammar... */
    @JsonProperty
    private String category;

    /** LOW | MEDIUM | HIGH (advisory; model-supplied). */
    @JsonProperty
    private String severity;

    /** Where in the chapter the note applies (model-supplied, free text). */
    @JsonProperty
    private String location;

    @JsonProperty
    private String recommendation;

    /** OPEN | ACCEPTED | REJECTED. */
    @JsonProperty
    private String status;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
