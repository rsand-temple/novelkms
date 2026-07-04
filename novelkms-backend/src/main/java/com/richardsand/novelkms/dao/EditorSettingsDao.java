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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.user.UserStyleDao;
import com.richardsand.novelkms.model.editor.EditorSettings;
import com.richardsand.novelkms.model.editor.EditorSettingsDefaults;
import com.richardsand.novelkms.model.editor.EditorSettingsDefinition;

/**
 * Cascading document ("editor") settings: {@code BOOK -> PROJECT -> USER -> SYSTEM}.
 *
 * <p>Single-bundle analogue of {@link UserStyleDao} (which is keyed by a roster
 * of style keys). There is exactly one SYSTEM row, one USER row per user, one
 * PROJECT row per project, and one BOOK row per book. SYSTEM is lazily seeded
 * from {@link EditorSettingsDefaults}; USER, PROJECT, and BOOK rows are
 * copy-on-write overrides that can be deleted to fall back to the next level.
 */
public class EditorSettingsDao {

    private static final ObjectMapper M = new ObjectMapper();

    private final BasicDataSource ds;

    public EditorSettingsDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private String json(EditorSettingsDefinition d) throws SQLException {
        try {
            return M.writeValueAsString(d);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    private EditorSettings map(ResultSet r) throws SQLException {
        try {
            return EditorSettings.builder()
                    .id(r.getObject("id", UUID.class))
                    .scope(r.getString("scope"))
                    .projectId(r.getObject("project_id", UUID.class))
                    .bookId(r.getObject("book_id", UUID.class))
                    .definition(M.readValue(r.getString("definition"), EditorSettingsDefinition.class))
                    .createdAt(r.getTimestamp("created_at").toInstant())
                    .updatedAt(r.getTimestamp("updated_at").toInstant())
                    .build();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    private Optional<EditorSettings> one(String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        }
    }

    private EditorSettings insert(String scope, UUID user, UUID project, UUID book, EditorSettingsDefinition d) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "INSERT INTO editor_settings(id,scope,user_id,project_id,book_id,definition,created_at,updated_at)"
                                + " VALUES (?,?,?,?,?,?,?,?)")) {
            p.setObject(1, id);
            p.setString(2, scope);
            p.setObject(3, user);
            p.setObject(4, project);
            p.setObject(5, book);
            p.setString(6, json(d));
            p.setTimestamp(7, Timestamp.from(now));
            p.setTimestamp(8, Timestamp.from(now));
            p.executeUpdate();
        }
        return one("SELECT * FROM editor_settings WHERE id=?", id).orElseThrow();
    }

    private EditorSettings update(UUID id, EditorSettingsDefinition d) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE editor_settings SET definition=?,updated_at=? WHERE id=?")) {
            p.setString(1, json(d));
            p.setTimestamp(2, Timestamp.from(Instant.now()));
            p.setObject(3, id);
            p.executeUpdate();
        }
        return one("SELECT * FROM editor_settings WHERE id=?", id).orElseThrow();
    }

    private boolean del(String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p.executeUpdate() > 0;
        }
    }

    // ── SYSTEM (factory default, lazily seeded) ───────────────────────────────

    public EditorSettings system() throws SQLException {
        Optional<EditorSettings> x = one(
                "SELECT * FROM editor_settings WHERE scope='SYSTEM' AND user_id IS NULL AND project_id IS NULL");
        return x.isPresent() ? x.get() : insert("SYSTEM", null, null, null, EditorSettingsDefaults.defaults());
    }

    // ── USER override ─────────────────────────────────────────────────────────

    public Optional<EditorSettings> user(UUID userId) throws SQLException {
        return one("SELECT * FROM editor_settings WHERE scope='USER' AND user_id=?", userId);
    }

    /** Resolved user-level settings: USER override if present, else SYSTEM. */
    public EditorSettings resolveUser(UUID userId) throws SQLException {
        return user(userId).orElse(system());
    }

    public EditorSettings upsertUser(UUID userId, EditorSettingsDefinition d) throws SQLException {
        Optional<EditorSettings> x = user(userId);
        return x.isPresent() ? update(x.get().getId(), d) : insert("USER", userId, null, null, d);
    }

    public boolean deleteUser(UUID userId) throws SQLException {
        return del("DELETE FROM editor_settings WHERE scope='USER' AND user_id=?", userId);
    }

    // ── PROJECT override ──────────────────────────────────────────────────────

    public Optional<EditorSettings> project(UUID projectId) throws SQLException {
        return one("SELECT * FROM editor_settings WHERE scope='PROJECT' AND project_id=?", projectId);
    }

    /** Resolved project-level settings: PROJECT override, else the user default. */
    public EditorSettings resolveProject(UUID userId, UUID projectId) throws SQLException {
        Optional<EditorSettings> x = project(projectId);
        return x.isPresent() ? x.get() : resolveUser(userId);
    }

    public EditorSettings upsertProject(UUID projectId, EditorSettingsDefinition d) throws SQLException {
        Optional<EditorSettings> x = project(projectId);
        return x.isPresent() ? update(x.get().getId(), d) : insert("PROJECT", null, projectId, null, d);
    }

    public boolean deleteProject(UUID projectId) throws SQLException {
        return del("DELETE FROM editor_settings WHERE scope='PROJECT' AND project_id=?", projectId);
    }

    // ── BOOK override ─────────────────────────────────────────────────────────

    public Optional<EditorSettings> book(UUID bookId) throws SQLException {
        return one("SELECT * FROM editor_settings WHERE scope='BOOK' AND book_id=?", bookId);
    }

    /** The book's owning project, for the resolution fall-through. */
    private UUID bookProjectId(UUID bookId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT project_id FROM book WHERE id=?")) {
            p.setObject(1, bookId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getObject("project_id", UUID.class) : null;
            }
        }
    }

    /**
     * Resolved book-level settings: BOOK override, else the project resolution
     * ({@code PROJECT -> USER -> SYSTEM}) for the book's owning project.
     */
    public EditorSettings resolveBook(UUID userId, UUID bookId) throws SQLException {
        Optional<EditorSettings> x = book(bookId);
        if (x.isPresent()) return x.get();
        UUID projectId = bookProjectId(bookId);
        return projectId != null ? resolveProject(userId, projectId) : resolveUser(userId);
    }

    public EditorSettings upsertBook(UUID bookId, EditorSettingsDefinition d) throws SQLException {
        Optional<EditorSettings> x = book(bookId);
        return x.isPresent() ? update(x.get().getId(), d) : insert("BOOK", null, null, bookId, d);
    }

    public boolean deleteBook(UUID bookId) throws SQLException {
        return del("DELETE FROM editor_settings WHERE scope='BOOK' AND book_id=?", bookId);
    }
}
