package com.richardsand.novelkms.dao.codex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexFieldUsage;
import com.richardsand.novelkms.util.CodexFieldKeys;

/**
 * Read and write access to the normalized per-instance field definitions in
 * {@code codex_type_field}. Each row is one field of a Codex Type (a category
 * chapter row); {@code chapter_id} points at that Type. This table is the
 * source of truth for a Type's schema — it replaces the system-global
 * {@code codex_category.field_schema} JSON that {@link CodexCategoryDao} still
 * serves only for seeding and AI-promotion mapping after the E3 cutover.
 *
 * <p>Rows map to {@link CodexField} verbatim ({@code field_key -> key},
 * {@code input_type -> type}, {@code feeds_ai -> feedsAi}) so the entry form and
 * AI reference assembly consume the same typed structure they do today. The
 * {@code options} column is a JSON array for SELECT fields and NULL otherwise;
 * it is parsed here into a {@code List<String>}, failing soft (null) on a blank
 * or malformed value exactly as the categories lookup does for its schema JSON.
 *
 * <p><b>Field identity across the write API is the immutable {@code field_key}</b>,
 * not the row id. Keys are unique within a Type (the {@code uq_codex_type_field_key}
 * index), never regenerated, and already the value the client holds via
 * {@link CodexField#getKey()} — so update / reorder address fields by key. Every
 * write is guarded by {@code chapter_id = ?}, so a key that belongs to a
 * different Type simply matches no row (the same isolation pattern
 * {@code ChapterDao.reorderInCodex} uses with its {@code codex_id} guard); the
 * caller can only reach a Type it already owns because {@code typeId} is a
 * tenant-authorized path segment.
 *
 * <p>Write methods here (add / update / reorder) are the E4 type-editor write
 * path. Non-destructive soft-remove / restore ({@code deleted_at}) is E6:
 * {@link #softRemoveField} hides a field from the form while its stored values
 * survive in {@code structured_data}, and {@link #restoreField} re-shows it in
 * its original slot. {@link #updateField} still refuses to touch a soft-removed
 * row (it must be restored first), and {@link #findUsage} exposes every field
 * (active and removed) with its removed state for the editor's "Removed fields"
 * area.
 */
public class CodexTypeFieldDao {

    private static final Logger                      logger      = LoggerFactory.getLogger(CodexTypeFieldDao.class);
    private static final ObjectMapper                MAPPER      = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final String SELECT_COLUMNS =
            "SELECT field_key, label, input_type, options, help, feeds_ai, display_order "
                    + "FROM codex_type_field ";

    private final BasicDataSource ds;

    public CodexTypeFieldDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------------------------
    // Reads (E2)
    // -------------------------------------------------------------------------

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

    /**
     * A single active field of a Type by its immutable key, or empty if no
     * active field with that key belongs to the Type. Used to echo the persisted
     * row back after a write.
     */
    public Optional<CodexField> findField(UUID chapterId, String fieldKey) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(SELECT_COLUMNS
                        + "WHERE chapter_id = ? AND field_key = ? AND deleted_at IS NULL")) {
            ps.setObject(1, chapterId);
            ps.setString(2, fieldKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * All fields of a Type (active and soft-removed) in display order, each as a
     * {@link CodexFieldUsage} carrying its {@code removed} state; {@code
     * entryCount} is left 0 here — the usage service overlays the scene-derived
     * counts. Backs the type editor's "Removed fields" area and the pre-removal
     * warning (E6).
     */
    public List<CodexFieldUsage> findUsage(UUID chapterId) throws SQLException {
        String sql = "SELECT field_key, label, input_type, options, help, feeds_ai, "
                + "display_order, deleted_at "
                + "FROM codex_type_field WHERE chapter_id = ? "
                + "ORDER BY display_order, field_key";
        List<CodexFieldUsage> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(CodexFieldUsage.builder()
                            .key(rs.getString("field_key"))
                            .label(rs.getString("label"))
                            .type(rs.getString("input_type"))
                            .options(parseOptions(rs.getString("options")))
                            .help(rs.getString("help"))
                            .feedsAi(rs.getBoolean("feeds_ai"))
                            .removed(rs.getTimestamp("deleted_at") != null)
                            .entryCount(0)
                            .build());
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Writes (E4)
    // -------------------------------------------------------------------------

    /**
     * Adds a new field to a Type. A fresh immutable key is generated from the
     * label (unique across all of the Type's fields, active or soft-removed) and
     * the field is appended after the current highest {@code display_order}.
     * {@code options} is persisted only for SELECT fields; for text fields it is
     * stored NULL regardless of what was passed. Returns the created field
     * (carrying its generated key).
     */
    public CodexField addField(UUID chapterId, String label, String inputType,
            List<String> options, String help, boolean feedsAi) throws SQLException {
        String  optionsJson = optionsJson(inputType, options);
        UUID    id          = UUID.randomUUID();
        Instant now         = Instant.now();
        String  insert = "INSERT INTO codex_type_field "
                + "(id, chapter_id, field_key, label, input_type, options, help, feeds_ai, display_order, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String key;
        int    order;
        try (Connection c = ds.getConnection()) {
            key   = CodexFieldKeys.generate(label, existingKeys(c, chapterId));
            order = nextDisplayOrder(c, chapterId);
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setObject(1, id);
                ps.setObject(2, chapterId);
                ps.setString(3, key);
                ps.setString(4, label);
                ps.setString(5, inputType);
                ps.setString(6, optionsJson);
                ps.setString(7, help);
                ps.setBoolean(8, feedsAi);
                ps.setInt(9, order);
                ps.setTimestamp(10, Timestamp.from(now));
                ps.setTimestamp(11, Timestamp.from(now));
                ps.executeUpdate();
            }
        }

        return CodexField.builder()
                .key(key)
                .label(label)
                .type(inputType)
                .options(parseOptions(optionsJson))
                .help(help)
                .feedsAi(feedsAi)
                .build();
    }

    /**
     * Updates a field's presentation (label, input type, options, help,
     * feeds-AI) by its immutable key. <b>The key is never touched</b>, so every
     * stored {@code structured_data} value keeps resolving. {@code options} is
     * persisted only for SELECT; switching to a text type clears any prior
     * options so they cannot resurface on a later switch back. Only an active
     * field is editable (a soft-removed field must be restored first, in E6).
     * Returns the updated field, or empty if no active field with that key
     * belongs to the Type (the resource maps empty to 404).
     */
    public Optional<CodexField> updateField(UUID chapterId, String fieldKey, String label,
            String inputType, List<String> options, String help, boolean feedsAi) throws SQLException {
        String optionsJson = optionsJson(inputType, options);
        String sql = "UPDATE codex_type_field "
                + "SET label = ?, input_type = ?, options = ?, help = ?, feeds_ai = ?, updated_at = ? "
                + "WHERE chapter_id = ? AND field_key = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, label);
            ps.setString(2, inputType);
            ps.setString(3, optionsJson);
            ps.setString(4, help);
            ps.setBoolean(5, feedsAi);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.setObject(7, chapterId);
            ps.setString(8, fieldKey);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findField(chapterId, fieldKey);
    }

    /**
     * Assigns {@code display_order} 0..n-1 to the given field keys in the order
     * supplied. The {@code chapter_id} guard confines the change to fields of
     * this Type — a key that belongs to another Type matches nothing and is a
     * silent no-op. Only reorders keys present in the list; keys omitted keep
     * their current order (callers send the full active set).
     */
    public void reorderFields(UUID chapterId, List<String> fieldKeys) throws SQLException {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return;
        }
        String sql = "UPDATE codex_type_field SET display_order = ?, updated_at = ? "
                + "WHERE chapter_id = ? AND field_key = ?";
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                Instant now = Instant.now();
                for (int i = 0; i < fieldKeys.size(); i++) {
                    ps.setInt(1, i);
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, chapterId);
                    ps.setString(4, fieldKeys.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Soft-remove / restore (E6)
    // -------------------------------------------------------------------------

    /**
     * Soft-removes an active field: stamps {@code deleted_at} so the field drops
     * off the entry form while its stored {@code structured_data} values are left
     * completely untouched and can be restored later. The {@code deleted_at IS
     * NULL} guard makes this a no-op on an already-removed field (and, with the
     * {@code chapter_id} guard, on a key belonging to another Type). Returns true
     * when a row was removed, false otherwise (the resource maps false to 404).
     */
    public boolean softRemoveField(UUID chapterId, String fieldKey) throws SQLException {
        String sql = "UPDATE codex_type_field SET deleted_at = ?, updated_at = ? "
                + "WHERE chapter_id = ? AND field_key = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            Instant now = Instant.now();
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setObject(3, chapterId);
            ps.setString(4, fieldKey);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Restores a soft-removed field by clearing {@code deleted_at}. Its original
     * {@code display_order} is preserved, so it re-appears in its former slot
     * among the active fields, and its stored values become visible again with
     * it. The {@code deleted_at IS NOT NULL} guard means restoring an already
     * active (or unknown) field matches nothing → empty (→ 404). Returns the now
     * active field. A key collision on restore is impossible: {@link #addField}
     * generates new keys against the Type's full key set including removed rows,
     * so a removed key can never be re-issued while it waits to be restored.
     */
    public Optional<CodexField> restoreField(UUID chapterId, String fieldKey) throws SQLException {
        String sql = "UPDATE codex_type_field SET deleted_at = NULL, updated_at = ? "
                + "WHERE chapter_id = ? AND field_key = ? AND deleted_at IS NOT NULL";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setObject(2, chapterId);
            ps.setString(3, fieldKey);
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findField(chapterId, fieldKey);
    }

    // -------------------------------------------------------------------------
    // Seeding (E7)
    // -------------------------------------------------------------------------

    /**
     * Stamps a freshly-created seeded Type with its own copy of a default
     * category's field set, taken verbatim from the {@code codex_category}
     * master schema. Unlike {@link #addField} (the author path, which generates a
     * fresh {@code slug_4hex} key), the keys here are copied <b>exactly</b> from
     * the source schema ({@code role}, {@code age}, …) so every instance of a
     * seeded type — the V42-backfilled ones and every newly-created codex —
     * shares one key set. That shared key set is what AI-promotion mapping (E8)
     * and Decision 3's immutable-key rule depend on, and it means a CHARACTER
     * entry authored in one project resolves its {@code structured_data} against
     * a CHARACTER type in any other.
     *
     * <p>{@code display_order} is the field's index in {@code fields}, mirroring
     * the array order the migration preserved from V33. {@code options} is
     * serialized only for SELECT fields (text fields store NULL). All rows for
     * the Type are inserted in one batched statement inside a single
     * transaction, so a Type's schema is stamped all-or-nothing.
     *
     * <p>This is a seed-time operation: it assumes {@code chapterId} is a
     * brand-new Type with no existing fields, so it neither checks for key
     * collisions nor consults the current max order. A null or empty field list
     * is a no-op (schema-less default categories seed no field rows).
     */
    public void seedFields(UUID chapterId, List<CodexField> fields) throws SQLException {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        String insert = "INSERT INTO codex_type_field "
                + "(id, chapter_id, field_key, label, input_type, options, help, feeds_ai, display_order, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                Instant now = Instant.now();
                for (int i = 0; i < fields.size(); i++) {
                    CodexField f = fields.get(i);
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, chapterId);
                    ps.setString(3, f.getKey());
                    ps.setString(4, f.getLabel());
                    ps.setString(5, f.getType());
                    ps.setString(6, optionsJson(f.getType(), f.getOptions()));
                    ps.setString(7, f.getHelp());
                    ps.setBoolean(8, f.isFeedsAi());
                    ps.setInt(9, i);
                    ps.setTimestamp(10, Timestamp.from(now));
                    ps.setTimestamp(11, Timestamp.from(now));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
     * Every key currently used by a Type, active or soft-removed. The uniqueness
     * index spans deleted rows, so a new key must avoid all of them — otherwise
     * restoring an old field (E6) could collide with a freshly added one.
     */
    private Set<String> existingKeys(Connection c, UUID chapterId) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT field_key FROM codex_type_field WHERE chapter_id = ?")) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        }
        return keys;
    }

    /**
     * Next {@code display_order} for a Type: one past the current maximum across
     * all its rows (including soft-removed), so a new field never shares an order
     * slot with a field that might later be restored.
     */
    private int nextDisplayOrder(Connection c, UUID chapterId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(display_order), -1) + 1 FROM codex_type_field WHERE chapter_id = ?")) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Serializes SELECT options to a JSON array, or returns null for a non-SELECT
     * field or an absent/empty option list. A SELECT with no options yet is still
     * a SELECT (distinguished by {@code input_type}); it simply renders an empty
     * dropdown until the author adds choices.
     */
    private String optionsJson(String inputType, List<String> options) {
        if (!"SELECT".equals(inputType) || options == null || options.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(options);
        } catch (Exception e) {
            logger.warn("Ignoring unserializable codex_type_field.options: {}", e.getMessage());
            return null;
        }
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
