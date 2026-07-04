package com.richardsand.novelkms.dao.ai;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.dao.MemoryTemplateDao;
import com.richardsand.novelkms.model.EditorialTemplateDefaults;
import com.richardsand.novelkms.model.book.BookSummaryTemplateDefaults;
import com.richardsand.novelkms.model.chapter.ChapterSummaryTemplateDefaults;

/**
 * Storage and resolution for the three author-editable AI generation prompt
 * templates that V35 introduces: chapter-summary, book-summary, and editorial.
 *
 * <p>All three follow the identical single-block selection model established by
 * {@link MemoryTemplateDao} (V24):
 * <ul>
 *   <li><b>book override</b>    — nullable column on {@code book}</li>
 *   <li><b>project override</b> — nullable column on {@code project}</li>
 *   <li><b>user global</b>      — one per-user row in the type-specific global
 *       table ({@code chapter_summary_template_global}, etc.)</li>
 *   <li><b>system default</b>   — a Java constant in the corresponding
 *       {@code *Defaults} class; no DB row, uneditable by construction</li>
 * </ul>
 *
 * <p>Resolution is always most-specific first:
 * {@code book -> project -> user global -> system}.
 *
 * <p>The {@code TemplateType} enum carries the table/column names that
 * distinguish the three types; all SQL that uses those names interpolates
 * them from the enum (never from user input) so the approach is safe.
 */
public class AiPromptTemplateDao {

    /** Resolved template and its provenance. {@code content} is never null or blank. */
    public record Resolved(String scope, String content, boolean hasOwnOverride) {}

    // ── Template types ────────────────────────────────────────────────────────

    /**
     * Identifies which of the three prompt-template families is being accessed.
     * Each value carries the column name used on both {@code project} and
     * {@code book}, the name of the per-user global table, and the system-default
     * constant from the corresponding {@code *Defaults} class.
     */
    public enum TemplateType {
        CHAPTER_SUMMARY(
                "chapter_summary_template",
                "chapter_summary_template_global",
                ChapterSummaryTemplateDefaults.SYSTEM_SCOPE,
                ChapterSummaryTemplateDefaults.SYSTEM_DEFAULT),
        BOOK_SUMMARY(
                "book_summary_template",
                "book_summary_template_global",
                BookSummaryTemplateDefaults.SYSTEM_SCOPE,
                BookSummaryTemplateDefaults.SYSTEM_DEFAULT),
        EDITORIAL(
                "editorial_template",
                "editorial_template_global",
                EditorialTemplateDefaults.SYSTEM_SCOPE,
                EditorialTemplateDefaults.SYSTEM_DEFAULT);

        /** Column name on {@code project} and {@code book} tables. */
        final String column;
        /** Name of the per-user global table. */
        final String globalTable;
        /** Scope label to return when falling back to the Java constant. */
        final String systemScope;
        /** The uneditable factory default text. */
        final String systemDefault;

        TemplateType(String column, String globalTable, String systemScope, String systemDefault) {
            this.column        = column;
            this.globalTable   = globalTable;
            this.systemScope   = systemScope;
            this.systemDefault = systemDefault;
        }
    }

    private final BasicDataSource ds;

    public AiPromptTemplateDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the effective template for a generation call (book &rarr; project
     * &rarr; user global &rarr; system default). Used by {@code AiReviewService}
     * to obtain the system prompt to pass to the provider.
     */
    public Resolved resolveForGeneration(TemplateType type, UUID userId,
            UUID projectId, UUID bookId) throws SQLException {
        if (bookId != null) {
            String book = blankToNull(bookColumn(type, bookId));
            if (book != null) return new Resolved("BOOK", book, true);
        }
        return resolveBelowBook(type, userId, projectId, false);
    }

    /** Dialog pre-population for the global (user) scope. */
    public Resolved resolveGlobal(TemplateType type, UUID userId) throws SQLException {
        String user = blankToNull(userGlobal(type, userId));
        if (user != null) return new Resolved("USER", user, true);
        return new Resolved(type.systemScope, type.systemDefault, false);
    }

    /** Dialog pre-population for a project scope. */
    public Resolved resolveForProject(TemplateType type, UUID userId,
            UUID projectId) throws SQLException {
        if (projectId != null) {
            String project = blankToNull(projectColumn(type, projectId));
            if (project != null) return new Resolved("PROJECT", project, true);
        }
        Resolved below = resolveGlobal(type, userId);
        return new Resolved(below.scope(), below.content(), false);
    }

    /** Dialog pre-population for a book scope. */
    public Resolved resolveForBook(TemplateType type, UUID userId,
            UUID bookId) throws SQLException {
        if (bookId != null) {
            String book = blankToNull(bookColumn(type, bookId));
            if (book != null) return new Resolved("BOOK", book, true);
            UUID projectId = bookProjectId(bookId);
            Resolved below = resolveBelowBook(type, userId, projectId, false);
            return new Resolved(below.scope(), below.content(), false);
        }
        return resolveGlobal(type, userId);
    }

    private Resolved resolveBelowBook(TemplateType type, UUID userId,
            UUID projectId, boolean hasOwnOverride) throws SQLException {
        if (projectId != null) {
            String project = blankToNull(projectColumn(type, projectId));
            if (project != null) return new Resolved("PROJECT", project, hasOwnOverride);
        }
        String user = blankToNull(userGlobal(type, userId));
        if (user != null) return new Resolved("USER", user, hasOwnOverride);
        return new Resolved(type.systemScope, type.systemDefault, hasOwnOverride);
    }

    // ── User global ───────────────────────────────────────────────────────────

    /** Creates or updates the caller's user-global override. */
    public void upsertGlobal(TemplateType type, UUID userId, String content) throws SQLException {
        Instant now = Instant.now();
        if (userGlobal(type, userId) == null) {
            String sql = "INSERT INTO " + type.globalTable
                    + "(user_id, content, created_at, updated_at) VALUES (?,?,?,?)";
            try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setObject(1, userId);
                p.setString(2, content);
                p.setTimestamp(3, Timestamp.from(now));
                p.setTimestamp(4, Timestamp.from(now));
                p.executeUpdate();
            }
        } else {
            String sql = "UPDATE " + type.globalTable
                    + " SET content=?, updated_at=? WHERE user_id=?";
            try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, content);
                p.setTimestamp(2, Timestamp.from(now));
                p.setObject(3, userId);
                p.executeUpdate();
            }
        }
    }

    /** Removes the user-global override, reverting to system default. */
    public boolean deleteGlobal(TemplateType type, UUID userId) throws SQLException {
        String sql = "DELETE FROM " + type.globalTable + " WHERE user_id=?";
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, userId);
            return p.executeUpdate() > 0;
        }
    }

    // ── Project / book overrides ──────────────────────────────────────────────

    public void setProject(TemplateType type, UUID projectId, String content) throws SQLException {
        updateColumn(type.column, "project", projectId, content);
    }

    public boolean clearProject(TemplateType type, UUID projectId) throws SQLException {
        return updateColumn(type.column, "project", projectId, null) > 0;
    }

    public void setBook(TemplateType type, UUID bookId, String content) throws SQLException {
        updateColumn(type.column, "book", bookId, content);
    }

    public boolean clearBook(TemplateType type, UUID bookId) throws SQLException {
        return updateColumn(type.column, "book", bookId, null) > 0;
    }

    // ── Scalar reads ──────────────────────────────────────────────────────────

    private String userGlobal(TemplateType type, UUID userId) throws SQLException {
        return scalar("SELECT content FROM " + type.globalTable + " WHERE user_id=?", userId);
    }

    private String projectColumn(TemplateType type, UUID projectId) throws SQLException {
        return scalar("SELECT " + type.column + " FROM project WHERE id=?", projectId);
    }

    private String bookColumn(TemplateType type, UUID bookId) throws SQLException {
        return scalar("SELECT " + type.column + " FROM book WHERE id=?", bookId);
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
     * {@code column}, {@code table} are fixed enum literals — never user input —
     * so interpolating them into SQL is safe, as with {@link MemoryTemplateDao}.
     */
    private int updateColumn(String column, String table, UUID id, String content)
            throws SQLException {
        String sql = "UPDATE " + table + " SET " + column + "=?, updated_at=? WHERE id=?";
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
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
