package com.richardsand.novelkms.dao.codex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.model.codex.CodexField;

/**
 * Read access to the normalized per-instance field definitions in
 * {@code codex_type_field}. Each row is one field of a Codex Type (a category
 * chapter row); {@code chapter_id} points at that Type. This table is the
 * source of truth for a Type's schema — it replaces the system-global
 * {@code codex_category.field_schema} JSON that {@link CodexCategoryDao} still
 * serves until phase E3 cuts the live read path over.
 *
 * <p>Rows map to {@link CodexField} verbatim ({@code field_key -> key},
 * {@code input_type -> type}, {@code feeds_ai -> feedsAi}) so the entry form and
 * AI reference assembly consume the same typed structure they do today. The
 * {@code options} column is a JSON array for SELECT fields and NULL otherwise;
 * it is parsed here into a {@code List<String>}, failing soft (null) on a blank
 * or malformed value exactly as the categories lookup does for its schema JSON.
 *
 * <p>This DAO is read-only in E2. Write methods (add / rename / reorder /
 * change-style / soft-remove / restore) arrive in E4 and E6.
 */
public class CodexTypeFieldDao {

    private static final Logger                   logger      = LoggerFactory.getLogger(CodexTypeFieldDao.class);
    private static final ObjectMapper             MAPPER      = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final String SELECT_COLUMNS =
            "SELECT field_key, label, input_type, options, help, feeds_ai, display_order "
                    + "FROM codex_type_field ";

    private final BasicDataSource ds;

    public CodexTypeFieldDao(BasicDataSource ds) {
        this.ds = ds;
    }

    /**
     * Active fields for a Type, in display order. Soft-removed fields
     * ({@code deleted_at} set) are excluded — this is what the entry form and AI
     * fill render.
     */
    public List<CodexField> findActiveByType(UUID chapterId) throws SQLException {
        return query(SELECT_COLUMNS
                + "WHERE chapter_id = ? AND deleted_at IS NULL "
                + "ORDER BY display_order, field_key", chapterId);
    }

    /**
     * All fields for a Type including soft-removed ones, in display order. Used
     * by the type editor's "Removed fields" area (E6); not shown on the entry
     * form.
     */
    public List<CodexField> findAllByType(UUID chapterId) throws SQLException {
        return query(SELECT_COLUMNS
                + "WHERE chapter_id = ? "
                + "ORDER BY display_order, field_key", chapterId);
    }

    private List<CodexField> query(String sql, UUID chapterId) throws SQLException {
        List<CodexField> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    private CodexField map(ResultSet rs) throws SQLException {
        return CodexField.builder()
                .key(rs.getString("field_key"))
                .label(rs.getString("label"))
                .type(rs.getString("input_type"))
                .options(parseOptions(rs.getString("options")))
                .help(rs.getString("help"))
                .feedsAi(rs.getBoolean("feeds_ai"))
                .build();
    }

    /**
     * Parses the stored SELECT options JSON array into a {@code List<String>}.
     * Returns null for a blank column (text fields) or unparseable JSON — a field
     * with no usable options is simply a non-SELECT field, so this fails soft
     * rather than breaking the read.
     */
    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            logger.warn("Ignoring malformed codex_type_field.options JSON: {}", e.getMessage());
            return null;
        }
    }
}
