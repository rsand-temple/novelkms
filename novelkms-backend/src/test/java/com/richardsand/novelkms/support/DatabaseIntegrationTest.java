package com.richardsand.novelkms.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.richardsand.novelkms.auth.OAuthProfile;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.model.AppUser;

public abstract class DatabaseIntegrationTest {
    protected BasicDataSource dataSource;

    @BeforeEach
    void createDatabase() {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:novelkms_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/h2")
                .load()
                .migrate();
    }

    @AfterEach
    void closeDatabase() throws Exception {
        dataSource.close();
    }

    protected AppUser createUser(String subject, String email, String displayName) throws SQLException {
        AuthDao authDao = new AuthDao(dataSource);
        String tokenHash = "pending-" + UUID.randomUUID();
        OAuthProfile profile = new OAuthProfile("GOOGLE", subject, email, true, null, null);
        authDao.createPendingRegistration(tokenHash, profile, Instant.now().plusSeconds(300));
        AuthDao.PendingRegistration pending = authDao.findPendingRegistration(tokenHash).orElseThrow();
        return authDao.register(pending, null, null, displayName, null);
    }

    protected Hierarchy createHierarchy(UUID ownerId, String suffix) throws SQLException {
        UUID projectId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                insert(c, "INSERT INTO project(id,title,description,created_at,updated_at,owner_user_id) VALUES (?,?,?,?,?,?)",
                        projectId, "Project " + suffix, null, now, now, ownerId);
                insert(c, "INSERT INTO book(id,project_id,title,display_order,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                        bookId, projectId, "Book " + suffix, 0, now, now);
                insert(c, "INSERT INTO part(id,book_id,title,display_order,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                        partId, bookId, "Part " + suffix, 0, now, now);
                insert(c, "INSERT INTO chapter(id,book_id,part_id,title,display_order,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                        chapterId, bookId, partId, "Chapter " + suffix, 0, now, now);
                insert(c, "INSERT INTO scene(id,chapter_id,title,display_order,content,word_count,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                        sceneId, chapterId, "Scene " + suffix, 0, "<p>words</p>", 1, now, now);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return new Hierarchy(projectId, bookId, partId, chapterId, sceneId);
    }

    private static void insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Instant instant) ps.setTimestamp(i + 1, Timestamp.from(instant));
                else ps.setObject(i + 1, value);
            }
            ps.executeUpdate();
        }
    }

    protected record Hierarchy(UUID projectId, UUID bookId, UUID partId, UUID chapterId, UUID sceneId) {}
}
