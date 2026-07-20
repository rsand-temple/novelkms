package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexType;

/**
 * Verifies the E2 read path: {@link CodexTypeFieldDao} reads a Type's
 * normalized fields (active vs. all, SELECT options round-trip, feeds_ai) and
 * {@link CodexTypeDao} assembles the {@link CodexType} header from the category
 * chapter row.
 *
 * <p>V42's backfill only stamped fields onto category instances that existed at
 * migration time, so a chapter created in-test starts field-less; each test
 * seeds its own {@code codex_type_field} rows directly.
 */
class CodexTypeDaoTest extends NovelKmsTestBase {

    private static final CodexTypeFieldDao fieldDao = new CodexTypeFieldDao(ds);
    private static final CodexTypeDao      typeDao  = new CodexTypeDao(ds, fieldDao);
    private static final CodexDao          codexDao = new CodexDao(ds);

    private Project project;
    private Book    book;
    private UUID    characterTypeId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = projectDao.create("Test Project", null);
        book = bookDao.create(project.getId(), "Test Book", null, null, null);

        var codex = codexDao.createForProject(project.getId(), "Codex");
        Chapter type = chapterDao.createCodexChapter(codex.getId(), "CHARACTER", "Characters");
        characterTypeId = type.getId();

        // Representative field set (order deliberately out of insertion order to
        // prove ORDER BY display_order): a SELECT with options, a plain text
        // field, a private (feeds_ai = false) field, and a soft-removed field.
        insertField(characterTypeId, "role", "Role / Archetype", "SELECT",
                "[\"Protagonist\",\"Antagonist\",\"Mentor\"]", "Structural role.", true, 0, null);
        insertField(characterTypeId, "age", "Age", "SHORT_TEXT",
                null, "Age or apparent age.", true, 1, null);
        insertField(characterTypeId, "authorNotes", "Author notes (private)", "LONG_TEXT",
                null, "Never shared with the AI.", false, 2, null);
        insertField(characterTypeId, "removedField", "Removed", "SHORT_TEXT",
                null, null, true, 3, Instant.now());
    }

    // -------------------------------------------------------------------------
    // CodexTypeFieldDao
    // -------------------------------------------------------------------------

    @Test
    void findActiveByType_returnsActiveFieldsInOrder_excludesRemoved() throws SQLException {
        List<CodexField> active = fieldDao.findActiveByType(characterTypeId);

        assertEquals(3, active.size(), "soft-removed field must be excluded");
        assertEquals("role", active.get(0).getKey());
        assertEquals("age", active.get(1).getKey());
        assertEquals("authorNotes", active.get(2).getKey());
    }

    @Test
    void findActiveByType_roundTripsSelectOptions() throws SQLException {
        CodexField role = fieldDao.findActiveByType(characterTypeId).get(0);

        assertEquals("SELECT", role.getType());
        assertEquals(List.of("Protagonist", "Antagonist", "Mentor"), role.getOptions());
        assertEquals("Structural role.", role.getHelp());
    }

    @Test
    void findActiveByType_mapsFeedsAiAndNullOptions() throws SQLException {
        List<CodexField> active = fieldDao.findActiveByType(characterTypeId);

        CodexField age = active.get(1);
        assertTrue(age.isFeedsAi());
        assertNull(age.getOptions(), "text field has no options");

        CodexField notes = active.get(2);
        assertFalse(notes.isFeedsAi(), "private field must not feed the AI");
    }

    @Test
    void findAllByType_includesSoftRemoved() throws SQLException {
        List<CodexField> all = fieldDao.findAllByType(characterTypeId);

        assertEquals(4, all.size());
        assertEquals("removedField", all.get(3).getKey());
    }

    @Test
    void findActiveByType_unknownType_isEmpty() throws SQLException {
        assertTrue(fieldDao.findActiveByType(UUID.randomUUID()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // CodexTypeDao
    // -------------------------------------------------------------------------

    @Test
    void findType_assemblesHeaderAndActiveFields() throws SQLException {
        setDescription(characterTypeId, "People in the story.");

        Optional<CodexType> result = typeDao.findType(characterTypeId);

        assertTrue(result.isPresent());
        CodexType type = result.get();
        assertEquals(characterTypeId, type.getId());
        assertEquals("Characters", type.getName());
        assertEquals("CHARACTER", type.getSystemKey());
        assertEquals("People in the story.", type.getDescription());
        assertEquals(3, type.getFields().size(), "only active fields are served");
        assertEquals("role", type.getFields().get(0).getKey());
    }

    @Test
    void findType_nullDescriptionBeforeEditor() throws SQLException {
        // No description written yet (E4 writes it) — header still resolves.
        CodexType type = typeDao.findType(characterTypeId).orElseThrow();
        assertNull(type.getDescription());
    }

    @Test
    void findType_manuscriptChapter_isEmpty() throws SQLException {
        Chapter manuscript = chapterDao.create(book.getId(), null, "Chapter One", null, null);
        assertTrue(typeDao.findType(manuscript.getId()).isEmpty(),
                "a manuscript chapter is not a codex Type");
    }

    @Test
    void findType_unknownId_isEmpty() throws SQLException {
        assertTrue(typeDao.findType(UUID.randomUUID()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void insertField(UUID chapterId, String key, String label, String inputType,
            String optionsJson, String help, boolean feedsAi, int order, Instant deletedAt)
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
            if (deletedAt == null) {
                ps.setNull(10, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(10, Timestamp.from(deletedAt));
            }
            ps.executeUpdate();
        }
    }

    private void setDescription(UUID chapterId, String description) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE chapter SET codex_type_description = ? WHERE id = ?")) {
            ps.setString(1, description);
            ps.setObject(2, chapterId);
            ps.executeUpdate();
        }
    }
}
