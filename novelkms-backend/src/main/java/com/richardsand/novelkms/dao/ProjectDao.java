package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.Project;

public class ProjectDao {
    private final BasicDataSource ds;

    public ProjectDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private Project map(ResultSet rs) throws SQLException {
        return Project.builder()
                .id(rs.getObject("id", UUID.class))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .authorFirstName(rs.getString("author_first_name"))
                .authorLastName(rs.getString("author_last_name"))
                .copyright(rs.getString("copyright"))
                .displayName(rs.getString("display_name"))
                .emailAddress(rs.getString("email_address"))
                .phoneNumber(rs.getString("phone_number"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    /** Tenant-scoped project list. This is the only list method resources should call. */
    public List<Project> findAllForUser(UUID userId) throws SQLException {
        String sql = "SELECT * FROM project WHERE owner_user_id = ? AND deleted_at IS NULL ORDER BY title";
        List<Project> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        }
        return result;
    }

    public Optional<Project> findByIdForUser(UUID id, UUID userId) throws SQLException {
        String sql = "SELECT * FROM project WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Project createForUser(UUID userId, String title, String description) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
                INSERT INTO project (id, owner_user_id, title, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Project.builder().id(id).title(title).description(description)
                .createdAt(now).updatedAt(now).build();
    }

    public Optional<Project> updateForUser(UUID userId, Project project) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE project
                SET title = ?, description = ?, author_first_name = ?, author_last_name = ?,
                    copyright = ?, display_name = ?, email_address = ?, phone_number = ?,
                    updated_at = ?
                WHERE id = ? AND owner_user_id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, project.getTitle());
            ps.setString(2, project.getDescription());
            ps.setString(3, project.getAuthorFirstName());
            ps.setString(4, project.getAuthorLastName());
            ps.setString(5, project.getCopyright());
            ps.setString(6, project.getDisplayName());
            ps.setString(7, project.getEmailAddress());
            ps.setString(8, project.getPhoneNumber());
            ps.setTimestamp(9, Timestamp.from(now));
            ps.setObject(10, project.getId());
            ps.setObject(11, userId);
            if (ps.executeUpdate() == 0) return Optional.empty();
        }
        return findByIdForUser(project.getId(), userId);
    }

    public boolean deleteForUser(UUID userId, UUID id) throws SQLException {
        String sql = "DELETE FROM project WHERE id = ? AND owner_user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public int getTotalWordCountForUser(UUID userId, UUID projectId) throws SQLException {
        if (findByIdForUser(projectId, userId).isEmpty()) return -1;
        return getTotalWordCount(projectId);
    }

    // Internal unscoped methods retained for services that run only after the resource/filter
    // has already established ownership. New resource code must use the *ForUser variants.
    public List<Project> findAll() throws SQLException {
        String sql = "SELECT * FROM project WHERE deleted_at IS NULL ORDER BY title";
        List<Project> result = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        }
        return result;
    }

    public Optional<Project> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM project WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Project create(String title, String description) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = "INSERT INTO project (id, title, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id); ps.setString(2, title); ps.setString(3, description);
            ps.setTimestamp(4, Timestamp.from(now)); ps.setTimestamp(5, Timestamp.from(now)); ps.executeUpdate();
        }
        return Project.builder().id(id).title(title).description(description).createdAt(now).updatedAt(now).build();
    }

    public Optional<Project> update(Project project) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE project
                SET title = ?, description = ?, author_first_name = ?, author_last_name = ?,
                    copyright = ?, display_name = ?, email_address = ?, phone_number = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, project.getTitle()); ps.setString(2, project.getDescription());
            ps.setString(3, project.getAuthorFirstName()); ps.setString(4, project.getAuthorLastName());
            ps.setString(5, project.getCopyright()); ps.setString(6, project.getDisplayName());
            ps.setString(7, project.getEmailAddress()); ps.setString(8, project.getPhoneNumber());
            ps.setTimestamp(9, Timestamp.from(now)); ps.setObject(10, project.getId());
            if (ps.executeUpdate() == 0) return Optional.empty();
        }
        return findById(project.getId());
    }

    public boolean delete(UUID id) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM project WHERE id = ?")) {
            ps.setObject(1, id); return ps.executeUpdate() > 0;
        }
    }

    public int getTotalWordCount(UUID projectId) throws SQLException {
        int total = 0;
        String sceneSql = """
                SELECT COALESCE(SUM(s.word_count), 0)
                FROM scene s JOIN chapter ch ON ch.id = s.chapter_id JOIN book b ON b.id = ch.book_id
                WHERE b.project_id = ? AND b.deleted_at IS NULL AND ch.deleted_at IS NULL AND s.deleted_at IS NULL
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sceneSql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) total += rs.getInt(1); }
        }
        String chapterSql = "SELECT ch.title, ch.subtitle FROM chapter ch JOIN book b ON b.id = ch.book_id WHERE b.project_id = ? AND b.deleted_at IS NULL AND ch.deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(chapterSql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("title"), s = rs.getString("subtitle");
                    total += (t == null || t.isBlank()) ? 2 : countPlainTextWords(t);
                    total += countPlainTextWords(s);
                }
            }
        }
        String partSql = "SELECT pt.title, pt.subtitle FROM part pt JOIN book b ON b.id = pt.book_id WHERE b.project_id = ? AND b.deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(partSql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("title"), s = rs.getString("subtitle");
                    total += (t == null || t.isBlank()) ? 2 : countPlainTextWords(t);
                    total += countPlainTextWords(s);
                }
            }
        }
        return total;
    }

    private static int countPlainTextWords(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0; boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                if (!inWord) { count++; inWord = true; }
            } else inWord = false;
        }
        return count;
    }
}
