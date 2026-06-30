package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.auth.Roles;
import com.richardsand.novelkms.model.admin.AdminUserDetail;
import com.richardsand.novelkms.model.admin.AdminUserSummary;

class AdminUserDaoTest {

    private JdbcDataSource dataSource;
    private AdminUserDao   dao;

    private UUID adminUserId;
    private UUID normalUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin_user_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");

        createSchema();

        dao = new AdminUserDao(dataSource);

        adminUserId = UUID.randomUUID();
        normalUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        insertUser(adminUserId, "admin@example.com", "admin@example.com", "Admin User", "ACTIVE");
        insertUser(normalUserId, "writer@example.com", "writer@example.com", "Writer User", "ACTIVE");
        insertUser(otherUserId, "other@example.com", "other@example.com", "Other User", "ACTIVE");

        insertRole(adminUserId, Roles.ADMIN);

        insertSubscription(
                normalUserId,
                "trialing",
                "trial",
                "cus_writer",
                "sub_writer",
                "price_trial",
                "prod_trial");

        UUID projectId = UUID.randomUUID();
        insertProject(projectId, normalUserId, "Writer Project", false);

        UUID deletedProjectId = UUID.randomUUID();
        insertProject(deletedProjectId, normalUserId, "Deleted Project", true);

        UUID bookId = UUID.randomUUID();
        insertBook(bookId, projectId, "Writer Book", false);

        UUID deletedBookId = UUID.randomUUID();
        insertBook(deletedBookId, projectId, "Deleted Book", true);

        UUID partId = UUID.randomUUID();
        insertPart(partId, bookId, "Part One");

        UUID chapterId = UUID.randomUUID();
        insertChapter(chapterId, bookId, null, "Chapter One", false);

        UUID deletedChapterId = UUID.randomUUID();
        insertChapter(deletedChapterId, bookId, null, "Deleted Chapter", true);

        UUID sceneId = UUID.randomUUID();
        insertScene(sceneId, chapterId, "Scene One", false);

        UUID deletedSceneId = UUID.randomUUID();
        insertScene(deletedSceneId, chapterId, "Deleted Scene", true);

        UUID codexId = UUID.randomUUID();
        insertCodex(codexId, projectId, null, "Canon Entry", false);
        
        UUID codexChapterId = UUID.randomUUID();
        insertChapter(codexChapterId, null, codexId, "Codex Chapter", false);

        UUID aiReviewId = UUID.randomUUID();
        insertAiReview(aiReviewId, chapterId, false);

        UUID deletedAiReviewId = UUID.randomUUID();
        insertAiReview(deletedAiReviewId, chapterId, true);
    }

    @Test
    void searchWithBlankQueryReturnsRecentUsers() throws Exception {
        List<AdminUserSummary> rows = dao.search(null, 10);

        assertEquals(3, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.id().equals(adminUserId)));
        assertTrue(rows.stream().anyMatch(row -> row.id().equals(normalUserId)));
        assertTrue(rows.stream().anyMatch(row -> row.id().equals(otherUserId)));
    }

    @Test
    void searchByEmailFindsUser() throws Exception {
        List<AdminUserSummary> rows = dao.search("writer@example.com", 10);

        assertEquals(1, rows.size());
        assertEquals(normalUserId, rows.get(0).id());
        assertEquals("writer@example.com", rows.get(0).emailAddress());
        assertEquals("Writer User", rows.get(0).displayName());
    }

    @Test
    void searchByDisplayNameFindsUser() throws Exception {
        List<AdminUserSummary> rows = dao.search("writer user", 10);

        assertEquals(1, rows.size());
        assertEquals(normalUserId, rows.get(0).id());
    }

    @Test
    void searchByUserIdFindsUser() throws Exception {
        List<AdminUserSummary> rows = dao.search(normalUserId.toString(), 10);

        assertEquals(1, rows.size());
        assertEquals(normalUserId, rows.get(0).id());
    }

    @Test
    void searchByStripeCustomerIdFindsUser() throws Exception {
        List<AdminUserSummary> rows = dao.search("cus_writer", 10);

        assertEquals(1, rows.size());
        assertEquals(normalUserId, rows.get(0).id());
        assertEquals("cus_writer", rows.get(0).subscription().stripeCustomerId());
    }

    @Test
    void searchByStripeSubscriptionIdFindsUser() throws Exception {
        List<AdminUserSummary> rows = dao.search("sub_writer", 10);

        assertEquals(1, rows.size());
        assertEquals(normalUserId, rows.get(0).id());
        assertEquals("sub_writer", rows.get(0).subscription().stripeSubscriptionId());
    }

    @Test
    void searchHonorsLimit() throws Exception {
        List<AdminUserSummary> rows = dao.search(null, 2);

        assertEquals(2, rows.size());
    }

    @Test
    void searchUsesDefaultLimitForInvalidLimit() throws Exception {
        List<AdminUserSummary> rows = dao.search(null, 0);

        assertEquals(3, rows.size());
    }

    @Test
    void findByIdReturnsEmptyForUnknownUser() throws Exception {
        Optional<AdminUserDetail> found = dao.findById(UUID.randomUUID());

        assertTrue(found.isEmpty());
    }

    @Test
    void findByIdReturnsRoles() throws Exception {
        AdminUserDetail detail = dao.findById(adminUserId).orElseThrow();

        assertEquals(adminUserId, detail.id());
        assertEquals("admin@example.com", detail.emailAddress());
        assertTrue(detail.roles().contains(Roles.ADMIN));
    }

    @Test
    void findByIdReturnsSubscriptionSummary() throws Exception {
        AdminUserDetail detail = dao.findById(normalUserId).orElseThrow();

        assertEquals("trialing", detail.subscription().status());
        assertEquals("trial", detail.subscription().planKey());
        assertEquals("cus_writer", detail.subscription().stripeCustomerId());
        assertEquals("sub_writer", detail.subscription().stripeSubscriptionId());
        assertEquals("price_trial", detail.subscription().stripePriceId());
        assertEquals("prod_trial", detail.subscription().stripeProductId());
    }

    @Test
    void findByIdReturnsNullSubscriptionWhenMissing() throws Exception {
        AdminUserDetail detail = dao.findById(otherUserId).orElseThrow();

        assertEquals(otherUserId, detail.id());
        assertEquals(null, detail.subscription());
    }

    @Test
    void findByIdReturnsUsageCountsForLiveObjectsOnly() throws Exception {
        AdminUserDetail detail = dao.findById(normalUserId).orElseThrow();

        assertEquals(1, detail.usage().projectCount());
        assertEquals(1, detail.usage().bookCount());
        assertEquals(1, detail.usage().partCount());
        assertEquals(2, detail.usage().chapterCount());
        assertEquals(1, detail.usage().sceneCount());
        assertEquals(1, detail.usage().codexEntryCount());
        assertEquals(1, detail.usage().aiReviewCount());
    }

    private void createSchema() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("""
                    CREATE TABLE app_user (
                        id UUID PRIMARY KEY,
                        email_address VARCHAR(320) NOT NULL,
                        normalized_email VARCHAR(320) NOT NULL UNIQUE,
                        email_verified BOOLEAN NOT NULL,
                        first_name VARCHAR(200),
                        last_name VARCHAR(200),
                        display_name VARCHAR(200) NOT NULL,
                        mobile_number VARCHAR(50),
                        status VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        last_login_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE user_role (
                        user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                        role VARCHAR(64) NOT NULL,
                        granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        granted_by UUID NULL REFERENCES app_user(id) ON DELETE SET NULL,

                        CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
                        CONSTRAINT ck_user_role_not_blank CHECK (length(trim(role)) > 0)
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE user_subscription (
                        user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
                        stripe_customer_id VARCHAR(255),
                        stripe_subscription_id VARCHAR(255),
                        status VARCHAR(64) NOT NULL,
                        plan_key VARCHAR(64),
                        stripe_price_id VARCHAR(255),
                        stripe_product_id VARCHAR(255),
                        current_period_start TIMESTAMP,
                        current_period_end TIMESTAMP,
                        trial_start TIMESTAMP,
                        trial_end TIMESTAMP,
                        cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
                        cancel_at TIMESTAMP,
                        cancellation_feedback VARCHAR(255),
                        cancellation_comment CLOB,
                        cancellation_reason VARCHAR(255),
                        canceled_at TIMESTAMP,
                        last_payment_succeeded_at TIMESTAMP,
                        last_payment_failed_at TIMESTAMP,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE project (
                        id UUID PRIMARY KEY,
                        owner_user_id UUID NOT NULL REFERENCES app_user(id),
                        name VARCHAR(255) NOT NULL,
                        deleted_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE book (
                        id UUID PRIMARY KEY,
                        project_id UUID NOT NULL REFERENCES project(id),
                        title VARCHAR(255) NOT NULL,
                        deleted_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE part (
                        id UUID PRIMARY KEY,
                        book_id UUID NOT NULL REFERENCES book(id),
                        title VARCHAR(255)
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE codex (
                        id UUID PRIMARY KEY,
                        project_id UUID NULL REFERENCES project(id),
                        book_id UUID NULL REFERENCES book(id),
                        title VARCHAR(255),
                        deleted_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE chapter (
                        id UUID PRIMARY KEY,
                        book_id UUID NULL REFERENCES book(id),
                        codex_id UUID NULL REFERENCES codex(id),
                        title VARCHAR(255),
                        deleted_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE scene (
                        id UUID PRIMARY KEY,
                        chapter_id UUID NOT NULL REFERENCES chapter(id),
                        title VARCHAR(255),
                        deleted_at TIMESTAMP
                    )
                    """);

            c.createStatement().execute("""
                    CREATE TABLE ai_review (
                        id UUID PRIMARY KEY,
                        chapter_id UUID NOT NULL REFERENCES chapter(id),
                        deleted_at TIMESTAMP
                    )
                    """);
        }
    }

    private void insertUser(UUID userId, String email, String normalizedEmail, String displayName, String status) throws SQLException {
        String sql = """
                INSERT INTO app_user
                    (id, email_address, normalized_email, email_verified,
                     first_name, last_name, display_name, mobile_number,
                     status, created_at, updated_at, last_login_at)
                VALUES (?, ?, ?, TRUE, NULL, NULL, ?, NULL, ?, ?, ?, ?)
                """;

        Instant now = Instant.now();

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, normalizedEmail);
            ps.setString(4, displayName);
            ps.setString(5, status);
            ps.setTimestamp(6, java.sql.Timestamp.from(now));
            ps.setTimestamp(7, java.sql.Timestamp.from(now));
            ps.setTimestamp(8, java.sql.Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertRole(UUID userId, String role) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO user_role (user_id, role) VALUES (?, ?)")) {
            ps.setObject(1, userId);
            ps.setString(2, role);
            ps.executeUpdate();
        }
    }

    private void insertSubscription(
            UUID userId,
            String status,
            String planKey,
            String stripeCustomerId,
            String stripeSubscriptionId,
            String stripePriceId,
            String stripeProductId) throws SQLException {

        Instant now      = Instant.now();
        Instant trialEnd = now.plusSeconds(14L * 24L * 60L * 60L);

        String sql = """
                INSERT INTO user_subscription
                    (user_id, stripe_customer_id, stripe_subscription_id, status, plan_key,
                     stripe_price_id, stripe_product_id,
                     current_period_start, current_period_end, trial_start, trial_end,
                     cancel_at_period_end,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)
                """;

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, stripeCustomerId);
            ps.setString(3, stripeSubscriptionId);
            ps.setString(4, status);
            ps.setString(5, planKey);
            ps.setString(6, stripePriceId);
            ps.setString(7, stripeProductId);
            ps.setTimestamp(8, java.sql.Timestamp.from(now));
            ps.setTimestamp(9, java.sql.Timestamp.from(trialEnd));
            ps.setTimestamp(10, java.sql.Timestamp.from(now));
            ps.setTimestamp(11, java.sql.Timestamp.from(trialEnd));
            ps.setTimestamp(12, java.sql.Timestamp.from(now));
            ps.setTimestamp(13, java.sql.Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    private void insertProject(UUID projectId, UUID userId, String name, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO project (id, owner_user_id, name, deleted_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
            ps.setObject(1, projectId);
            ps.setObject(2, userId);
            ps.setString(3, name);
            ps.setTimestamp(4, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }

    private void insertBook(UUID bookId, UUID projectId, String title, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO book (id, project_id, title, deleted_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
            ps.setObject(1, bookId);
            ps.setObject(2, projectId);
            ps.setString(3, title);
            ps.setTimestamp(4, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }

    private void insertPart(UUID partId, UUID bookId, String title) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO part (id, book_id, title)
                        VALUES (?, ?, ?)
                        """)) {
            ps.setObject(1, partId);
            ps.setObject(2, bookId);
            ps.setString(3, title);
            ps.executeUpdate();
        }
    }

    private void insertCodex(UUID codexId, UUID projectId, UUID bookId, String title, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO codex (id, project_id, book_id, title, deleted_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
            ps.setObject(1, codexId);
            ps.setObject(2, projectId);
            ps.setObject(3, bookId);
            ps.setString(4, title);
            ps.setTimestamp(5, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }

    private void insertChapter(UUID chapterId, UUID bookId, UUID codexId, String title, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO chapter (id, book_id, codex_id, title, deleted_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
            ps.setObject(1, chapterId);
            ps.setObject(2, bookId);
            ps.setObject(3, codexId);
            ps.setString(4, title);
            ps.setTimestamp(5, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }

    private void insertScene(UUID sceneId, UUID chapterId, String title, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO scene (id, chapter_id, title, deleted_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
            ps.setObject(1, sceneId);
            ps.setObject(2, chapterId);
            ps.setString(3, title);
            ps.setTimestamp(4, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }

    private void insertAiReview(UUID aiReviewId, UUID chapterId, boolean deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO ai_review (id, chapter_id, deleted_at)
                        VALUES (?, ?, ?)
                        """)) {
            ps.setObject(1, aiReviewId);
            ps.setObject(2, chapterId);
            ps.setTimestamp(3, deleted ? java.sql.Timestamp.from(Instant.now()) : null);
            ps.executeUpdate();
        }
    }
}