package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.resource.codex.CodexResource;
import com.richardsand.novelkms.service.CodexFieldUsageService;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Exercises the E2 read endpoint {@code GET /codex/types/{typeId}} through the
 * JAX-RS stack: the resource assembles the {@link com.richardsand.novelkms.model.codex.CodexType}
 * header from the category chapter and its active fields, and 404s for anything
 * that isn't a live codex Type.
 *
 * <p>ResourceExtension does not register {@code TenantAuthorizationFilter}, so
 * this covers the resource + DAO contract; cross-tenant denial for the
 * {@code types} route is covered at the DAO layer by
 * {@code TenantIsolationDaoTest.ownsChapter_*CodexChapter_scopedToOwner}.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class CodexResourceTest extends NovelKmsTestBase {

    private static final CodexDao          codexDao          = new CodexDao(ds);
    private static final CodexCategoryDao  codexCategoryDao  = new CodexCategoryDao(ds);
    private static final CodexTypeFieldDao codexTypeFieldDao = new CodexTypeFieldDao(ds);
    private static final CodexTypeDao      codexTypeDao      = new CodexTypeDao(ds, chapterDao, codexTypeFieldDao);
    private static final CodexFieldUsageService codexFieldUsageService =
            new CodexFieldUsageService(codexTypeFieldDao, sceneDao);

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new CodexResource(codexDao, codexCategoryDao, chapterDao, codexTypeDao,
                    codexTypeFieldDao, codexFieldUsageService))
            .setMapper(createMapper())
            .build();

    private UUID codexId;
    private UUID typeId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = createTestProject("Test Project", null);
        Codex   codex   = codexDao.createForProject(project.getId(), "Codex");
        codexId = codex.getId();
        Chapter type    = chapterDao.createCodexChapter(codex.getId(), "CHARACTER", "Characters");
        typeId = type.getId();

        // Fresh chapter has no backfilled fields; seed a representative set:
        // a SELECT with options, a plain text field, and a soft-removed one.
        insertField(typeId, "role", "Role", "SELECT",
                "[\"Protagonist\",\"Antagonist\"]", "Structural role.", true, 0, false);
        insertField(typeId, "age", "Age", "SHORT_TEXT", null, null, true, 1, false);
        insertField(typeId, "removed", "Removed", "SHORT_TEXT", null, null, true, 2, true);
        setDescription(typeId, "People in the story.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getType_returns200WithHeaderAndActiveFields() {
        Response r = RESOURCES.target("/codex/types/" + typeId).request().get();
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        assertNotNull(body.get("id"));
        assertEquals("Characters", body.get("name"));
        assertEquals("CHARACTER", body.get("systemKey"));
        assertEquals("People in the story.", body.get("description"));

        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("fields");
        assertEquals(2, fields.size(), "soft-removed field must be excluded");
        assertEquals("role", fields.get(0).get("key"));
        assertEquals("SELECT", fields.get(0).get("type"));
        assertEquals(List.of("Protagonist", "Antagonist"), fields.get(0).get("options"));
        assertTrue((Boolean) fields.get(0).get("feedsAi"));
        assertEquals("age", fields.get(1).get("key"));
    }

    @Test
    void getType_manuscriptChapter_returns404() throws SQLException {
        Project project = createTestProject("Manuscript Project", null);
        Book    book    = bookDao.create(project.getId(), "Book", null, null, null);
        Chapter ms      = chapterDao.create(book.getId(), null, "Chapter One", null, null);

        Response r = RESOURCES.target("/codex/types/" + ms.getId()).request().get();
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void getType_unknownId_returns404() {
        Response r = RESOURCES.target("/codex/types/" + UUID.randomUUID()).request().get();
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    // -------------------------------------------------------------------------
    // E4 write path: create/update Type
    // -------------------------------------------------------------------------

    @Test
    void createType_returns201WithFieldlessAuthorType() {
        Response r = RESOURCES.target("/codex/" + codexId + "/types").request()
                .post(Entity.json(Map.of("name", "Dragons", "description", "The scaly cast.")));
        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        assertNotNull(body.get("id"));
        assertEquals("Dragons", body.get("name"));
        assertEquals("The scaly cast.", body.get("description"));
        assertNull(body.get("systemKey"), "author-created type has no system key");
        assertTrue(((List<?>) body.get("fields")).isEmpty(), "a new type starts field-less");
    }

    @Test
    void createType_blankName_returns400() {
        Response r = RESOURCES.target("/codex/" + codexId + "/types").request()
                .post(Entity.json(Map.of("name", "  ")));
        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void updateType_renamesAndEditsDescription() {
        Response r = RESOURCES.target("/codex/types/" + typeId).request()
                .put(Entity.json(Map.of("name", "People", "description", "Everyone who matters.")));
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        assertEquals("People", body.get("name"));
        assertEquals("Everyone who matters.", body.get("description"));
        assertEquals("CHARACTER", body.get("systemKey"), "rename must not touch the system key");
    }

    @Test
    void updateType_unknownId_returns404() {
        Response r = RESOURCES.target("/codex/types/" + UUID.randomUUID()).request()
                .put(Entity.json(Map.of("name", "Nope")));
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    // -------------------------------------------------------------------------
    // E4 write path: add / update / reorder fields
    // -------------------------------------------------------------------------

    @Test
    void addField_generatesSlugHexKeyAndReturnsField() {
        Response r = RESOURCES.target("/codex/types/" + typeId + "/fields").request()
                .post(Entity.json(Map.of(
                        "label", "Wing Span",
                        "inputType", "SHORT_TEXT",
                        "feedsAi", true)));
        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        String key = (String) body.get("key");
        assertTrue(key.matches("wingspan_[0-9a-f]{4}"), "unexpected generated key: " + key);
        assertEquals("Wing Span", body.get("label"));
        assertEquals("SHORT_TEXT", body.get("type"));
        assertTrue((Boolean) body.get("feedsAi"));
    }

    @Test
    void addField_selectRoundTripsOptions() {
        Response r = RESOURCES.target("/codex/types/" + typeId + "/fields").request()
                .post(Entity.json(Map.of(
                        "label", "Alignment",
                        "inputType", "SELECT",
                        "options", List.of("Lawful", "Neutral", "Chaotic"))));
        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        assertEquals("SELECT", body.get("type"));
        assertEquals(List.of("Lawful", "Neutral", "Chaotic"), body.get("options"));
    }

    @Test
    void addField_missingInputType_returns400() {
        Response r = RESOURCES.target("/codex/types/" + typeId + "/fields").request()
                .post(Entity.json(Map.of("label", "Orphan")));
        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void addField_manuscriptChapter_returns404() throws SQLException {
        Project project = createTestProject("Manuscript Project", null);
        Book    book    = bookDao.create(project.getId(), "Book", null, null, null);
        Chapter ms      = chapterDao.create(book.getId(), null, "Chapter One", null, null);

        Response r = RESOURCES.target("/codex/types/" + ms.getId() + "/fields").request()
                .post(Entity.json(Map.of("label", "Nope", "inputType", "SHORT_TEXT")));
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void updateField_keyStableAcrossRenameAndTypeChange() {
        // Add a SELECT field, then rename + switch it to LONG_TEXT.
        Map<String, Object> created = RESOURCES.target("/codex/types/" + typeId + "/fields").request()
                .post(Entity.json(Map.of(
                        "label", "Faction",
                        "inputType", "SELECT",
                        "options", List.of("Rebels", "Empire"))))
                .readEntity(new GenericType<Map<String, Object>>() {});
        String key = (String) created.get("key");

        Response r = RESOURCES.target("/codex/types/" + typeId + "/fields/" + key).request()
                .put(Entity.json(Map.of("label", "Allegiance", "inputType", "LONG_TEXT")));
        assertEquals(Status.OK.getStatusCode(), r.getStatus());

        Map<String, Object> body = r.readEntity(new GenericType<Map<String, Object>>() {});
        assertEquals(key, body.get("key"), "the immutable key must never change on edit");
        assertEquals("Allegiance", body.get("label"));
        assertEquals("LONG_TEXT", body.get("type"));
        assertNull(body.get("options"), "switching away from SELECT clears options");
    }

    @Test
    void updateField_unknownKey_returns404() {
        Response r = RESOURCES.target("/codex/types/" + typeId + "/fields/does_not_exist").request()
                .put(Entity.json(Map.of("label", "X", "inputType", "SHORT_TEXT")));
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reorderFields_reordersActiveFieldsByKey() {
        // Existing active fields are role (0), age (1). Reverse them.
        Response reorder = RESOURCES.target("/codex/types/" + typeId + "/fields/order").request()
                .put(Entity.json(Map.of("fieldKeys", List.of("age", "role"))));
        assertEquals(Status.NO_CONTENT.getStatusCode(), reorder.getStatus());

        Map<String, Object> type = RESOURCES.target("/codex/types/" + typeId).request()
                .get(new GenericType<Map<String, Object>>() {});
        List<Map<String, Object>> fields = (List<Map<String, Object>>) type.get("fields");
        assertEquals("age", fields.get(0).get("key"));
        assertEquals("role", fields.get(1).get("key"));
    }

    // -------------------------------------------------------------------------
    // E6: soft-remove / restore / usage
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void removeField_activeField_returns204_andDropsFromActiveForm() {
        Response del = RESOURCES.target("/codex/types/" + typeId + "/fields/age").request().delete();
        assertEquals(Status.NO_CONTENT.getStatusCode(), del.getStatus());

        Map<String, Object> type = RESOURCES.target("/codex/types/" + typeId).request()
                .get(new GenericType<Map<String, Object>>() {});
        List<Map<String, Object>> fields = (List<Map<String, Object>>) type.get("fields");
        assertEquals(1, fields.size(), "removed field must drop off the entry form");
        assertEquals("role", fields.get(0).get("key"));
    }

    @Test
    void removeField_alreadyRemoved_returns404() {
        // "removed" was seeded soft-deleted; removing it again matches no active row.
        Response del = RESOURCES.target("/codex/types/" + typeId + "/fields/removed").request().delete();
        assertEquals(Status.NOT_FOUND.getStatusCode(), del.getStatus());
    }

    @Test
    void removeField_unknownKey_returns404() {
        Response del = RESOURCES.target("/codex/types/" + typeId + "/fields/does_not_exist")
                .request().delete();
        assertEquals(Status.NOT_FOUND.getStatusCode(), del.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreField_returns200_andReappearsInOriginalSlot() {
        Response restore = RESOURCES.target("/codex/types/" + typeId + "/fields/removed/restore")
                .request().post(Entity.json(Map.of()));
        assertEquals(Status.OK.getStatusCode(), restore.getStatus());
        Map<String, Object> body = restore.readEntity(new GenericType<Map<String, Object>>() {});
        assertEquals("removed", body.get("key"));

        Map<String, Object> type = RESOURCES.target("/codex/types/" + typeId).request()
                .get(new GenericType<Map<String, Object>>() {});
        List<Map<String, Object>> fields = (List<Map<String, Object>>) type.get("fields");
        // display_order was 2, so it slots back after role (0) and age (1).
        assertEquals(3, fields.size());
        assertEquals("removed", fields.get(2).get("key"));
    }

    @Test
    void restoreField_activeField_returns404() {
        Response restore = RESOURCES.target("/codex/types/" + typeId + "/fields/role/restore")
                .request().post(Entity.json(Map.of()));
        assertEquals(Status.NOT_FOUND.getStatusCode(), restore.getStatus());
    }

    @Test
    void fieldUsage_listsActiveAndRemovedWithEntryCounts() throws SQLException {
        // One entry with a value for role and a blank (whitespace) age.
        var entry = sceneDao.create(typeId, "Frodo", null);
        sceneDao.saveStructuredData(entry.getId(), "{\"role\":\"Protagonist\",\"age\":\"  \"}");

        List<Map<String, Object>> usage = RESOURCES.target("/codex/types/" + typeId + "/fields/usage")
                .request().get(new GenericType<List<Map<String, Object>>>() {});

        assertEquals(3, usage.size(), "usage lists active and removed fields");
        assertEquals("role", usage.get(0).get("key"));
        assertEquals(Boolean.FALSE, usage.get(0).get("removed"));
        assertEquals(1, ((Number) usage.get(0).get("entryCount")).intValue());
        assertEquals("age", usage.get(1).get("key"));
        assertEquals(0, ((Number) usage.get(1).get("entryCount")).intValue(),
                "a whitespace-only value does not count as information");
        assertEquals("removed", usage.get(2).get("key"));
        assertEquals(Boolean.TRUE, usage.get(2).get("removed"));
    }

    @Test
    void fieldUsage_unknownType_returns404() {
        Response r = RESOURCES.target("/codex/types/" + UUID.randomUUID() + "/fields/usage")
                .request().get();
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());
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
