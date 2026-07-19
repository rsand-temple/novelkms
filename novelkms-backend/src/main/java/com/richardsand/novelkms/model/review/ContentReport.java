package com.richardsand.novelkms.model.review;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A moderation report against a request, review, or profile ({@code content_report}).
 *
 * <p>This is the raw persistence model — it carries the reporter's and resolver's
 * {@code app_user} ids and is never serialized to a normal user. It is read only
 * by the admin moderation service, which maps it to a {@link com.richardsand.novelkms.model.admin.ContentReportView}
 * (reporter handle instead of id) for the admin console wire.
 *
 * <p>{@code targetId} is a bare UUID with no foreign key, mirroring the schema: a
 * report must outlive removal of the thing it reported so a dispute can still be
 * adjudicated. {@code targetType} says which table the id points at
 * ({@code REQUEST}/{@code REVIEW}/{@code PROFILE}).
 *
 * <p>No {@code isX} boolean fields, per the Lombok/Jackson is-prefix collision rule —
 * {@code status} carries the state instead.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentReport {

    private UUID id;

    /** The reporting user ({@code app_user}). Internal — never sent to a normal user. */
    private UUID reporterUserId;

    /** REQUEST, REVIEW, or PROFILE. */
    private String targetType;

    /** The reported request id, review id, or profile id — a bare UUID, no FK. */
    private UUID targetId;

    /** A fixed reason key (SPAM, HARASSMENT, COPYRIGHT, HATE, EXPLICIT, OTHER). */
    private String reason;

    /** Optional free-text elaboration from the reporter. */
    private String detail;

    /** OPEN, RESOLVED, or DISMISSED. */
    private String status;

    /** The admin who resolved/dismissed it, if any. Internal. */
    private UUID resolvedByUserId;

    /** Optional admin note recorded at resolution. */
    private String resolutionNote;

    private Instant createdAt;

    private Instant resolvedAt;
}
