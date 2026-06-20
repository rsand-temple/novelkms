package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.Codex;

/**
 * CRUD for the codex container. A codex belongs to exactly one project OR one
 * book; the database enforces "at most one per scope" via unique indexes on
 * project_id and book_id. Seeding of the default category chapters is
 * orchestrated by CodexResource (which co-injects ChapterDao + CodexCategoryDao),
 * mirroring how PartResource creates chapters.
 */
public class CodexDao {

    private final BasicDataSource ds;

    public CodexDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private Codex map(ResultSet rs) throws SQLException {
        return Codex.builder()
                .id(rs.getObject("id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .title(rs.getString("title"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    private static final String SELECT_COLUMNS =
            "SELECT id, project_id, book_id, title, created_at, updated_at FROM codex ";

    public Optional<Codex> findById(UUID id) throws SQLException {
        return findOne(SELECT_COLUMNS + "WHERE id = ?", id);
    }

    public Optional<Codex> findByProjectId(UUID projectId) throws SQLException {
        return findOne(SELECT_COLUMNS + "WHERE project_id = ?", projectId);
    }

    public Optional<Codex> findByBookId(UUID bookId) throws SQLException {
        return findOne(SELECT_COLUMNS + "WHERE book_id = ?", bookId);
    }

    private Optional<Codex> findOne(String sql, UUID key) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Codex createForProject(UUID projectId, String title) throws SQLException {
        return insert(projectId, null, title);
    }

    public Codex createForBook(UUID bookId, String title) throws SQLException {
        return insert(null, bookId, title);
    }

    private Codex insert(UUID projectId, UUID bookId, String title) throws SQLException {
        UUID    id   = UUID.randomUUID();
        Instant now  = Instant.now();
        String  name = (title == null || title.isBlank()) ? "Codex" : title;
        String  sql  = """
                INSERT INTO codex (id, project_id, book_id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setObject(3, bookId);
            ps.setString(4, name);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Codex.builder()
                .id(id).projectId(projectId).bookId(bookId).title(name)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public boolean delete(UUID id) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM codex WHERE id = ?")) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
