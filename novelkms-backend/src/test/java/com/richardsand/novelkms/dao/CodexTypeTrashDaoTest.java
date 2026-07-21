package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;

/**
 * E7 Trash-carry contract for Codex Types. A Type is a category chapter row that
 * owns its normalized fields in {@code codex_type_field} (FK to the chapter,
 * {@code ON DELETE CASCADE}) and its entries as scene rows. This verifies the
 * interaction between soft-delete (Trash) and that cascade:
 *
 * <ul>
 *   <li><b>Trash</b> ({@link TrashDao#trashChapter}) stamps only the chapter
 *       root's {@code deleted_at}; it never touches {@code codex_type_field}, so
 *       a trashed Type keeps its field rows and its entry rows in place (entries
 *       are hidden transitively by live reads, not deleted).</li>
 *   <li><b>Restore</b> ({@link TrashDao#restoreChapter}) clears the flag and the
 *       untouched fields and entries become live again together.</li>
 *   <li><b>Purge</b> ({@link TrashDao#purgeChapter}) hard-deletes the chapter, so
 *       the {@code ON DELETE CASCADE} FK finally removes the field rows (and the
 *       entry scenes).</li>
 * </ul>
 *
 * <p>This is the confirmation the E7 handoff note calls for: fields must survive
 * while a Type sits in Trash and cascade only on a hard purge. It needs no new
 * production code — the existing Trash mechanics already satisfy it — so these
 * tests exist to lock the behavior in.
 */
class CodexTypeTrashDaoTest extends NovelKmsTestBase {

    private static final CodexDao          codexDao          = new CodexDao(ds);
    private static final CodexTypeFieldDao codexTypeFieldDao = new CodexTypeFieldDao(ds);

    private UUID typeId;
    private UUID entryId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = createTestProject("Test Project", null);
        Codex   codex   = codexDao.createForProject(project.getId(), "Codex");

        Chapter type = chapterDao.createCodexChapter(codex.getId(), "CHARACTER", "Characters");
        typeId = type.getId();

        // Two fields and one entry scene beneath the Type.
        insertField(typeId, "role", "Role", "SHORT_TEXT", 0);
        insertField(typeId, "age", "Age", "SHORT_TEXT", 1);
        Scene entry = sceneDao.create(typeId, "Frodo", null);
        entryId = entry.getId();
    }

    @Test
    void trashType_leavesFieldRowsAndEntryInPlace() throws SQLException {
        var trashed = trashDao.trashChapter(TEST_USER_ID, typeId);

        assertTrue(trashed.isPresent());
        assertEquals("CODEX_CATEGORY", trashed.get().getRootType());

        // The chapter row is soft-deleted (still present), and neither its field
        // rows nor its entry scene were touched.
        assertTrue(rawRowExists("chapter", typeId), "trashed Type row is soft-deleted, not removed");
        assertEquals(2, countFields(typeId), "field rows survive a trashed Type");
        assertTrue(rawRowExists("scene", entryId), "entry scene survives a trashed Type");
    }

    @Test
    void restoreType_bringsBackFieldsAndEntryTogether() throws SQLException {
        trashDao.trashChapter(TEST_USER_ID, typeId);
        boolean restored = trashDao.restoreChapter(typeId, "Characters", 0);

        assertTrue(restored, "restore clears the chapter's deleted flag");
        // Fields were never stamped, so they read live again with the chapter,
        // and the entry scene is live once more.
        assertEquals(2, codexTypeFieldDao.findActiveByType(typeId).size(),
                "fields are live again after the Type is restored");
        assertEquals(2, countFields(typeId), "no field rows were lost across the trash/restore cycle");
        assertTrue(rawRowExists("scene", entryId), "entry scene comes back with the restored Type");
    }

    @Test
    void purgeType_cascadesFieldRowsAndEntries() throws SQLException {
        trashDao.trashChapter(TEST_USER_ID, typeId);
        trashDao.purgeChapter(typeId);

        assertFalse(rawRowExists("chapter", typeId), "purge hard-deletes the Type chapter");
        assertEquals(0, countFields(typeId), "field rows cascade away only on a hard purge");
        assertFalse(rawRowExists("scene", entryId), "entry scenes cascade away on a hard purge");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void insertField(UUID chapterId, String key, String label, String inputType, int order)
            throws SQLException {
        String sql = "INSERT INTO codex_type_field "
                + "(id, chapter_id, field_key, label, input_type, feeds_ai, display_order) "
                + "VALUES (?, ?, ?, ?, ?, TRUE, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, chapterId);
            ps.setString(3, key);
            ps.setString(4, label);
            ps.setString(5, inputType);
            ps.setInt(6, order);
            ps.executeUpdate();
        }
    }

    /** Count of all field rows (active or soft-removed) for a Type. */
    private int countFields(UUID chapterId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM codex_type_field WHERE chapter_id = ?")) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean rawRowExists(String table, UUID id) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
