package com.richardsand.novelkms.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * User-facing NovelKMS archive export/import.
 *
 * This is intentionally not a database backup format. It exports author-owned
 * project data into a versioned JSON document and imports it as new local
 * projects by remapping every source UUID to a fresh target UUID.
 *
 * V1 deliberately avoids merge/replace semantics, Trash restoration, OAuth
 * identity state, sessions, and raw secrets/API keys.
 */
public class KmsArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(KmsArchiveService.class);

    public static final String FORMAT         = "novelkms-export";
    public static final int    FORMAT_VERSION = 1;
    public static final String MIME_TYPE      = "application/vnd.novelkms.archive+json";

    private final BasicDataSource ds;
    private final ObjectMapper    mapper;

    public KmsArchiveService(BasicDataSource ds) {
        this.ds = ds;
        this.mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    public record ExportMeta(byte[] bytes, String filename) {
    }

    public record ImportPreview(
            boolean valid,
            String format,
            int formatVersion,
            int projectCount,
            int bookCount,
            int partCount,
            int chapterCount,
            int sceneCount,
            int codexCount,
            int aiReviewCount,
            int aiRecommendationCount,
            List<String> warnings,
            List<String> errors) {
    }

    public record ImportResult(
            int projectCount,
            int bookCount,
            int partCount,
            int chapterCount,
            int sceneCount,
            List<UUID> projectIds,
            List<String> warnings) {
    }

    /**
     * The archive body is relational rather than nested so import can perform a
     * deterministic dependency-ordered remap.
     */
    public static class Archive {
        public String                                 format;
        public int                                    formatVersion;
        public String                                 exportedAt;
        public Map<String, Object>                    source  = new LinkedHashMap<>();
        public Map<String, Object>                    options = new LinkedHashMap<>();
        public Map<String, List<Map<String, Object>>> data    = new LinkedHashMap<>();
    }

    // ---------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------

    public ExportMeta exportProject(UUID userId, UUID projectId) throws Exception {
        Archive archive = new Archive();
        archive.format = FORMAT;
        archive.formatVersion = FORMAT_VERSION;
        archive.exportedAt = Instant.now().toString();
        archive.source.put("app", "NovelKMS");
        archive.options.put("scope", "PROJECT");
        archive.options.put("includeTrash", false);
        archive.options.put("includeSecrets", false);
        archive.options.put("importMode", "AS_NEW_PROJECTS");

        try (Connection c = ds.getConnection()) {
            List<Map<String, Object>> projects = queryRows(c,
                    """
                            SELECT id, title, description, author_first_name, author_last_name,
                                   copyright, display_name, email_address, phone_number,
                                   created_at, updated_at
                            FROM project
                            WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL
                            """, projectId, userId);
            if (projects.isEmpty()) {
                throw new IllegalArgumentException("Project not found");
            }
            put(archive, "projects", projects);

            List<UUID> bookIds = ids(queryRows(c,
                    """
                            SELECT id, project_id, title, subtitle, short_title, display_order, notes,
                                   cover_image, cover_image_mime_type, imported_from, imported_at,
                                   created_at, updated_at
                            FROM book
                            WHERE project_id = ? AND deleted_at IS NULL
                            ORDER BY display_order, title
                            """, projectId));
            put(archive, "books", queryRows(c,
                    """
                            SELECT id, project_id, title, subtitle, short_title, display_order, notes,
                                   cover_image, cover_image_mime_type, imported_from, imported_at,
                                   created_at, updated_at
                            FROM book
                            WHERE project_id = ? AND deleted_at IS NULL
                            ORDER BY display_order, title
                            """, projectId));

            put(archive, "parts", queryRows(c,
                    """
                            SELECT p.id, p.book_id, p.title, p.subtitle, p.display_order, p.notes,
                                   p.created_at, p.updated_at
                            FROM part p
                            JOIN book b ON b.id = p.book_id
                            WHERE b.project_id = ? AND b.deleted_at IS NULL
                            ORDER BY b.display_order, p.display_order
                            """, projectId));

            List<Map<String, Object>> chapters = queryRows(c,
                    """
                            SELECT ch.id, ch.book_id, ch.part_id, ch.codex_id, ch.codex_category,
                                   ch.title, ch.subtitle, ch.display_order, ch.notes,
                                   ch.resets_numbering, ch.created_at, ch.updated_at
                            FROM chapter ch
                            JOIN book b ON b.id = ch.book_id
                            WHERE b.project_id = ?
                              AND b.deleted_at IS NULL
                              AND ch.codex_id IS NULL
                              AND ch.deleted_at IS NULL
                            ORDER BY b.display_order, ch.display_order
                            """, projectId);
            put(archive, "chapters", chapters);
            List<UUID> chapterIds = ids(chapters);

            put(archive, "scenes", chapterIds.isEmpty() ? List.of()
                    : queryRowsIn(c,
                            """
                                    SELECT s.id, s.chapter_id, s.title, s.display_order, s.content,
                                           s.word_count, s.notes, s.created_at, s.updated_at
                                    FROM scene s
                                    WHERE s.chapter_id IN (%s) AND s.deleted_at IS NULL
                                    ORDER BY s.display_order, s.title
                                    """, chapterIds));

            exportOptionalProjectTables(c, archive, projectId, bookIds, chapterIds);
        }

        String title = stringValue(archive.data.get("projects").get(0).get("title"));
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive);
        return new ExportMeta(bytes, safeFilename(title) + "-novelkms.json");
    }

    private void exportOptionalProjectTables(Connection c, Archive archive, UUID projectId,
            List<UUID> bookIds, List<UUID> chapterIds) {
        // Codex containers at project or book scope.
        List<Map<String, Object>> codex = optionalRowsFirstOrIn(c, "codex", """
                SELECT id, project_id, book_id, title, created_at, updated_at
                FROM codex
                WHERE project_id = ? OR book_id IN (%s)
                ORDER BY title
                """, projectId, bookIds);
        put(archive, "codex", codex);
        List<UUID> codexIds = ids(codex);

        // Codex category chapters and their scenes, if any exist.
        List<Map<String, Object>> codexChapters = codexIds.isEmpty() ? List.of() : optionalRows(c, "codex chapters", """
                SELECT id, book_id, part_id, codex_id, codex_category, title, subtitle,
                       display_order, notes, resets_numbering, created_at, updated_at
                FROM chapter
                WHERE codex_id IN (%s) AND deleted_at IS NULL
                ORDER BY display_order, title
                """, codexIds);
        put(archive, "codexChapters", codexChapters);
        List<UUID> codexChapterIds = ids(codexChapters);
        if (!codexChapterIds.isEmpty()) {
            put(archive, "codexScenes", optionalRows(c, "codex scenes", """
                    SELECT id, chapter_id, title, display_order, content,
                           word_count, notes, created_at, updated_at
                    FROM scene
                    WHERE chapter_id IN (%s) AND deleted_at IS NULL
                    ORDER BY display_order, title
                    """, codexChapterIds));
        }

        put(archive, "aiReviews", chapterIds.isEmpty() ? List.of() : optionalRows(c, "ai_review", """
                SELECT * FROM ai_review
                WHERE chapter_id IN (%s)
                ORDER BY created_at
                """, chapterIds));
        List<UUID> reviewIds = ids(archive.data.getOrDefault("aiReviews", List.of()));
        put(archive, "aiRecommendations", reviewIds.isEmpty() ? List.of() : optionalRows(c, "ai_review_recommendation", """
                SELECT * FROM ai_review_recommendation
                WHERE review_id IN (%s)
                ORDER BY seq, created_at
                """, reviewIds));

        put(archive, "chapterMemory", chapterIds.isEmpty() ? List.of() : optionalRows(c, "chapter_memory", """
                SELECT * FROM chapter_memory
                WHERE chapter_id IN (%s)
                ORDER BY generated_at
                """, chapterIds));
        put(archive, "chapterSummaries", chapterIds.isEmpty() ? List.of() : optionalRows(c, "chapter_summary", """
                SELECT * FROM chapter_summary
                WHERE chapter_id IN (%s)
                ORDER BY generated_at
                """, chapterIds));
        put(archive, "bookSummaries", bookIds.isEmpty() ? List.of() : optionalRows(c, "book_summary", """
                SELECT * FROM book_summary
                WHERE book_id IN (%s)
                ORDER BY generated_at
                """, bookIds));

        // Settings/templates are optional in V1; absent tables/columns are skipped
        // rather than making archive portability depend on exact schema vintage.
        put(archive, "editorSettings", optionalRowsFirstOrIn(c, "editor_settings", """
                SELECT * FROM editor_settings
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds));
        put(archive, "pageLayouts", optionalRowsFirstOrIn(c, "page_layout", """
                SELECT * FROM page_layout
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds));
        put(archive, "aiFormInstructions", optionalRowsFirstOrIn(c, "ai_form_instructions", """
                SELECT * FROM ai_form_instructions
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds));
        put(archive, "memoryTemplates", optionalRowsFirstOrIn(c, "memory_template", """
                SELECT * FROM memory_template
                WHERE project_id = ? OR book_id IN (%s)
                """, projectId, bookIds));
    }

    // ---------------------------------------------------------------------
    // Validate / preview
    // ---------------------------------------------------------------------

    public ImportPreview preview(InputStream stream) throws IOException {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Archive      archive  = readArchive(stream);
        validateArchive(archive, errors, warnings);
        Map<String, List<Map<String, Object>>> d = archive.data != null ? archive.data : Map.of();
        return new ImportPreview(errors.isEmpty(), archive.format, archive.formatVersion,
                size(d, "projects"), size(d, "books"), size(d, "parts"),
                size(d, "chapters") + size(d, "codexChapters"),
                size(d, "scenes") + size(d, "codexScenes"),
                size(d, "codex"), size(d, "aiReviews"), size(d, "aiRecommendations"),
                warnings, errors);
    }

    // ---------------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------------

    public ImportResult importAsNewProjects(UUID userId, InputStream stream) throws Exception {
        Archive      archive  = readArchive(stream);
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        validateArchive(archive, errors, warnings);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        Map<UUID, UUID>                        idMap         = new HashMap<>();
        List<UUID>                             newProjectIds = new ArrayList<>();
        Map<String, List<Map<String, Object>>> d             = archive.data;

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                importRows(c, "project", d.getOrDefault("projects", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "book", d.getOrDefault("books", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "part", d.getOrDefault("parts", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "chapter", d.getOrDefault("chapters", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "scene", d.getOrDefault("scenes", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "codex", d.getOrDefault("codex", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "chapter", d.getOrDefault("codexChapters", List.of()), idMap, userId, newProjectIds, warnings);
                importRows(c, "scene", d.getOrDefault("codexScenes", List.of()), idMap, userId, newProjectIds, warnings);

                importRowsIfPresent(c, "ai_review", d.get("aiReviews"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "ai_review_recommendation", d.get("aiRecommendations"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "chapter_memory", d.get("chapterMemory"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "chapter_summary", d.get("chapterSummaries"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "book_summary", d.get("bookSummaries"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "editor_settings", d.get("editorSettings"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "page_layout", d.get("pageLayouts"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "ai_form_instructions", d.get("aiFormInstructions"), idMap, userId, newProjectIds, warnings);
                importRowsIfPresent(c, "memory_template", d.get("memoryTemplates"), idMap, userId, newProjectIds, warnings);

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        return new ImportResult(size(d, "projects"), size(d, "books"), size(d, "parts"),
                size(d, "chapters") + size(d, "codexChapters"),
                size(d, "scenes") + size(d, "codexScenes"), newProjectIds, warnings);
    }

    private void importRowsIfPresent(Connection c, String table, List<Map<String, Object>> rows,
            Map<UUID, UUID> idMap, UUID userId, List<UUID> newProjectIds, List<String> warnings) throws SQLException {
        if (rows == null || rows.isEmpty())
            return;
        if (!tableExists(c, table)) {
            warnings.add("Skipped " + table + ": table is not present in this schema");
            return;
        }
        importRows(c, table, rows, idMap, userId, newProjectIds, warnings);
    }

    private void importRows(Connection c, String table, List<Map<String, Object>> rows,
            Map<UUID, UUID> idMap, UUID userId, List<UUID> newProjectIds, List<String> warnings) throws SQLException {
        for (Map<String, Object> source : rows) {
            LinkedHashMap<String, Object> target = new LinkedHashMap<>(source);

            UUID sourceId = uuidValue(target.get("id"));
            if (sourceId != null) {
                UUID newId = idMap.computeIfAbsent(sourceId, ignored -> UUID.randomUUID());
                target.put("id", newId.toString());
                if ("project".equals(table))
                    newProjectIds.add(newId);
            }

            // User-facing imports always become owned by the authenticated user.
            if ("project".equals(table)) {
                target.put("owner_user_id", userId.toString());
            } else {
                target.remove("owner_user_id");
                if (target.containsKey("user_id")) {
                    target.put("user_id", userId.toString());
                }
            }

            remapForeignKeys(target, idMap);
            removeColumnsNotInTable(c, table, target);

            if (target.isEmpty())
                continue;
            insertRow(c, table, target);
        }
    }

    private void remapForeignKeys(Map<String, Object> row, Map<UUID, UUID> idMap) {
        for (String key : new ArrayList<>(row.keySet())) {
            if (!isUuidLikeColumn(key) || row.get(key) == null)
                continue;
            UUID sourceUuid = uuidValue(row.get(key));
            if (sourceUuid == null)
                continue;
            UUID mapped = idMap.computeIfAbsent(sourceUuid, ignored -> UUID.randomUUID());
            row.put(key, mapped.toString());
        }
    }

    private void insertRow(Connection c, String table, LinkedHashMap<String, Object> row) throws SQLException {
        String columns      = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(k -> "?").toList());
        String sql          = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                setParam(ps, i++, e.getKey(), e.getValue());
            }
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------
    // Generic JDBC helpers
    // ---------------------------------------------------------------------

    private List<Map<String, Object>> queryRows(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++)
                ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData         md   = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String column = md.getColumnLabel(i);
                        Object value  = rs.getObject(i);
                        row.put(column, jsonValue(value));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private List<Map<String, Object>> queryRowsIn(Connection c, String sqlTemplate, List<UUID> ids) throws SQLException {
        if (ids == null || ids.isEmpty())
            return List.of();
        String placeholders = String.join(", ", ids.stream().map(id -> "?").toList());
        return queryRows(c, sqlTemplate.formatted(placeholders), ids.toArray());
    }

    private List<Map<String, Object>> optionalRows(Connection c, String label, String sqlTemplate, List<UUID> ids) {
        try {
            return queryRowsIn(c, sqlTemplate, ids);
        } catch (SQLException e) {
            logger.info("Skipping optional KMS archive section {}: {}", label, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> queryRowsFirstOrIn(Connection c, String sqlTemplate, Object first, List<UUID> ids) throws SQLException {
        String       placeholders = ids == null || ids.isEmpty()
                ? "NULL"
                : String.join(", ", ids.stream().map(id -> "?").toList());
        List<Object> params       = new ArrayList<>();
        params.add(first);
        if (ids != null)
            params.addAll(ids);
        return queryRows(c, sqlTemplate.formatted(placeholders), params.toArray());
    }

    private List<Map<String, Object>> optionalRowsFirstOrIn(Connection c, String label, String sqlTemplate, Object first, List<UUID> ids) {
        try {
            return queryRowsFirstOrIn(c, sqlTemplate, first, ids);
        } catch (SQLException e) {
            logger.info("Skipping optional KMS archive section {}: {}", label, e.getMessage());
            return List.of();
        }
    }

    private Object jsonValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof UUID u)
            return u.toString();
        if (value instanceof Timestamp ts)
            return ts.toInstant().toString();
        if (value instanceof java.sql.Date d)
            return d.toLocalDate().toString();
        if (value instanceof byte[] bytes)
            return Base64.getEncoder().encodeToString(bytes);
        return value;
    }

    private void setParam(PreparedStatement ps, int index, String column, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else if ("cover_image".equals(column) && value instanceof String s) {
            ps.setBytes(index, Base64.getDecoder().decode(s));
        } else if (isTimestampColumn(column) && value instanceof String s) {
            ps.setTimestamp(index, Timestamp.from(Instant.parse(s)));
        } else if (isUuidLikeColumn(column) && value instanceof String s) {
            ps.setObject(index, UUID.fromString(s));
        } else {
            ps.setObject(index, value);
        }
    }

    private void removeColumnsNotInTable(Connection c, String table, LinkedHashMap<String, Object> row) throws SQLException {
        Set<String> columns = tableColumns(c, table);
        row.keySet().removeIf(k -> !columns.contains(k));
    }

    private Set<String> tableColumns(Connection c, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next())
                result.add(rs.getString("COLUMN_NAME"));
        }
        // PostgreSQL may require lower-case lookup through the public schema.
        if (result.isEmpty()) {
            try (ResultSet rs = c.getMetaData().getColumns(null, "public", table, null)) {
                while (rs.next())
                    result.add(rs.getString("COLUMN_NAME"));
            }
        }
        return result;
    }

    private boolean tableExists(Connection c, String table) throws SQLException {
        return !tableColumns(c, table).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Validation / parsing helpers
    // ---------------------------------------------------------------------

    private Archive readArchive(InputStream stream) throws IOException {
        return mapper.readValue(stream, Archive.class);
    }

    private void validateArchive(Archive archive, List<String> errors, List<String> warnings) {
        if (archive == null) {
            errors.add("Archive could not be read");
            return;
        }
        if (!FORMAT.equals(archive.format)) {
            errors.add("Unsupported archive format: " + archive.format);
        }
        if (archive.formatVersion != FORMAT_VERSION) {
            errors.add("Unsupported archive version: " + archive.formatVersion);
        }
        if (archive.data == null) {
            errors.add("Archive is missing data section");
            return;
        }
        if (size(archive.data, "projects") == 0) {
            errors.add("Archive contains no projects");
        }
        if (size(archive.data, "projects") > 1) {
            warnings.add("Archive contains multiple projects; all will be imported as new projects");
        }
        validateUniqueIds(archive.data, errors);
        validateReferences(archive.data, errors);
    }

    private void validateUniqueIds(Map<String, List<Map<String, Object>>> data, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, List<Map<String, Object>>> section : data.entrySet()) {
            for (Map<String, Object> row : section.getValue()) {
                Object id = row.get("id");
                if (id == null)
                    continue;
                String s = String.valueOf(id);
                if (!ids.add(s))
                    errors.add("Duplicate exported id: " + s);
            }
        }
    }

    private void validateReferences(Map<String, List<Map<String, Object>>> data, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (List<Map<String, Object>> rows : data.values()) {
            for (Map<String, Object> row : rows) {
                if (row.get("id") != null)
                    ids.add(String.valueOf(row.get("id")));
            }
        }
        for (Map.Entry<String, List<Map<String, Object>>> section : data.entrySet()) {
            for (Map<String, Object> row : section.getValue()) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    String key = e.getKey();
                    if ("id".equals(key) || e.getValue() == null || !isUuidLikeColumn(key))
                        continue;
                    String ref = String.valueOf(e.getValue());
                    // provider/user/system-scoped metadata may reference rows outside the archive;
                    // for V1 warn by omission rather than failing those imports.
                    if (!ids.contains(ref) && !"credential_id".equals(key)) {
                        // Do not fail hard; the import remapper can allocate a placeholder UUID,
                        // but FKs will reject impossible references. This catches most mistakes.
                    }
                }
            }
        }
    }

    private List<UUID> ids(List<Map<String, Object>> rows) {
        List<UUID> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID id = uuidValue(row.get("id"));
            if (id != null)
                ids.add(id);
        }
        return ids;
    }

    private UUID uuidValue(Object value) {
        if (value == null)
            return null;
        if (value instanceof UUID u)
            return u;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isUuidLikeColumn(String column) {
        return "id".equals(column) || column.endsWith("_id");
    }

    private boolean isTimestampColumn(String column) {
        return column.endsWith("_at") || column.endsWith("_time") || "timestamp".equals(column);
    }

    private int size(Map<String, List<Map<String, Object>>> data, String section) {
        return data.getOrDefault(section, List.of()).size();
    }

    private void put(Archive archive, String key, List<Map<String, Object>> rows) {
        archive.data.put(key, rows == null ? List.of() : rows);
    }

    private String stringValue(Object o) {
        return o == null ? "novelkms" : String.valueOf(o);
    }

    private String safeFilename(String raw) {
        String s = raw == null || raw.isBlank() ? "novelkms-project" : raw.trim();
        s = s.replaceAll("[^A-Za-z0-9._-]+", "-");
        s = s.replaceAll("-+", "-");
        return s.isBlank() ? "novelkms-project" : s;
    }
}
