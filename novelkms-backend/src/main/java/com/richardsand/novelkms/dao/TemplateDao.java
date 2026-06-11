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

import com.richardsand.novelkms.model.Template;

/**
 * DAO for page-layout templates.
 *
 * <h2>Scope model</h2>
 * <ul>
 *   <li><b>GLOBAL</b> — exactly one editable row per {@code templateType},
 *       spanning all projects. Lazily seeded from a code constant
 *       ({@link #defaultContentFor(String)}) the first time it is requested.</li>
 *   <li><b>BOOK</b> — an optional per-book override (one row per
 *       {@code (book_id, templateType)}).</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * {@link #resolveForBook(UUID, String)} returns the BOOK override if present,
 * otherwise the GLOBAL default (creating it on demand). "Override for this
 * book" is a frontend action: it reads the resolved content, then writes it
 * back via {@link #upsertBookOverride(UUID, String, String)}. "Reset to
 * default" maps to {@link #resetGlobal(String)} / {@link #deleteBookOverride}.
 */
public class TemplateDao {

    public static final String TYPE_COVER   = "COVER";
    public static final String TYPE_PART    = "PART";
    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_BOOK   = "BOOK";

    // -------------------------------------------------------------------------
    // Default (factory) content. Tokens render as <span data-token="..."> and
    // are resolved at render time. Authors may freely restyle from here.
    // -------------------------------------------------------------------------

    public static final String DEFAULT_COVER_CONTENT =
            """
            <h1 style="text-align: center"><span data-token="TITLE"></span></h1>
            <p style="text-align: center"><span data-token="SUBTITLE"></span></p>
            <p style="text-align: center">By <span data-token="AUTHOR_FULL_NAME"></span></p>
            """;

    public static final String DEFAULT_PART_CONTENT =
            """
            <h1 style="text-align: center">Part <span data-token="PART_NUMBER"></span></h1>
            <h2 style="text-align: center"><span data-token="PART_TITLE"></span></h2>
            """;

    /** Returns the factory-default content for a template type. */
    public static String defaultContentFor(String type) {
        return TYPE_PART.equals(type) ? DEFAULT_PART_CONTENT : DEFAULT_COVER_CONTENT;
    }

    // -------------------------------------------------------------------------

    private final BasicDataSource ds;

    public TemplateDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private Template map(ResultSet rs) throws SQLException {
        return Template.builder()
                .id(rs.getObject("id", UUID.class))
                .templateType(rs.getString("template_type"))
                .scope(rs.getString("scope"))
                .bookId(rs.getObject("book_id", UUID.class)) // null for GLOBAL
                .content(rs.getString("content"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // -------------------------------------------------------------------------
    // Global templates
    // -------------------------------------------------------------------------

    public Optional<Template> findGlobal(String type) throws SQLException {
        String sql = """
                SELECT * FROM template
                WHERE scope = 'GLOBAL' AND template_type = ? AND book_id IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the GLOBAL template for the given type, creating it from the
     * factory default if it does not yet exist. This is the lazy-seed path:
     * the application never needs an explicit "install defaults" step.
     */
    public Template getOrCreateGlobal(String type) throws SQLException {
        Optional<Template> existing = findGlobal(type);
        return existing.isPresent() ? existing.get() : insertGlobal(type, defaultContentFor(type));
    }

    private Template insertGlobal(String type, String content) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String  sql = """
                INSERT INTO template (id, template_type, scope, book_id, content, created_at, updated_at)
                VALUES (?, ?, 'GLOBAL', NULL, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, type);
            ps.setString(3, content);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Template.builder()
                .id(id).templateType(type).scope(SCOPE_GLOBAL).bookId(null)
                .content(content).createdAt(now).updatedAt(now)
                .build();
    }

    /** Updates the GLOBAL template content, seeding the row first if needed. */
    public Template updateGlobal(String type, String content) throws SQLException {
        getOrCreateGlobal(type); // ensure the row exists
        Instant now = Instant.now();
        String  sql = """
                UPDATE template SET content = ?, updated_at = ?
                WHERE scope = 'GLOBAL' AND template_type = ? AND book_id IS NULL
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, type);
            ps.executeUpdate();
        }
        return findGlobal(type).orElseThrow(
                () -> new SQLException("GLOBAL template vanished after update: " + type));
    }

    /** Resets the GLOBAL template content back to the factory default. */
    public Template resetGlobal(String type) throws SQLException {
        return updateGlobal(type, defaultContentFor(type));
    }

    // -------------------------------------------------------------------------
    // Book overrides
    // -------------------------------------------------------------------------

    public Optional<Template> findBookOverride(UUID bookId, String type) throws SQLException {
        String sql = """
                SELECT * FROM template
                WHERE scope = 'BOOK' AND book_id = ? AND template_type = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the effective template for a book: the BOOK override if present,
     * otherwise the (lazily created) GLOBAL default. Inspect the returned
     * {@code scope} to tell whether the caller is looking at an override.
     */
    public Template resolveForBook(UUID bookId, String type) throws SQLException {
        Optional<Template> override = findBookOverride(bookId, type);
        return override.isPresent() ? override.get() : getOrCreateGlobal(type);
    }

    /**
     * Creates or updates the BOOK override row for {@code (bookId, type)}.
     * This is the copy-on-write target: the frontend seeds the first write
     * with the currently-resolved content.
     */
    public Template upsertBookOverride(UUID bookId, String type, String content) throws SQLException {
        Optional<Template> existing = findBookOverride(bookId, type);
        Instant now = Instant.now();

        if (existing.isPresent()) {
            String sql = "UPDATE template SET content = ?, updated_at = ? WHERE id = ?";
            try (Connection c = ds.getConnection();
                    PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, content);
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setObject(3, existing.get().getId());
                ps.executeUpdate();
            }
            return findBookOverride(bookId, type).orElseThrow(
                    () -> new SQLException("BOOK override vanished after update"));
        }

        UUID   id  = UUID.randomUUID();
        String sql = """
                INSERT INTO template (id, template_type, scope, book_id, content, created_at, updated_at)
                VALUES (?, ?, 'BOOK', ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, type);
            ps.setObject(3, bookId);
            ps.setString(4, content);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.executeUpdate();
        }
        return Template.builder()
                .id(id).templateType(type).scope(SCOPE_BOOK).bookId(bookId)
                .content(content).createdAt(now).updatedAt(now)
                .build();
    }

    /** Removes a book override, reverting that book to the GLOBAL default. */
    public boolean deleteBookOverride(UUID bookId, String type) throws SQLException {
        String sql = """
                DELETE FROM template
                WHERE scope = 'BOOK' AND book_id = ? AND template_type = ?
                """;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, bookId);
            ps.setString(2, type);
            return ps.executeUpdate() > 0;
        }
    }
}
