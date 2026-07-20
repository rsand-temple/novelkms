package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
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
    private static final CodexTypeDao      codexTypeDao      = new CodexTypeDao(ds, codexTypeFieldDao);

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new CodexResource(codexDao, codexCategoryDao, chapterDao, codexTypeDao))
            .setMapper(createMapper())
            .build();

    private UUID typeId;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        Project project = createTestProject("Test Project", null);
        Codex   codex   = codexDao.createForProject(project.getId(), "Codex");
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
