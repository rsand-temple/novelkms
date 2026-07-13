package com.richardsand.novelkms;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.auth.AuthConstants;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.TemplateDao;
import com.richardsand.novelkms.dao.TrashDao;
import com.richardsand.novelkms.dao.artifact.ArtifactNodeDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.user.UserStyleDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.service.ArtifactStorage;
import com.richardsand.novelkms.service.TrashService;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

/**
 * Abstract base for all NovelKMS tests.
 *
 * <p>
 * Resource tests use {@link #testAuthenticationFilter()} to install a
 * deterministic authenticated principal. Authentication/session behavior itself
 * should continue to be tested separately with the production
 * AuthenticationFilter and SessionService.
 *
 * <p>
 * DAOs are static so subclasses can reference them in static
 * ResourceExtension fields, which are initialized before JUnit lifecycle
 * methods.
 */
public abstract class NovelKmsTestBase {

    /**
     * Stable test identity used by ordinary resource tests.
     *
     * <p>
     * The row is deleted and recreated by {@link #truncateAll()}, but the UUID
     * remains stable so statically-created request filters can reference it.
     */
    protected static final UUID TEST_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    protected static final UUID OTHER_USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    /**
     * A third stable identity. Review-network handles are globally unique, so
     * collision and disclosure tests need a bystander who is neither the profile
     * owner nor the single "other tenant" the older isolation tests share.
     */
    protected static final UUID THIRD_USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    protected static final BasicDataSource ds;
    protected static final ProjectDao      projectDao;
    protected static final BookDao         bookDao;
    protected static final ChapterDao      chapterDao;
    protected static final SceneDao        sceneDao;
    protected static final TemplateDao     templateDao;
    protected static final UserStyleDao    styleDao;
    protected static final TrashDao        trashDao;
    protected static final TrashService    trashService;

    private static ArtifactStorage artifactStorage;

    private static ArtifactNodeDao artifactNodeDao;

    static {
        try {
            ds = new BasicDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:novelkms_test;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setMinIdle(1);
            ds.setMaxIdle(5);
            ds.setMaxTotal(10);
            ds.setDefaultAutoCommit(true);

            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration/h2")
                    .load()
                    .migrate();

            artifactNodeDao = new ArtifactNodeDao(ds);
            artifactStorage = new ArtifactStorage("");
            bookDao = new BookDao(ds);
            chapterDao = new ChapterDao(ds);
            projectDao = new ProjectDao(ds);
            sceneDao = new SceneDao(ds);
            styleDao = new UserStyleDao(ds);
            templateDao = new TemplateDao(ds);
            trashDao = new TrashDao(ds);
            trashService = new TrashService(trashDao, projectDao, bookDao, chapterDao, sceneDao, artifactNodeDao, artifactStorage);

            // Ensure static ResourceExtensions have a valid principal even before
            // the first subclass @BeforeEach executes.
            resetTestUsers();

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Register this provider in each ResourceExtension:
     *
     * <pre>
     * static final ResourceExtension RESOURCES = ResourceExtension.builder()
     *         .addProvider(testAuthenticationFilter())
     *         .addResource(new ProjectResource(projectDao))
     *         .setMapper(createMapper())
     *         .build();
     * </pre>
     *
     * <p>
     * This deliberately bypasses cookies and SessionService for ordinary
     * resource-unit tests. Dedicated authentication tests should register the
     * production AuthenticationFilter instead.
     */
    protected static ContainerRequestFilter testAuthenticationFilter() {
        return new TestAuthenticationFilter(TEST_USER_ID);
    }

    /** Creates a filter representing another authenticated tenant. */
    protected static ContainerRequestFilter testAuthenticationFilter(UUID userId) {
        return new TestAuthenticationFilter(userId);
    }

    /**
     * Creates a project owned by the default authenticated test user.
     *
     * <p>
     * Use this instead of the legacy {@code projectDao.create(...)} in
     * resource tests. The legacy method creates an ownerless row and is therefore
     * intentionally invisible to Stage 2 user-scoped queries.
     */
    protected static Project createTestProject(String title, String description)
            throws SQLException {
        return projectDao.createForUser(TEST_USER_ID, title, description);
    }

    /** Creates a project owned by a specified test tenant. */
    protected static Project createTestProject(UUID ownerUserId,
            String title,
            String description)
            throws SQLException {
        return projectDao.createForUser(ownerUserId, title, description);
    }

    /**
     * Deletes all rows in reverse FK order and recreates deterministic test
     * users. Call from each resource test's @BeforeEach.
     */
    protected static void truncateAll() throws SQLException {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DELETE FROM template");
            s.execute("DELETE FROM style");
            s.execute("DELETE FROM scene");
            s.execute("DELETE FROM chapter");
            s.execute("DELETE FROM part");
            s.execute("DELETE FROM book");
            s.execute("DELETE FROM project");

            s.execute("DELETE FROM content_report");
            s.execute("DELETE FROM user_block");
            s.execute("DELETE FROM human_review");
            s.execute("DELETE FROM review_assignment");
            s.execute("DELETE FROM review_context_item");
            s.execute("DELETE FROM review_snapshot");
            s.execute("DELETE FROM review_request");
            s.execute("DELETE FROM review_profile");

            s.execute("DELETE FROM user_session");
            s.execute("DELETE FROM user_identity");
            s.execute("DELETE FROM pending_registration");
            s.execute("DELETE FROM oauth_state");
            s.execute("DELETE FROM stripe_webhook_event");
            s.execute("DELETE FROM admin_audit_log");
            s.execute("DELETE FROM user_subscription");
            s.execute("DELETE FROM user_role");
            s.execute("DELETE FROM app_user");
        }
        resetTestUsers();
    }

    /**
     * Recreates two stable users. The second user is useful for tenant-isolation
     * tests without each test having to understand the app_user schema.
     */
    protected static void resetTestUsers() throws SQLException {
        insertTestUser(
                TEST_USER_ID,
                "test.user@example.com",
                "Test User");

        insertTestUser(
                OTHER_USER_ID,
                "other.user@example.com",
                "Other User");

        insertTestUser(
                THIRD_USER_ID,
                "third.user@example.com",
                "Third User");
    }

    private static void insertTestUser(UUID id, String email, String displayName)
            throws SQLException {
        String sql = """
                INSERT INTO app_user (
                    id,
                    email_address,
                    normalized_email,
                    email_verified,
                    display_name,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, TRUE, ?, 'ACTIVE', ?, ?)
                """;

        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, email);
            ps.setString(3, email.toLowerCase());
            ps.setString(4, displayName);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
    }

    protected static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Minimal authentication provider for resource-unit tests.
     *
     * <p>
     * Priority matches production authentication so CurrentUser can resolve
     * the principal before the resource method or TenantAuthorizationFilter runs.
     */
    @Priority(Priorities.AUTHENTICATION)
    private static final class TestAuthenticationFilter
            implements ContainerRequestFilter {

        private final UUID userId;

        private TestAuthenticationFilter(UUID userId) {
            this.userId = userId;
        }

        @Override
        public void filter(ContainerRequestContext request) throws IOException {
            request.setProperty(AuthConstants.REQUEST_USER_ID, userId);
        }
    }
}
