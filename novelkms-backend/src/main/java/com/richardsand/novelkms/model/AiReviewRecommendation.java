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
 * (OPEN -> ACCEPTED | REJECTED | FUTURE | DELETED | PROMOTED) so the author
 * can track which suggestions were applied without the manuscript ever being
 * modified automatically.
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

    /** OPEN | ACCEPTED | REJECTED | FUTURE | DELETED | PROMOTED. */
    @JsonProperty
    private String status;

    /** Model-suggested codex category for one-click promotion (CHARACTER, CANON, …). */
    @JsonProperty
    private String codexCategory;

    /** Model-suggested concise title for the codex entry if promoted. */
    @JsonProperty
    private String codexTitle;

    /**
     * A short verbatim quote from the chapter text that anchors this
     * recommendation to a specific passage. Used by the frontend to
     * scroll-to-and-highlight when the user clicks a recommendation card.
     * Null for reviews created before prompt v2.
     */
    @JsonProperty
    private String anchorText;

    /** Set once promoted: the scene id of the created codex entry (null otherwise). */
    @JsonProperty
    private UUID promotedSceneId;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
