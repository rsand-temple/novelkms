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
 * <p>A chapter review and a scene review are the same kind of artifact; they
 * differ only in {@code scope}. {@code scope} is derived (never stored):
 * {@code chapterId == null} &rarr; {@code BOOK} (reserved for a future
 * book-scope review), else {@code sceneId != null} &rarr; {@code SCENE}, else
 * {@code CHAPTER}. A scene review records its parent chapter in
 * {@code chapterId} so it groups under that chapter's AI workflow, and the
 * reviewed scene in {@code sceneId}.
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

    /** Set only for a scene-scope review; null for chapter (and future book) scope. */
    @JsonProperty
    private UUID sceneId;

    /** Derived origin of the review: {@code CHAPTER}, {@code SCENE}, or {@code BOOK}. */
    @JsonProperty
    private String scope;

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

    /**
     * Provenance: which scope supplied the editorial "form" block that governed
     * this review — {@code BOOK}, {@code PROJECT}, {@code USER}, or {@code SYSTEM}.
     */
    @JsonProperty
    private String formScope;

    /**
     * Provenance: the exact form block used, captured at run time so this
     * immutable artifact stays faithful even if the user later edits their
     * global/override. The constant functional contract is not stored.
     */
    @JsonProperty
    private String formInstructions;

    /** Populated only when status is FAILED. */
    @JsonProperty
    private String errorMessage;

    /** Populated on detail fetch; empty on list fetch. */
    @JsonProperty
    private List<AiReviewRecommendation> recommendations;

    public void setRecommendations(List<AiReviewRecommendation> recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Derives the review scope from its target columns. Centralized here so the
     * DAO mapper and any future caller agree on the rule.
     */
    public static String deriveScope(UUID chapterId, UUID sceneId) {
        if (chapterId == null) return "BOOK";
        return sceneId != null ? "SCENE" : "CHAPTER";
    }
}
