package com.richardsand.novelkms.dao.codex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.model.codex.CodexCategory;
import com.richardsand.novelkms.model.codex.CodexSchema;

/**
 * Read access to the master list of codex categories. The table is a lookup /
 * reference table seeded by migration; this DAO is read-only for now (editing
 * the list is a future feature).
 *
 * <p>Each row may carry an optional {@code schema} JSON column defining the
 * structured fields for that category's entries; it is parsed into a
 * {@link CodexSchema} here so both the categories endpoint and AI reference
 * assembly work with a typed structure rather than a raw string. A null or
 * malformed schema maps to a null {@code CodexSchema} (the category is then a
 * plain title-plus-body category).
 */
public class CodexCategoryDao {

    private static final Logger       logger = LoggerFactory.getLogger(CodexCategoryDao.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BasicDataSource ds;

    public CodexCategoryDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private CodexCategory map(ResultSet rs) throws SQLException {
        return CodexCategory.builder()
                .categoryKey(rs.getString("category_key"))
                .label(rs.getString("label"))
                .displayOrder(rs.getInt("display_order"))
                .icon(rs.getString("icon"))
                .isDefault(rs.getBoolean("is_default"))
                .schema(parseSchema(rs.getString("field_schema")))
                .build();
    }

    /**
     * Parses the stored schema JSON into a {@link CodexSchema}. Returns null for
     * a blank column or unparseable JSON — a category with no usable schema is
     * simply an unstructured (title-plus-body) category, so this fails soft
     * rather than breaking the categories lookup.
     */
    private CodexSchema parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, CodexSchema.class);
        } catch (Exception e) {
            logger.warn("Ignoring malformed codex_category.schema JSON: {}", e.getMessage());
            return null;
        }
    }

    public List<CodexCategory> findAll() throws SQLException {
        return query("SELECT category_key, label, display_order, icon, is_default, field_schema "
                + "FROM codex_category ORDER BY display_order, label");
    }

    public List<CodexCategory> findDefaults() throws SQLException {
        return query("SELECT category_key, label, display_order, icon, is_default, field_schema "
                + "FROM codex_category WHERE is_default = TRUE ORDER BY display_order, label");
    }

    private List<CodexCategory> query(String sql) throws SQLException {
        List<CodexCategory> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }
}
