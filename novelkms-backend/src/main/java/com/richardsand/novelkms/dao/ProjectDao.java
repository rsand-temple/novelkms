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

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Project map(ResultSet rs) throws SQLException {
        return Project.builder()
                .id(rs.getObject("id", UUID.class))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .authorFirstName(rs.getString("author_first_name"))
                .authorLastName(rs.getString("author_last_name"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<Project> findAll() throws SQLException {
        String        sql    = "SELECT * FROM project ORDER BY title";
        List<Project> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    public Optional<Project> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM project WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    public Project create(String title, String description) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String  sql = """
                INSERT INTO project (id, title, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Project.builder()
                .id(id).title(title).description(description)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Optional<Project> update(UUID id, String title, String description,
            String authorFirstName, String authorLastName) throws SQLException {
        Instant now = Instant.now();
        String  sql = """
                UPDATE project
                SET title = ?, description = ?, author_first_name = ?, author_last_name = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, authorFirstName);
            ps.setString(4, authorLastName);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setObject(6, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    public boolean delete(UUID id) throws SQLException {
        String sql = "DELETE FROM project WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
