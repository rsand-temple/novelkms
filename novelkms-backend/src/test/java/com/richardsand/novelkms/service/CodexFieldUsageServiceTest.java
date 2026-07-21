package com.richardsand.novelkms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.CodexFieldUsage;

/**
 * Verifies {@link CodexFieldUsageService}: that the type-editor usage view lists
 * every field of a Type (active and soft-removed, in display order) and annotates
 * each with the number of entries that currently hold a non-blank value for it.
 *
 * <p>The counting runs in Java over each entry's {@code structured_data} JSON, so
 * these tests pin the "contains information" definition (non-null, non-blank after
 * trim), confirm that soft-removed fields still report the values preserved under
 * them, and confirm that a malformed blob is skipped rather than fatal.
 */
class CodexFieldUsageServiceTest extends NovelKmsTestBase {

    private static final CodexTypeFieldDao      fieldDao = new CodexTypeFieldDao(ds);
    private static final CodexDao               codexDao = new CodexDao(ds);
    private static final CodexFieldUsageService service  = new CodexFieldUsageService(fieldDao, sceneDao);

    private UUID codexId;
    private UUID typeId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = createTestProject("Test Project", null);
        var     codex   = codexDao.createForProject(project.getId(), "Codex");
        codexId = codex.getId();

        Chapter type = chapterDao.createCodexChapter(codexId, "CHARACTER", "Characters");
        typeId = type.getId();

        // Two active fields and one soft-removed field whose values must survive.
        insertField(typeId, "role", "Role", "SELECT",
                "[\"Protagonist\",\"Antagonist\"]", null, true, 0, false);
        insertField(typeId, "age", "Age", "SHORT_TEXT", null, null, true, 1, false);
        insertField(typeId, "notes", "Notes", "LONG_TEXT", null, null, false, 2, true);
    }

    @Test
    void usage_countsNonBlankValuesPerKey_acrossActiveAndRemovedFields() throws SQLException {
        createEntry("Frodo", "{\"role\":\"Protagonist\",\"age\":\"\"}");            // role only (age blank)
        createEntry("Sam",   "{\"role\":\"Antagonist\",\"age\":\"42\",\"notes\":\"secret\"}"); // role, age, notes
        createEntry("Merry", null);                                                  // no data at all

        List<CodexFieldUsage> usage = service.usage(typeId);

        assertEquals(3, usage.size(), "usage lists active and soft-removed fields in order");

        CodexFieldUsage role = find(usage, "role");
        assertFalse(role.isRemoved());
        assertEquals(2, role.getEntryCount());

        CodexFieldUsage age = find(usage, "age");
        assertEquals(1, age.getEntryCount(), "an empty-string value is not counted");

        CodexFieldUsage notes = find(usage, "notes");
        assertTrue(notes.isRemoved(), "notes remains soft-removed");
        assertEquals(1, notes.getEntryCount(),
                "values under a removed field are preserved and still counted");
    }

    @Test
    void usage_whitespaceOnlyValue_isNotCounted() throws SQLException {
        createEntry("Pippin", "{\"age\":\"   \"}");
        assertEquals(0, find(service.usage(typeId), "age").getEntryCount());
    }

    @Test
    void usage_unparseableStructuredData_isSkipped_notFatal() throws SQLException {
        createEntry("Broken", "{ this is not json");
        createEntry("Valid",  "{\"role\":\"Protagonist\"}");

        List<CodexFieldUsage> usage = service.usage(typeId);
        assertEquals(1, find(usage, "role").getEntryCount(),
                "the valid entry counts; the malformed one is ignored");
    }

    @Test
    void usage_fieldlessType_returnsEmpty() throws SQLException {
        Chapter empty = chapterDao.createCodexChapter(codexId, null, "Empty Type");
        assertTrue(service.usage(empty.getId()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CodexFieldUsage find(List<CodexFieldUsage> usage, String key) {
        return usage.stream().filter(byKey(key)).findFirst().orElseThrow();
    }

    private static Predicate<CodexFieldUsage> byKey(String key) {
        return u -> key.equals(u.getKey());
    }

    private Scene createEntry(String title, String structuredData) throws SQLException {
        Scene scene = sceneDao.create(typeId, title, null);
        if (structuredData != null) {
            sceneDao.saveStructuredData(scene.getId(), structuredData);
        }
        return scene;
    }

    private void insertField(UUID chapterId, String key, String label, String inputType,
            String optionsJson, String help, boolean feedsAi, int order, boolean deleted)
            throws SQLException {
        String sql = "INSERT INTO codex_type_field "
                + "(id, chapter_id, field_key, label, input_type, options, help, feeds_ai, display_order, deleted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, chapterId);
            ps.setString(3, key);
            ps.setString(4, label);
            ps.setString(5, inputType);
            ps.setString(6, optionsJson);
            ps.setString(7, help);
            ps.setBoolean(8, feedsAi);
            ps.setInt(9, order);
            if (deleted) {
                ps.setTimestamp(10, Timestamp.from(Instant.now()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }
            ps.executeUpdate();
        }
    }
}
