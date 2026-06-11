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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.model.Style;
import com.richardsand.novelkms.model.StyleDefaults;
import com.richardsand.novelkms.model.StyleDefinition;

/**
 * DAO for paragraph styles.
 *
 * <h2>Scope model</h2>
 * <ul>
 *   <li><b>GLOBAL</b> — one editable row per style_key (lazily seeded from
 *       {@link StyleDefaults#defaultFor(String)}).</li>
 *   <li><b>PROJECT</b> — optional per-project override (one per project+key).</li>
 *   <li><b>BOOK</b> — optional per-book override (one per book+key).</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * {@link #resolveForBook(UUID, String)} returns BOOK override → PROJECT override
 * (the book's project) → GLOBAL. {@link #resolveForProject(UUID, String)} is
 * PROJECT → GLOBAL. Overrides are copy-on-write: the frontend reads the resolved
 * definition and writes it back to create the override row.
 */
public class StyleDao {

    public static final String SCOPE_GLOBAL  = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";
    public static final String SCOPE_BOOK    = "BOOK";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BasicDataSource ds;

    public StyleDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private String toJson(StyleDefinition def) throws SQLException {
        try {
            return MAPPER.writeValueAsString(def);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize style definition", e);
        }
    }

    private StyleDefinition fromJson(String json) throws SQLException {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, StyleDefinition.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse style definition", e);
        }
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Style map(ResultSet rs) throws SQLException {
        return Style.builder()
                .id(rs.getObject("id", UUID.class))
                .styleKey(rs.getString("style_key"))
                .scope(rs.getString("scope"))
                .projectId(rs.getObject("project_id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .definition(fromJson(rs.getString("definition")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Global
    // -------------------------------------------------------------------------

    public Optional<Style> findGlobal(String key) throws SQLException {
        String sql = """
                SELECT * FROM style
                WHERE scope = 'GLOBAL' AND style_key = ? AND project_id IS NULL AND book_id IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Style getOrCreateGlobal(String key) throws SQLException {
        Optional<Style> existing = findGlobal(key);
        return existing.isPresent() ? existing.get() : insertGlobal(key, StyleDefaults.defaultFor(key));
    }

    private Style insertGlobal(String key, StyleDefinition def) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String  sql = """
                INSERT INTO style (id, style_key, scope, project_id, book_id, definition, created_at, updated_at)
                VALUES (?, ?, 'GLOBAL', NULL, NULL, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, key);
            ps.setString(3, toJson(def));
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Style.builder()
                .id(id).styleKey(key).scope(SCOPE_GLOBAL).definition(def)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public Style updateGlobal(String key, StyleDefinition def) throws SQLException {
        getOrCreateGlobal(key);
        Instant now = Instant.now();
        String  sql = """
                UPDATE style SET definition = ?, updated_at = ?
                WHERE scope = 'GLOBAL' AND style_key = ? AND project_id IS NULL AND book_id IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, toJson(def));
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, key);
            ps.executeUpdate();
        }
        return findGlobal(key).orElseThrow(() -> new SQLException("GLOBAL style vanished after update: " + key));
    }

    public Style resetGlobal(String key) throws SQLException {
        return updateGlobal(key, StyleDefaults.defaultFor(key));
    }

    /** Ensures every roster key has a GLOBAL row, returned in roster order. */
    public List<Style> getAllGlobal() throws SQLException {
        List<Style> out = new ArrayList<>();
        for (String key : StyleDefaults.STYLE_KEYS) {
            out.add(getOrCreateGlobal(key));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Project overrides
    // -------------------------------------------------------------------------

    public Optional<Style> findProjectOverride(UUID projectId, String key) throws SQLException {
        String sql = "SELECT * FROM style WHERE scope = 'PROJECT' AND project_id = ? AND style_key = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Style upsertProjectOverride(UUID projectId, String key, StyleDefinition def) throws SQLException {
        Optional<Style> existing = findProjectOverride(projectId, key);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            String sql = "UPDATE style SET definition = ?, updated_at = ? WHERE id = ?";
            try (Connection c = ds.getConnection();
                    PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, toJson(def));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setObject(3, existing.get().getId());
                ps.executeUpdate();
            }
            return findProjectOverride(projectId, key).orElseThrow(
                    () -> new SQLException("PROJECT style vanished after update"));
        }
        UUID   id  = UUID.randomUUID();
        String sql = """
                INSERT INTO style (id, style_key, scope, project_id, book_id, definition, created_at, updated_at)
                VALUES (?, ?, 'PROJECT', ?, NULL, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, key);
            ps.setObject(3, projectId);
            ps.setString(4, toJson(def));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Style.builder()
                .id(id).styleKey(key).scope(SCOPE_PROJECT).projectId(projectId).definition(def)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public boolean deleteProjectOverride(UUID projectId, String key) throws SQLException {
        String sql = "DELETE FROM style WHERE scope = 'PROJECT' AND project_id = ? AND style_key = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Book overrides
    // -------------------------------------------------------------------------

    public Optional<Style> findBookOverride(UUID bookId, String key) throws SQLException {
        String sql = "SELECT * FROM style WHERE scope = 'BOOK' AND book_id = ? AND style_key = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Style upsertBookOverride(UUID bookId, String key, StyleDefinition def) throws SQLException {
        Optional<Style> existing = findBookOverride(bookId, key);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            String sql = "UPDATE style SET definition = ?, updated_at = ? WHERE id = ?";
            try (Connection c = ds.getConnection();
                    PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, toJson(def));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setObject(3, existing.get().getId());
                ps.executeUpdate();
            }
            return findBookOverride(bookId, key).orElseThrow(
                    () -> new SQLException("BOOK style vanished after update"));
        }
        UUID   id  = UUID.randomUUID();
        String sql = """
                INSERT INTO style (id, style_key, scope, project_id, book_id, definition, created_at, updated_at)
                VALUES (?, ?, 'BOOK', NULL, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, key);
            ps.setObject(3, bookId);
            ps.setString(4, toJson(def));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Style.builder()
                .id(id).styleKey(key).scope(SCOPE_BOOK).bookId(bookId).definition(def)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public boolean deleteBookOverride(UUID bookId, String key) throws SQLException {
        String sql = "DELETE FROM style WHERE scope = 'BOOK' AND book_id = ? AND style_key = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setString(2, key);
            return ps.executeUpdate() > 0;
        }
    }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    /** PROJECT override → GLOBAL. */
    public Style resolveForProject(UUID projectId, String key) throws SQLException {
        Optional<Style> proj = findProjectOverride(projectId, key);
        return proj.isPresent() ? proj.get() : getOrCreateGlobal(key);
    }

    /** BOOK override → PROJECT override (book's project) → GLOBAL. */
    public Style resolveForBook(UUID bookId, String key) throws SQLException {
        Optional<Style> book = findBookOverride(bookId, key);
        if (book.isPresent()) return book.get();
        UUID projectId = lookupBookProject(bookId);
        if (projectId != null) {
            Optional<Style> proj = findProjectOverride(projectId, key);
            if (proj.isPresent()) return proj.get();
        }
        return getOrCreateGlobal(key);
    }

    /** Resolved stylesheet (all roster keys) for a project, in roster order. */
    public List<Style> resolveAllForProject(UUID projectId) throws SQLException {
        List<Style> out = new ArrayList<>();
        for (String key : StyleDefaults.STYLE_KEYS) {
            out.add(resolveForProject(projectId, key));
        }
        return out;
    }

    /** Resolved stylesheet (all roster keys) for a book, in roster order. */
    public List<Style> resolveAllForBook(UUID bookId) throws SQLException {
        UUID projectId = lookupBookProject(bookId);
        List<Style> out = new ArrayList<>();
        for (String key : StyleDefaults.STYLE_KEYS) {
            Optional<Style> book = findBookOverride(bookId, key);
            if (book.isPresent()) { out.add(book.get()); continue; }
            if (projectId != null) {
                Optional<Style> proj = findProjectOverride(projectId, key);
                if (proj.isPresent()) { out.add(proj.get()); continue; }
            }
            out.add(getOrCreateGlobal(key));
        }
        return out;
    }

    private UUID lookupBookProject(UUID bookId) throws SQLException {
        String sql = "SELECT project_id FROM book WHERE id = ?";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject("project_id", UUID.class) : null;
            }
        }
    }
}
