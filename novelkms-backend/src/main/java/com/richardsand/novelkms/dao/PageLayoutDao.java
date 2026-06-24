package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.PageLayout;
import com.richardsand.novelkms.model.PageLayoutDefaults;

/**
 * Scoped page layout: {@code BOOK -> PROJECT -> SYSTEM}. Typed-column analogue of
 * {@link EditorSettingsDao}: one lazily-seeded SYSTEM row, one PROJECT row per
 * project, one BOOK row per book; PROJECT/BOOK rows are copy-on-write overrides
 * that can be deleted to fall back to the next level. Page layout affects
 * export/preview only.
 */
public class PageLayoutDao {

    private final BasicDataSource ds;

    public PageLayoutDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private static final String COLS =
            "id, scope, project_id, book_id, page_layout_enabled, page_size_preset,"
            + " page_width_in, page_height_in, page_margin_top_in, page_margin_bottom_in,"
            + " page_margin_inner_in, page_margin_outer_in, created_at, updated_at";

    private PageLayout map(ResultSet r) throws SQLException {
        return PageLayout.builder()
                .id(r.getObject("id", UUID.class))
                .scope(r.getString("scope"))
                .projectId(r.getObject("project_id", UUID.class))
                .bookId(r.getObject("book_id", UUID.class))
                .pageLayoutEnabled(r.getBoolean("page_layout_enabled"))
                .pageSizePreset(r.getString("page_size_preset"))
                .pageWidthIn(nullableDouble(r, "page_width_in"))
                .pageHeightIn(nullableDouble(r, "page_height_in"))
                .pageMarginTopIn(nullableDouble(r, "page_margin_top_in"))
                .pageMarginBottomIn(nullableDouble(r, "page_margin_bottom_in"))
                .pageMarginInnerIn(nullableDouble(r, "page_margin_inner_in"))
                .pageMarginOuterIn(nullableDouble(r, "page_margin_outer_in"))
                .createdAt(r.getTimestamp("created_at").toInstant())
                .updatedAt(r.getTimestamp("updated_at").toInstant())
                .build();
    }

    private Optional<PageLayout> one(String where, Object... args) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT " + COLS + " FROM page_layout WHERE " + where)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        }
    }

    private PageLayout insert(String scope, UUID project, UUID book, PageLayout v) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "INSERT INTO page_layout (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            p.setObject(1, id);
            p.setString(2, scope);
            p.setObject(3, project);
            p.setObject(4, book);
            p.setBoolean(5, v.isPageLayoutEnabled());
            p.setString(6, v.getPageSizePreset() != null ? v.getPageSizePreset() : "LETTER");
            setNullableDouble(p, 7, v.getPageWidthIn());
            setNullableDouble(p, 8, v.getPageHeightIn());
            p.setDouble(9, orDefault(v.getPageMarginTopIn(), PageLayoutDefaults.DEFAULT_MARGIN_TOP));
            p.setDouble(10, orDefault(v.getPageMarginBottomIn(), PageLayoutDefaults.DEFAULT_MARGIN_BOTTOM));
            p.setDouble(11, orDefault(v.getPageMarginInnerIn(), PageLayoutDefaults.DEFAULT_MARGIN_INNER));
            p.setDouble(12, orDefault(v.getPageMarginOuterIn(), PageLayoutDefaults.DEFAULT_MARGIN_OUTER));
            p.setTimestamp(13, Timestamp.from(now));
            p.setTimestamp(14, Timestamp.from(now));
            p.executeUpdate();
        }
        return one("id=?", id).orElseThrow();
    }

    private PageLayout update(UUID id, PageLayout v) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "UPDATE page_layout SET page_layout_enabled=?, page_size_preset=?,"
                        + " page_width_in=?, page_height_in=?, page_margin_top_in=?, page_margin_bottom_in=?,"
                        + " page_margin_inner_in=?, page_margin_outer_in=?, updated_at=? WHERE id=?")) {
            p.setBoolean(1, v.isPageLayoutEnabled());
            p.setString(2, v.getPageSizePreset() != null ? v.getPageSizePreset() : "LETTER");
            setNullableDouble(p, 3, v.getPageWidthIn());
            setNullableDouble(p, 4, v.getPageHeightIn());
            p.setDouble(5, orDefault(v.getPageMarginTopIn(), PageLayoutDefaults.DEFAULT_MARGIN_TOP));
            p.setDouble(6, orDefault(v.getPageMarginBottomIn(), PageLayoutDefaults.DEFAULT_MARGIN_BOTTOM));
            p.setDouble(7, orDefault(v.getPageMarginInnerIn(), PageLayoutDefaults.DEFAULT_MARGIN_INNER));
            p.setDouble(8, orDefault(v.getPageMarginOuterIn(), PageLayoutDefaults.DEFAULT_MARGIN_OUTER));
            p.setTimestamp(9, Timestamp.from(Instant.now()));
            p.setObject(10, id);
            p.executeUpdate();
        }
        return one("id=?", id).orElseThrow();
    }

    private boolean del(String where, Object arg) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM page_layout WHERE " + where)) {
            p.setObject(1, arg);
            return p.executeUpdate() > 0;
        }
    }

    // ── SYSTEM (factory default, lazily seeded) ───────────────────────────────

    public PageLayout system() throws SQLException {
        Optional<PageLayout> x = one("scope='SYSTEM' AND project_id IS NULL AND book_id IS NULL");
        return x.isPresent() ? x.get() : insert("SYSTEM", null, null, PageLayoutDefaults.defaults());
    }

    // ── PROJECT override ──────────────────────────────────────────────────────

    public Optional<PageLayout> project(UUID projectId) throws SQLException {
        return one("scope='PROJECT' AND project_id=?", projectId);
    }

    /** Resolved project-level layout: PROJECT override, else SYSTEM. */
    public PageLayout resolveProject(UUID projectId) throws SQLException {
        Optional<PageLayout> x = project(projectId);
        return x.isPresent() ? x.get() : system();
    }

    public PageLayout upsertProject(UUID projectId, PageLayout v) throws SQLException {
        Optional<PageLayout> x = project(projectId);
        return x.isPresent() ? update(x.get().getId(), v) : insert("PROJECT", projectId, null, v);
    }

    public boolean deleteProject(UUID projectId) throws SQLException {
        return del("scope='PROJECT' AND project_id=?", projectId);
    }

    // ── BOOK override ─────────────────────────────────────────────────────────

    public Optional<PageLayout> book(UUID bookId) throws SQLException {
        return one("scope='BOOK' AND book_id=?", bookId);
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

    /** Resolved book-level layout: BOOK override, else PROJECT (book's project), else SYSTEM. */
    public PageLayout resolveBook(UUID bookId) throws SQLException {
        Optional<PageLayout> x = book(bookId);
        if (x.isPresent()) return x.get();
        UUID projectId = bookProjectId(bookId);
        return projectId != null ? resolveProject(projectId) : system();
    }

    public PageLayout upsertBook(UUID bookId, PageLayout v) throws SQLException {
        Optional<PageLayout> x = book(bookId);
        return x.isPresent() ? update(x.get().getId(), v) : insert("BOOK", null, bookId, v);
    }

    public boolean deleteBook(UUID bookId) throws SQLException {
        return del("scope='BOOK' AND book_id=?", bookId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double orDefault(Double v, double fallback) {
        return v != null ? v : fallback;
    }

    /** DECIMAL columns may arrive as BigDecimal; read portably as Double. */
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        return Double.valueOf(value.toString());
    }

    private static void setNullableDouble(PreparedStatement p, int idx, Double v) throws SQLException {
        if (v == null) p.setNull(idx, Types.DECIMAL);
        else p.setObject(idx, v);
    }
}
