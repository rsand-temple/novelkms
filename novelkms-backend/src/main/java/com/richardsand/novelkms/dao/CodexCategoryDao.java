package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.CodexCategory;

/**
 * Read access to the master list of codex categories. The table is a lookup /
 * reference table seeded by migration; this DAO is read-only for now (editing
 * the list is a future feature).
 */
public class CodexCategoryDao {

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
                .build();
    }

    public List<CodexCategory> findAll() throws SQLException {
        return query("SELECT category_key, label, display_order, icon, is_default "
                + "FROM codex_category ORDER BY display_order, label");
    }

    public List<CodexCategory> findDefaults() throws SQLException {
        return query("SELECT category_key, label, display_order, icon, is_default "
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
