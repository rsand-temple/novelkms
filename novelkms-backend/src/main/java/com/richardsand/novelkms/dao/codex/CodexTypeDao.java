package com.richardsand.novelkms.dao.codex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexType;

/**
 * Read access to a Codex Type as a single assembled read model
 * ({@link CodexType}): the category chapter row's header (id, name, system key,
 * description) joined with its active {@code codex_type_field} rows.
 *
 * <p>This DAO reads the {@code chapter} table directly for the Type header
 * rather than going through {@code ChapterDao}. That is deliberate:
 * {@code ChapterDao} produces a manuscript-oriented {@code Chapter} projection
 * that intentionally omits several codex-only columns (it never selected
 * {@code codex_type_description}). A Type is a distinct read model with its own
 * shape, so it gets its own purpose-built projection here instead of widening
 * the shared manuscript row mapper. The header query is guarded to live codex
 * chapters ({@code codex_id IS NOT NULL AND deleted_at IS NULL}), so a
 * manuscript chapter id, a plain codex chapter, a trashed Type, or an unknown id
 * all resolve to empty (the resource returns 404).
 *
 * <p>Read-only in E2. The type-editor write path (create Type, edit name /
 * description) lands in E4.
 */
public class CodexTypeDao {

    private static final String HEADER_SELECT =
            "SELECT id, title, codex_category, codex_type_description "
                    + "FROM chapter "
                    + "WHERE id = ? AND codex_id IS NOT NULL AND deleted_at IS NULL";

    private final BasicDataSource    ds;
    private final CodexTypeFieldDao  fieldDao;

    public CodexTypeDao(BasicDataSource ds, CodexTypeFieldDao fieldDao) {
        this.ds = ds;
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
}
