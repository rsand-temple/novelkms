package com.richardsand.novelkms.dao.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.review.ContentReport;

/**
 * Storage rules for {@code content_report} (slice 1F): a report is filed OPEN,
 * survives removal of its target (no FK), resolves once, and auto-resolves in bulk
 * for a target while leaving already-closed reports untouched.
 */
class ContentReportDaoTest extends NovelKmsTestBase {

    private static final ContentReportDao REPORTS = new ContentReportDao(ds);

    private static final UUID REPORTER = OTHER_USER_ID;
    private static final UUID ADMIN    = TEST_USER_ID;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    @Test
    void create_filesOpen_andIsReadableById() throws SQLException {
        UUID target = UUID.randomUUID();
        ContentReport r = REPORTS.create(REPORTER, ContentReportDao.TARGET_REQUEST, target,
                "SPAM", "looks like promo copy");

        assertNotNull(r.getId());
        assertEquals(ContentReportDao.STATUS_OPEN, r.getStatus());
        assertEquals("SPAM", r.getReason());
        assertEquals(target, r.getTargetId());
        assertNull(r.getResolvedAt());

        assertEquals(r.getId(), REPORTS.findById(r.getId()).orElseThrow().getId());
    }

    @Test
    void listByStatus_filtersToOpen_andAllWhenBlank() throws SQLException {
        ContentReport open = REPORTS.create(REPORTER, ContentReportDao.TARGET_REVIEW,
                UUID.randomUUID(), "HARASSMENT", null);
        ContentReport toClose = REPORTS.create(REPORTER, ContentReportDao.TARGET_PROFILE,
                UUID.randomUUID(), "HATE", null);
        REPORTS.resolve(toClose.getId(), ADMIN, ContentReportDao.STATUS_DISMISSED, "not warranted");

        assertEquals(1, REPORTS.listByStatus(ContentReportDao.STATUS_OPEN, 50).size());
        assertEquals(open.getId(),
                REPORTS.listByStatus(ContentReportDao.STATUS_OPEN, 50).get(0).getId());
        assertEquals(2, REPORTS.listByStatus(null, 50).size(), "blank status returns all");
    }

    @Test
    void resolve_stampsWhoWhenAndStatus() throws SQLException {
        ContentReport r = REPORTS.create(REPORTER, ContentReportDao.TARGET_REQUEST,
                UUID.randomUUID(), "OTHER", null);

        ContentReport resolved = REPORTS.resolve(r.getId(), ADMIN,
                ContentReportDao.STATUS_RESOLVED, "handled").orElseThrow();

        assertEquals(ContentReportDao.STATUS_RESOLVED, resolved.getStatus());
        assertEquals(ADMIN, resolved.getResolvedByUserId());
        assertEquals("handled", resolved.getResolutionNote());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    void resolveOpenForTarget_closesOpenOnly_andCountsThem() throws SQLException {
        UUID target = UUID.randomUUID();
        REPORTS.create(REPORTER, ContentReportDao.TARGET_REQUEST, target, "SPAM", null);
        REPORTS.create(ADMIN,    ContentReportDao.TARGET_REQUEST, target, "OTHER", null);
        // An unrelated report against a different target must be left alone.
        ContentReport other = REPORTS.create(REPORTER, ContentReportDao.TARGET_REQUEST,
                UUID.randomUUID(), "SPAM", null);

        int closed = REPORTS.resolveOpenForTarget(ContentReportDao.TARGET_REQUEST, target,
                ADMIN, "removed the request");

        assertEquals(2, closed);
        assertEquals(ContentReportDao.STATUS_OPEN,
                REPORTS.findById(other.getId()).orElseThrow().getStatus(),
                "a report about a different target is untouched");

        assertEquals(0, REPORTS.resolveOpenForTarget(ContentReportDao.TARGET_REQUEST, target,
                ADMIN, "again"), "already-closed reports do not re-resolve");
    }
}
