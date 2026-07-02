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

    // -------------------------------------------------------------------------
    // AI reference context (pinned codex entries shared with the AI)
    // -------------------------------------------------------------------------

    /**
     * One codex entry as it appears in the "Manage AI Context" surface and in
     * review-prompt assembly: the entry scene joined to its codex category
     * chapter. {@code content} is the entry's authored HTML (stripped to plain
     * text by the caller before it reaches a prompt).
     */
    public record AiContextEntry(
            UUID sceneId,
            UUID chapterId,
            String categoryKey,
            String categoryTitle,
            String title,
            int wordCount,
            boolean pinned,
            String content,
            String structuredData) {
    }

    private static final String AI_CONTEXT_SELECT =
            "SELECT s.id AS scene_id, s.chapter_id, ch.codex_category, ch.title AS category_title, " +
            "       s.title, s.word_count, s.ai_context_pinned, s.content, s.structured_data " +
            "FROM scene s " +
            "JOIN chapter ch ON ch.id = s.chapter_id " +
            "WHERE ch.codex_id = ? AND ch.deleted_at IS NULL AND s.deleted_at IS NULL ";

    private AiContextEntry mapEntry(ResultSet rs) throws SQLException {
        return new AiContextEntry(
                rs.getObject("scene_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getString("codex_category"),
                rs.getString("category_title"),
                rs.getString("title"),
                rs.getInt("word_count"),
                rs.getBoolean("ai_context_pinned"),
                rs.getString("content"),
                rs.getString("structured_data"));
    }

    /**
     * Every entry in a codex (pinned or not), category first then display order,
     * for the Manage AI Context dialog.
     */
    public List<AiContextEntry> listAiContextEntries(UUID codexId) throws SQLException {
        String sql = AI_CONTEXT_SELECT + "ORDER BY ch.display_order, s.display_order, s.title";
        return queryEntries(sql, codexId);
    }

    /**
     * Only the pinned entries in a codex, in the same order. Used by review
     * assembly and by the per-book pinned-context summary.
     */
    public List<AiContextEntry> listPinnedAiContextEntries(UUID codexId) throws SQLException {
        String sql = AI_CONTEXT_SELECT +
                "AND s.ai_context_pinned = TRUE ORDER BY ch.display_order, s.display_order, s.title";
        return queryEntries(sql, codexId);
    }

    private List<AiContextEntry> queryEntries(String sql, UUID codexId) throws SQLException {
        List<AiContextEntry> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, codexId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapEntry(rs));
                }
            }
        }
        return result;
    }
}
