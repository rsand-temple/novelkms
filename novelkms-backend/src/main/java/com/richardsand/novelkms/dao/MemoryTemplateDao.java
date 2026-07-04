package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.dao.ai.AiFormInstructionsDao;
import com.richardsand.novelkms.model.chapter.ChapterMemoryTemplateDefaults;

/**
 * Storage and resolution for the <em>memory-document template</em> — the section
 * structure the AI fills in when generating a chapter's memory document.
 *
 * <p>This is a direct parallel of {@link AiFormInstructionsDao}: three
 * independent, optional override layers sit over one non-editable system
 * default:
 * <ul>
 *   <li><b>book override</b> — {@code book.memory_template} (nullable)</li>
 *   <li><b>project override</b> — {@code project.memory_template} (nullable)</li>
 *   <li><b>user global</b> — one {@code memory_template_global} row per user</li>
 *   <li><b>system default</b> — {@link ChapterMemoryTemplateDefaults#SYSTEM_DEFAULT}
 *       (a Java constant; no DB row)</li>
 * </ul>
 *
 * <p>Resolution is single-block <em>selection</em>, most-specific first, with no
 * inheritance and no concatenation across layers:
 * {@code book -> project -> user global -> system}. A blank (whitespace-only)
 * stored value is treated as absent.
 *
 * <p>As with {@code AiFormInstructionsDao}, the project/book columns are
 * read/written with targeted scalar SQL rather than through {@code ProjectDao} /
 * {@code BookDao} update paths, so adding a template override cannot perturb
 * those "echo every persisted field" update payloads.
 */
public class MemoryTemplateDao {

    /** Resolved template plus where it came from. {@code content} is never null/blank. */
    public record Resolved(String scope, String content, boolean hasOwnOverride) {}

    private final BasicDataSource ds;

    public MemoryTemplateDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the template that governs memory generation for the given
     * book/project, most-specific first.
     */
    public Resolved resolveForGeneration(UUID userId, UUID projectId, UUID bookId) throws SQLException {
        String book = bookId == null ? null : blankToNull(bookTemplate(bookId));
        if (book != null) return new Resolved("BOOK", book, true);
        return resolveBelowBook(userId, projectId, false);
    }

    /** Dialog pre-population for the global (user) scope: user global if set, else system. */
    public Resolved resolveGlobal(UUID userId) throws SQLException {
        String user = blankToNull(userGlobal(userId));
        if (user != null) return new Resolved("USER", user, true);
        return new Resolved(ChapterMemoryTemplateDefaults.SYSTEM_SCOPE,
                ChapterMemoryTemplateDefaults.SYSTEM_DEFAULT, false);
    }

    /** Dialog pre-population for a project: project override, else user, else system. */
    public Resolved resolveForProject(UUID userId, UUID projectId) throws SQLException {
        String project = projectId == null ? null : blankToNull(projectTemplate(projectId));
        if (project != null) return new Resolved("PROJECT", project, true);
        Resolved below = resolveGlobal(userId);
        return new Resolved(below.scope(), below.content(), false);
    }

    /** Dialog pre-population for a book: book, else project, else user, else system. */
    public Resolved resolveForBook(UUID userId, UUID bookId) throws SQLException {
        String book = bookId == null ? null : blankToNull(bookTemplate(bookId));
        if (book != null) return new Resolved("BOOK", book, true);
        UUID projectId = bookId == null ? null : bookProjectId(bookId);
        Resolved below = resolveBelowBook(userId, projectId, false);
        return new Resolved(below.scope(), below.content(), false);
    }

    /** Shared tail used once the book layer is known absent. */
    private Resolved resolveBelowBook(UUID userId, UUID projectId, boolean hasOwnOverride) throws SQLException {
        String project = projectId == null ? null : blankToNull(projectTemplate(projectId));
        if (project != null) return new Resolved("PROJECT", project, hasOwnOverride);
        String user = blankToNull(userGlobal(userId));
        if (user != null) return new Resolved("USER", user, hasOwnOverride);
        return new Resolved(ChapterMemoryTemplateDefaults.SYSTEM_SCOPE,
                ChapterMemoryTemplateDefaults.SYSTEM_DEFAULT, hasOwnOverride);
    }

    // ── User global (memory_template_global) ──────────────────────────────────

    /** Upserts the caller's user-global memory template. */
    public void upsertGlobal(UUID userId, String content) throws SQLException {
        Instant now = Instant.now();
        if (userGlobal(userId) == null) {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "INSERT INTO memory_template_global(user_id, content, created_at, updated_at)"
                                    + " VALUES (?,?,?,?)")) {
                p.setObject(1, userId);
                p.setString(2, content);
                p.setTimestamp(3, Timestamp.from(now));
                p.setTimestamp(4, Timestamp.from(now));
                p.executeUpdate();
            }
        } else {
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "UPDATE memory_template_global SET content=?, updated_at=? WHERE user_id=?")) {
                p.setString(1, content);
                p.setTimestamp(2, Timestamp.from(now));
                p.setObject(3, userId);
                p.executeUpdate();
            }
        }
    }

    /** Removes the user-global override, reverting the user to the system default. */
    public boolean deleteGlobal(UUID userId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM memory_template_global WHERE user_id=?")) {
            p.setObject(1, userId);
            return p.executeUpdate() > 0;
        }
    }

    // ── Project / book overrides (columns) ────────────────────────────────────

    public void setProject(UUID projectId, String content) throws SQLException {
        updateColumn("project", projectId, content);
    }

    public boolean clearProject(UUID projectId) throws SQLException {
        return updateColumn("project", projectId, null) > 0;
    }

    public void setBook(UUID bookId, String content) throws SQLException {
        updateColumn("book", bookId, content);
    }

    public boolean clearBook(UUID bookId) throws SQLException {
        return updateColumn("book", bookId, null) > 0;
    }

    // ── Scalar reads ──────────────────────────────────────────────────────────

    private String userGlobal(UUID userId) throws SQLException {
        return scalar("SELECT content FROM memory_template_global WHERE user_id=?", userId);
    }

    private String projectTemplate(UUID projectId) throws SQLException {
        return scalar("SELECT memory_template FROM project WHERE id=?", projectId);
    }

    private String bookTemplate(UUID bookId) throws SQLException {
        return scalar("SELECT memory_template FROM book WHERE id=?", bookId);
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
    private int updateColumn(String table, UUID id, String content) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE " + table + " SET memory_template=?, updated_at=? WHERE id=?")) {
            if (content == null) {
                p.setNull(1, java.sql.Types.VARCHAR);
            } else {
                p.setString(1, content);
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
