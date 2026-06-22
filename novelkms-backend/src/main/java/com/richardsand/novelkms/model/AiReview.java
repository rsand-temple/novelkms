package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An AI review execution, treated as a first-class immutable project artifact.
 * Re-running a review creates a new {@code AiReview}; existing rows are never
 * rewritten. The raw provider response is persisted in the database for audit
 * but is intentionally not exposed on this model.
 *
 * <p>{@code status} progresses PENDING -&gt; COMPLETED | FAILED. v1 runs the
 * review synchronously, but {@code submittedAt}/{@code completedAt} are retained
 * so a future async/polling execution model needs no schema change.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReview {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID userId;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private UUID chapterId;

    @JsonProperty
    private String provider;

    @JsonProperty
    private String model;

    /** PENDING | COMPLETED | FAILED. */
    @JsonProperty
    private String status;

    @JsonProperty
    private Instant submittedAt;

    @JsonProperty
    private Instant completedAt;

    @JsonProperty
    private String promptVersion;

    /** Populated only when status is FAILED. */
    @JsonProperty
    private String errorMessage;

    /** Populated on detail fetch; empty on list fetch. */
    @JsonProperty
    private List<AiReviewRecommendation> recommendations;

    public void setRecommendations(List<AiReviewRecommendation> recommendations) {
        this.recommendations = recommendations;
    }
}
