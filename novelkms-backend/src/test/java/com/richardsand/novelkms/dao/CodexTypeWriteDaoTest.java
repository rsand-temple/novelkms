package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexType;

/**
 * Verifies the E4 type-editor write path: {@link CodexTypeDao} creating an
 * author Type and editing its header, and {@link CodexTypeFieldDao} adding,
 * updating, and reordering fields.
 *
 * <p>The load-bearing guarantees under test are Decision 3 (the generated
 * {@code field_key} is immutable and never regenerated on edit, so stored
 * {@code structured_data} values keep resolving) and the {@code chapter_id}
 * guard that isolates every field write to the Type it targets.
 */
class CodexTypeWriteDaoTest extends NovelKmsTestBase {

    private static final CodexTypeFieldDao fieldDao = new CodexTypeFieldDao(ds);
    private static final CodexTypeDao      typeDao  = new CodexTypeDao(ds, chapterDao, fieldDao);
    private static final CodexDao          codexDao = new CodexDao(ds);

    private UUID codexId;
    private UUID characterTypeId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = projectDao.create("Test Project", null);
        var     codex   = codexDao.createForProject(project.getId(), "Codex");
        codexId = codex.getId();

        Chapter type = chapterDao.createCodexChapter(codexId, "CHARACTER", "Characters");
        characterTypeId = type.getId();

        // Two active seed fields: a SELECT (order 0) and a text field (order 1).
        insertField(characterTypeId, "role", "Role", "SELECT",
                "[\"Protagonist\",\"Antagonist\"]", null, true, 0, false);
        insertField(characterTypeId, "age", "Age", "SHORT_TEXT", null, null, true, 1, false);
    }

    // -------------------------------------------------------------------------
    // CodexTypeDao: create / update header
    // -------------------------------------------------------------------------

    @Test
    void createType_makesAuthorTypeWithNullSystemKeyAndNoFields() throws SQLException {
        CodexType created = typeDao.createType(codexId, "Dragons", "The scaly cast.");

        assertNotNull(created.getId());
        assertEquals("Dragons", created.getName());
        assertEquals("The scaly cast.", created.getDescription());
        assertNull(created.getSystemKey(), "author type carries no system key");
        assertTrue(created.getFields().isEmpty());

        CodexType read = typeDao.findType(created.getId()).orElseThrow();
        assertEquals("Dragons", read.getName());
        assertNull(read.getSystemKey());
        assertEquals("The scaly cast.", read.getDescription());
    }

    @Test
    void createType_nullDescriptionIsPersistedNull() throws SQLException {
        CodexType created = typeDao.createType(codexId, "Places", null);
        assertNull(typeDao.findType(created.getId()).orElseThrow().getDescription());
    }

    @Test
    void updateHeader_renamesButKeepsSystemKey() throws SQLException {
        CodexType updated = typeDao.updateHeader(characterTypeId, "People", "Everyone who matters.")
                .orElseThrow();

        assertEquals("People", updated.getName());
        assertEquals("Everyone who matters.", updated.getDescription());
        assertEquals("CHARACTER", updated.getSystemKey(), "rename must not touch the system key");
        assertEquals(2, updated.getFields().size(), "active fields still served");
    }

    @Test
    void updateHeader_unknownId_isEmpty() throws SQLException {
        assertTrue(typeDao.updateHeader(UUID.randomUUID(), "Nope", null).isEmpty());
    }

    // -------------------------------------------------------------------------
    // CodexTypeFieldDao: add
    // -------------------------------------------------------------------------

    @Test
    void addField_generatesImmutableSlugHexKey_andAppends() throws SQLException {
        CodexField added = fieldDao.addField(characterTypeId, "Wing Span", "SHORT_TEXT", null, "Help.", true);

        assertTrue(added.getKey().matches("wingspan_[0-9a-f]{4}"),
                "unexpected generated key: " + added.getKey());
        assertEquals("SHORT_TEXT", added.getType());

        List<CodexField> active = fieldDao.findActiveByType(characterTypeId);
        assertEquals(3, active.size());
        assertEquals(added.getKey(), active.get(2).getKey(), "new field is appended last");
    }

    @Test
    void addField_select_persistsOptions() throws SQLException {
        CodexField added = fieldDao.addField(characterTypeId, "Alignment", "SELECT",
                List.of("Lawful", "Neutral", "Chaotic"), null, true);

        CodexField read = fieldDao.findField(characterTypeId, added.getKey()).orElseThrow();
        assertEquals("SELECT", read.getType());
        assertEquals(List.of("Lawful", "Neutral", "Chaotic"), read.getOptions());
    }

    @Test
    void addField_textType_ignoresOptions() throws SQLException {
        CodexField added = fieldDao.addField(characterTypeId, "Nickname", "SHORT_TEXT",
                List.of("should be dropped"), null, true);
        assertNull(fieldDao.findField(characterTypeId, added.getKey()).orElseThrow().getOptions());
    }

    // -------------------------------------------------------------------------
    // CodexTypeFieldDao: update
    // -------------------------------------------------------------------------

    @Test
    void updateField_keyStable_andClearsOptionsOnSwitchToText() throws SQLException {
        CodexField added = fieldDao.addField(characterTypeId, "Faction", "SELECT",
                List.of("Rebels", "Empire"), null, true);
        String key = added.getKey();

        CodexField updated = fieldDao.updateField(characterTypeId, key,
                "Allegiance", "LONG_TEXT", null, "Notes.", false).orElseThrow();

        assertEquals(key, updated.getKey(), "the immutable key must survive an edit");
        assertEquals("Allegiance", updated.getLabel());
        assertEquals("LONG_TEXT", updated.getType());
        assertNull(updated.getOptions(), "switching away from SELECT clears options");
        assertFalse(updated.isFeedsAi());
    }

    @Test
    void updateField_crossType_matchesNothing() throws SQLException {
        // "role" belongs to characterType; addressing it through another Type id
        // must match nothing and leave the original untouched.
        Chapter other = chapterDao.createCodexChapter(codexId, "VOICE", "Voices");

        assertTrue(fieldDao.updateField(other.getId(), "role",
                "Hacked", "SHORT_TEXT", null, null, true).isEmpty());

        assertEquals("Role", fieldDao.findField(characterTypeId, "role").orElseThrow().getLabel());
    }

    @Test
    void updateField_softRemovedField_isNotEditable() throws SQLException {
        insertField(characterTypeId, "gone_1a2b", "Gone", "SHORT_TEXT", null, null, true, 9, true);

        assertTrue(fieldDao.updateField(characterTypeId, "gone_1a2b",
                "Back", "SHORT_TEXT", null, null, true).isEmpty(),
                "a soft-removed field must be restored (E6) before editing");
    }

    // -------------------------------------------------------------------------
    // CodexTypeFieldDao: reorder
    // -------------------------------------------------------------------------

    @Test
    void reorderFields_reordersActiveFieldsByKey() throws SQLException {
        fieldDao.reorderFields(characterTypeId, List.of("age", "role"));

        List<CodexField> active = fieldDao.findActiveByType(characterTypeId);
        assertEquals("age", active.get(0).getKey());
        assertEquals("role", active.get(1).getKey());
    }

    @Test
    void reorderFields_foreignKeyIsIgnored_andDoesNotDisturbOtherType() throws SQLException {
        Chapter other = chapterDao.createCodexChapter(codexId, "VOICE", "Voices");
        insertField(other.getId(), "register", "Register", "SHORT_TEXT", null, null, true, 0, false);

        // "register" belongs to the other Type; guarded out of this Type's reorder.
        fieldDao.reorderFields(characterTypeId, List.of("register"));

        // characterType's own order is untouched...
        List<CodexField> mine = fieldDao.findActiveByType(characterTypeId);
        assertEquals("role", mine.get(0).getKey());
        assertEquals("age", mine.get(1).getKey());

        // ...and the other Type's field is still present.
        assertEquals(1, fieldDao.findActiveByType(other.getId()).size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
