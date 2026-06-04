package com.richardsand.novelkms;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;

/**
 * Abstract base for all NovelKMS tests.
 *
 * DAOs are static so that subclasses can reference them in static
 * {@code @RegisterExtension ResourceExtension} fields, which are initialized
 * before JUnit's {@code @BeforeAll} lifecycle — class loading order guarantees
 * the static initializer here fires first.
 *
 * Every test class should call {@link #truncateAll()} in a {@code @BeforeEach}
 * method to keep tests independent.
 */
public abstract class NovelKmsTestBase {

    // -------------------------------------------------------------------------
    // Shared infrastructure — initialized once for the entire test run
    // -------------------------------------------------------------------------

    protected static final BasicDataSource ds;
    protected static final ProjectDao      projectDao;
    protected static final BookDao         bookDao;
    protected static final ChapterDao      chapterDao;
    protected static final SceneDao        sceneDao;

    static {
        try {
            ds = new BasicDataSource();
            ds.setDriverClassName("org.h2.Driver");
            // Named in-memory DB with DB_CLOSE_DELAY=-1 so it survives across
            // connection pool borrowing/returning
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

            projectDao = new ProjectDao(ds);
            bookDao = new BookDao(ds);
            chapterDao = new ChapterDao(ds);
            sceneDao = new SceneDao(ds);

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Deletes all rows in reverse FK-dependency order.
     * Call this in {@code @BeforeEach} to isolate each test.
     */
    protected static void truncateAll() throws SQLException {
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            s.execute("DELETE FROM scene");
            s.execute("DELETE FROM chapter");
            s.execute("DELETE FROM part");
            s.execute("DELETE FROM book");
            s.execute("DELETE FROM project");
        }
    }

    /**
     * Returns an ObjectMapper configured to match the production server:
     * ISO-8601 Instant strings, Java time module registered.
     * Pass this to {@code ResourceExtension.builder().setMapper()} so both
     * the server-side and client-side Jackson providers handle {@code Instant}.
     */
    protected static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
