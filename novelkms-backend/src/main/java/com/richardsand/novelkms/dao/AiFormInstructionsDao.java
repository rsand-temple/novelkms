package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.AiFormInstructionsDefaults;

/**
 * Storage and resolution for AI review <em>form</em> instructions.
 *
 * <p>Three independent, optional override layers sit over one non-editable
 * system default:
 * <ul>
 *   <li><b>book override</b> — {@code book.ai_form_instructions} (nullable)</li>
 *   <li><b>project override</b> — {@code project.ai_form_instructions} (nullable)</li>
 *   <li><b>user global</b> — one {@code ai_form_global} row per user</li>
 *   <li><b>system default</b> — {@link AiFormInstructionsDefaults#SYSTEM_DEFAULT}
 *       (a Java constant; no DB row)</li>
 * </ul>
 *
 * <p>Resolution is single-block <em>selection</em>, most-specific first, with no
 * inheritance and no concatenation across layers:
 * {@code book -> project -> user global -> system}. A blank (whitespace-only)
 * stored value is treated as absent, so an override is "present" only when it
 * holds real text.
 *
 * <p>This DAO deliberately reads/writes the {@code ai_form_instructions} columns
 * with targeted scalar SQL rather than going through {@code ProjectDao} /
 * {@code BookDao} update paths, so adding form instructions cannot perturb those
 * fragile "echo every persisted field" update payloads.
 */
public class AiFormInstructionsDao {

    /** Resolved form block plus where it came from. {@code instructions} is never null/blank. */
    public record Resolved(String scope, String instructions, boolean hasOwnOverride) {}

    private final BasicDataSource ds;

    public AiFormInstructionsDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the form block that governs a review of the given book/project,
     * most-specific first. {@code hasOwnOverride} reflects the book layer.
     */
    public Resolved resolveForReview(UUID userId, UUID projectId, UUID bookId) throws SQLException {
        String book = bookId == null ? null : blankToNull(bookInstructions(bookId));
        if (book != null) return new Resolved("BOOK", book, true);
        return resolveBelowBook(userId, projectId, false);
    }

    /** Dialog pre-population for the global (user) scope: user global if set, else system. */
    public Resolved resolveGlobal(UUID userId) throws SQLException {
        String user = blankToNull(userGlobal(userId));
        if (user != null) return new Resolved("USER", user, true);
        return new Resolved(AiFormInstructionsDefaults.SYSTEM_SCOPE,
                AiFormInstructionsDefaults.SYSTEM_DEFAULT, false);
    }

    /** Dialog pre-population for a project: project override, else user, else system. */
    public Resolved resolveForProject(UUID userId, UUID projectId) throws SQLException {
        String project = projectId == null ? null : blankToNull(projectInstructions(projectId));
        if (project != null) return new Resolved("PROJECT", project, true);
        Resolved below = resolveGlobal(userId);
        return new Resolved(below.scope(), below.instructions(), false);
    }

    /** Dialog pre-population for a book: book, else project, else user, else system. */
    public Resolved resolveForBook(UUID userId, UUID bookId) throws SQLException {
        String book = bookId == null ? null : blankToNull(bookInstructions(bookId));
        if (book != null) return new Resolved("BOOK", book, true);
        UUID projectId = bookId == null ? null : bookProjectId(bookId);
        Resolved below = resolveBelowBook(userId, projectId, false);
        return new Resolved(below.scope(), below.instructions(), false);
    }

    /** Shared tail used once the book layer is known absent. */
    private Resolved resolveBelowBook(UUID userId, UUID projectId, boolean hasOwnOverride) throws SQLException {
        String project = projectId == null ? null : blankToNull(projectInstructions(projectId));
        if (project != null) return new Resolved("PROJECT", project, hasOwnOverride);
        String user = blankToNull(userGlobal(userId));
        if (user != null) return new Resolved("USER", user, hasOwnOverride);
        return new Resolved(AiFormInstructionsDefaults.SYSTEM_SCOPE,
                AiFormInstructionsDefaults.SYSTEM_DEFAULT, hasOwnOverride);
    }

    // ── User global (ai_form_global) ──────────────────────────────────────────

    /** Upserts the caller's user-global form instructions. */
    public void upsertGlobal(UUID userId, String instructions) throws SQLException {
        Instant now = Instant.now();
        if (userGlobal(userId) == null) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO ai_form_global(user_id, instructions, created_at, updated_at)"
                                    + " VALUES (?,?,?,?)")) {
                p.setObject(1, userId);
                p.setString(2, instructions);
                p.setTimestamp(3, Timestamp.from(now));
                p.setTimestamp(4, Timestamp.from(now));
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE ai_form_global SET instructions=?, updated_at=? WHERE user_id=?")) {
                p.setString(1, instructions);
                p.setTimestamp(2, Timestamp.from(now));
                p.setObject(3, userId);
                p.executeUpdate();
            }
        }
    }

    /** Removes the user-global override, reverting the user to the system default. */
    public boolean deleteGlobal(UUID userId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM ai_form_global WHERE user_id=?")) {
            p.setObject(1, userId);
            return p.executeUpdate() > 0;
        }
    }

    // ── Project / book overrides (columns) ────────────────────────────────────

    public void setProject(UUID projectId, String instructions) throws SQLException {
        updateColumn("project", projectId, instructions);
    }

    public boolean clearProject(UUID projectId) throws SQLException {
        return updateColumn("project", projectId, null) > 0;
    }

    public void setBook(UUID bookId, String instructions) throws SQLException {
        updateColumn("book", bookId, instructions);
    }

    public boolean clearBook(UUID bookId) throws SQLException {
        return updateColumn("book", bookId, null) > 0;
    }

    // ── Scalar reads ──────────────────────────────────────────────────────────

    private String userGlobal(UUID userId) throws SQLException {
        return scalar("SELECT instructions FROM ai_form_global WHERE user_id=?", userId);
    }

    private String projectInstructions(UUID projectId) throws SQLException {
        return scalar("SELECT ai_form_instructions FROM project WHERE id=?", projectId);
    }

    private String bookInstructions(UUID bookId) throws SQLException {
        return scalar("SELECT ai_form_instructions FROM book WHERE id=?", bookId);
    }

    private UUID bookProjectId(UUID bookId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT project_id FROM book WHERE id=?")) {
            p.setObject(1, bookId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getObject("project_id", UUID.class) : null;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * {@code table} is a fixed internal literal ("project" / "book"), never user
     * input, so interpolating it into the SQL is safe.
     */
    private int updateColumn(String table, UUID id, String instructions) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE " + table + " SET ai_form_instructions=?, updated_at=? WHERE id=?")) {
            if (instructions == null) {
                p.setNull(1, java.sql.Types.VARCHAR);
            } else {
                p.setString(1, instructions);
            }
            p.setTimestamp(2, Timestamp.from(Instant.now()));
            p.setObject(3, id);
            return p.executeUpdate();
        }
    }

    private String scalar(String sql, Object arg) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, arg);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
