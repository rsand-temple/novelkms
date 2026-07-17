package com.richardsand.novelkms.model.admin;

import java.time.Instant;

/**
 * Admin request to extend (or start) a user's local trial entitlement.
 *
 * Exactly one of {@code trialEndsAt} or {@code extendDays} must be supplied:
 * <ul>
 *   <li>{@code trialEndsAt} — an absolute UTC instant to set {@code trial_end} to outright.</li>
 *   <li>{@code extendDays} — a positive number of days to extend by, anchored on
 *       {@code max(now, current trial_end)} so extending never shortens a live trial.</li>
 * </ul>
 *
 * Supplying neither or both is a client error (400). The service resolves the mode
 * into a single concrete {@code trial_end} before persisting.
 */
public record ExtendTrialRequest(
        Instant trialEndsAt,
        Integer extendDays,
        String reason,
        String note) {
}
