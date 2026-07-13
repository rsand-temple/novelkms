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
 * A user's public identity in the human-review network ({@code review_profile}).
 *
 * <p>This is deliberately distinct from {@link com.richardsand.novelkms.model.AppUser},
 * which carries authentication identity. An {@code AppUser} always exists; a
 * {@code ReviewProfile} exists only once the user opts in by claiming a handle,
 * and claiming a handle is the gate for <em>all</em> participation — authoring
 * review requests and writing reviews alike.
 *
 * <p>The email address and OAuth identity on {@code AppUser} must never leak
 * into this object: it is the only user-shaped thing other users can read.
 *
 * <p>{@code handle} preserves the casing the user typed; case-insensitive
 * uniqueness is driven by the {@code handle_lower} column, which is not exposed
 * here (the frontend never needs it). This mirrors the artifact-node name rule.
 *
 * <p>Genres are stored as a single comma-separated column and split by the DAO,
 * so the wire contract is a clean array while the schema stays flat — there is
 * no query that filters on an individual genre in Phase 1.
 *
 * <p>Contribution metrics (§13.2) are intentionally absent: they are derived at
 * read time from submitted reviews, never stored on the profile. They arrive as
 * a separate response object in slice 1E.
 *
 * <p>No {@code isX} boolean fields, per the Lombok/Jackson is-prefix collision
 * rule — {@code visibility} and {@code status} carry the states instead.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewProfile {

    @JsonProperty
    private UUID id;

    /**
     * The owning {@code app_user}. Serialized because the frontend needs to know
     * whether a profile it is looking at is its own; it discloses nothing that a
     * handle does not already disclose.
     */
    @JsonProperty
    private UUID userId;

    /** Unique, case-insensitively. Display casing preserved. */
    @JsonProperty
    private String handle;

    /** Optional friendly name. Falls back to the handle in the UI when absent. */
    @JsonProperty
    private String displayName;

    @JsonProperty
    private String bio;

    @JsonProperty
    private List<String> genresWritten;

    @JsonProperty
    private List<String> genresReviewed;

    /** PUBLIC or HIDDEN. HIDDEN keeps the profile out of other users' view. */
    @JsonProperty
    private String visibility;

    /** ACTIVE or SUSPENDED. SUSPENDED is a moderation state, not a user setting. */
    @JsonProperty
    private String status;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
