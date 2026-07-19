package com.richardsand.novelkms.model.admin;

/**
 * The body for every admin moderation mutation (slice 1F): removing a request or
 * review, suspending or reinstating a profile, resolving or dismissing a report.
 *
 * <p>{@code reason} is the human-readable justification recorded in the audit trail;
 * {@code note} is an optional longer elaboration. Both are nullable so an
 * absent-body call still works (the resource substitutes an empty request), the same
 * shape {@code GrantFamilyAccessRequest} uses.
 */
public record ModerationActionRequest(
        String reason,
        String note) {
}
