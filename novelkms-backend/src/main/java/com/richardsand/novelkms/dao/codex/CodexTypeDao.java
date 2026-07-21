package com.richardsand.novelkms.dao.codex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexType;

/**
 * Read and write access to a Codex Type as a single assembled model
 * ({@link CodexType}): the category chapter row's header (id, name, system key,
 * description) joined with its {@code codex_type_field} rows.
 *
 * <p>This DAO reads and writes the {@code chapter} table's Type header directly
 * rather than going through {@code ChapterDao.map()}. That is deliberate:
 * {@code ChapterDao} produces a manuscript-oriented {@code Chapter} projection
 * that intentionally omits several codex-only columns (it never selected
 * {@code codex_type_description}). A Type is a distinct model with its own
 * shape, so it gets its own purpose-built projection here instead of widening
 * the shared manuscript row mapper. The header query is guarded to live codex
 * chapters ({@code codex_id IS NOT NULL AND deleted_at IS NULL}), so a
 * manuscript chapter id, a plain codex chapter, a trashed Type, or an unknown id
 * all resolve to empty (the resource returns 404).
 *
 * <p>Creating a Type still <em>reuses</em> {@link ChapterDao#createCodexChapter}
 * for the row insert, so codex-scoped {@code display_order} allocation lives in
 * exactly one place; this DAO then stamps the optional description. Header
 * <em>reads</em> and description writes stay here so all Type-header semantics
 * are co-located.
 *
 * <p>The type-editor header write path (create Type with a NULL
 * {@code codex_category}, rename, edit description) is E4. Field-level writes
 * live in {@link CodexTypeFieldDao}.
 */
public class CodexTypeDao {

    private static final String HEADER_SELECT =
            "SELECT id, title, codex_category, codex_type_description "
                    + "FROM chapter "
                    + "WHERE id = ? AND codex_id IS NOT NULL AND deleted_at IS NULL";

    private final BasicDataSource   ds;
    private final ChapterDao        chapterDao;
    private final CodexTypeFieldDao fieldDao;

    public CodexTypeDao(BasicDataSource ds, ChapterDao chapterDao, CodexTypeFieldDao fieldDao) {
        this.ds = ds;
        this.chapterDao = chapterDao;
        this.fieldDao = fieldDao;
    }

    /**
     * Assembles the {@link CodexType} for a Type (category chapter) id, or empty
     * if the id is not a live codex Type. The header is read in a fully closed
     * statement scope before the field read opens its own connection, so no two
     * pooled statements are held open at once (DBCP2 statement-pooling rule).
     */
    public Optional<CodexType> findType(UUID typeId) throws SQLException {
        UUID   id;
        String name;
        String systemKey;
        String description;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(HEADER_SELECT)) {
            ps.setObject(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                id          = rs.getObject("id", UUID.class);
                name        = rs.getString("title");
                systemKey   = rs.getString("codex_category");
                description = rs.getString("codex_type_description");
            }
        }

        List<CodexField> fields = fieldDao.findActiveByType(typeId);

        return Optional.of(CodexType.builder()
                .id(id)
                .name(name)
                .description(description)
                .systemKey(systemKey)
                .fields(fields)
                .build());
    }

    /**
     * Creates an author-defined Type inside a codex: a category chapter row with
     * a <b>NULL {@code codex_category}</b> (only seeded types carry a system key
     * for AI-promotion mapping) and no fields yet. The row and its codex-scoped
     * {@code display_order} are created via {@link ChapterDao#createCodexChapter};
     * an optional description is then stamped on the same row. Returns the
     * assembled (field-less) {@link CodexType}.
     */
    public CodexType createType(UUID codexId, String name, String description) throws SQLException {
        Chapter row = chapterDao.createCodexChapter(codexId, null, name);
        if (description != null) {
            updateDescription(row.getId(), description);
        }
        return CodexType.builder()
                .id(row.getId())
                .name(name)
                .description(description)
                .systemKey(null)
                .fields(List.of())
                .build();
    }

    /**
     * Renames a Type and/or edits its description. {@code codex_category} (the
     * system key) is never touched, so renaming leaves AI-promotion mapping
     * intact. Guarded to live codex Types, so a manuscript chapter, a trashed
     * Type, or an unknown id updates nothing and yields empty (→ 404). Returns
     * the freshly assembled {@link CodexType} echoing all persisted header fields
     * plus current active fields.
     */
    public Optional<CodexType> updateHeader(UUID typeId, String name, String description) throws SQLException {
        String sql = "UPDATE chapter SET title = ?, codex_type_description = ?, updated_at = ? "
                + "WHERE id = ? AND codex_id IS NOT NULL AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, typeId);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findType(typeId);
    }

    /**
     * Stamps the optional per-Type description on the category chapter row.
     * Separate closed statement scope from the row insert (no shared pooled
     * statements).
     */
    private void updateDescription(UUID typeId, String description) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE chapter SET codex_type_description = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, description);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, typeId);
            ps.executeUpdate();
        }
    }
}
