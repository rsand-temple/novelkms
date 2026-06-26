package com.richardsand.novelkms.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.richardsand.novelkms.dao.KmsArchiveDao;
import com.richardsand.novelkms.dao.KmsArchiveDao.InsertBatch;

/**
 * User-facing NovelKMS archive export/import.
 *
 * <p>This is intentionally not a database backup format. It exports
 * author-owned project data into a versioned JSON document and imports it as new
 * local projects by remapping every source UUID to a fresh target UUID.</p>
 *
 * <p>V1 deliberately avoids merge/replace semantics, Trash restoration, OAuth
 * identity state, sessions, and raw secrets/API keys.</p>
 */
public class KmsArchiveService {

    public static final String FORMAT         = "novelkms-export";
    public static final int    FORMAT_VERSION = 1;
    public static final String MIME_TYPE      = "application/vnd.novelkms.archive+json";

    private final KmsArchiveDao dao;
    private final ObjectMapper  mapper;

    public KmsArchiveService(KmsArchiveDao dao) {
        this.dao = dao;
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

    /** Relational archive body for deterministic dependency-ordered import. */
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

        List<Map<String, Object>> projects = dao.findProjectForExport(userId, projectId);
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("Project not found");
        }
        put(archive, "projects", projects);

        List<Map<String, Object>> books = dao.findBooksForProject(projectId);
        put(archive, "books", books);
        List<UUID> bookIds = ids(books);

        put(archive, "parts", dao.findPartsForProject(projectId));

        List<Map<String, Object>> chapters = dao.findChaptersForProject(projectId);
        put(archive, "chapters", chapters);
        List<UUID> chapterIds = ids(chapters);

        put(archive, "scenes", dao.findScenesForChapters(chapterIds));
        exportOptionalProjectTables(archive, projectId, bookIds, chapterIds);

        String title = stringValue(archive.data.get("projects").get(0).get("title"));
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive);
        return new ExportMeta(bytes, safeFilename(title) + "-novelkms.json");
    }

    private void exportOptionalProjectTables(Archive archive, UUID projectId, List<UUID> bookIds, List<UUID> chapterIds) {
        List<Map<String, Object>> codex = dao.findCodexForProject(projectId, bookIds);
        put(archive, "codex", codex);
        List<UUID> codexIds = ids(codex);

        List<Map<String, Object>> codexChapters = dao.findCodexChapters(codexIds);
        put(archive, "codexChapters", codexChapters);
        List<UUID> codexChapterIds = ids(codexChapters);
        put(archive, "codexScenes", dao.findCodexScenes(codexChapterIds));

        List<Map<String, Object>> aiReviews = dao.findAiReviews(chapterIds);
        put(archive, "aiReviews", aiReviews);
        put(archive, "aiRecommendations", dao.findAiRecommendations(ids(aiReviews)));

        put(archive, "chapterMemory", dao.findChapterMemory(chapterIds));
        put(archive, "chapterSummaries", dao.findChapterSummaries(chapterIds));
        put(archive, "bookSummaries", dao.findBookSummaries(bookIds));

        put(archive, "editorSettings", dao.findEditorSettings(projectId, bookIds));
        put(archive, "pageLayouts", dao.findPageLayouts(projectId, bookIds));
        put(archive, "aiFormInstructions", dao.findAiFormInstructions(projectId, bookIds));
        put(archive, "memoryTemplates", dao.findMemoryTemplates(projectId, bookIds));
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

        List<InsertBatch> batches = new ArrayList<>();
        batches.add(new InsertBatch("project", remapRows("project", d.getOrDefault("projects", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("book", remapRows("book", d.getOrDefault("books", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("part", remapRows("part", d.getOrDefault("parts", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("chapter", remapRows("chapter", d.getOrDefault("chapters", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("scene", remapRows("scene", d.getOrDefault("scenes", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("codex", remapRows("codex", d.getOrDefault("codex", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("chapter", remapRows("chapter", d.getOrDefault("codexChapters", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("scene", remapRows("scene", d.getOrDefault("codexScenes", List.of()), idMap, userId, newProjectIds)));

        batches.add(new InsertBatch("ai_review", remapRows("ai_review", d.getOrDefault("aiReviews", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("ai_review_recommendation", remapRows("ai_review_recommendation", d.getOrDefault("aiRecommendations", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("chapter_memory", remapRows("chapter_memory", d.getOrDefault("chapterMemory", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("chapter_summary", remapRows("chapter_summary", d.getOrDefault("chapterSummaries", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("book_summary", remapRows("book_summary", d.getOrDefault("bookSummaries", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("editor_settings", remapRows("editor_settings", d.getOrDefault("editorSettings", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("page_layout", remapRows("page_layout", d.getOrDefault("pageLayouts", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("ai_form_instructions", remapRows("ai_form_instructions", d.getOrDefault("aiFormInstructions", List.of()), idMap, userId, newProjectIds)));
        batches.add(new InsertBatch("memory_template", remapRows("memory_template", d.getOrDefault("memoryTemplates", List.of()), idMap, userId, newProjectIds)));

        dao.insertRowsInTransaction(batches, warnings);

        return new ImportResult(size(d, "projects"), size(d, "books"), size(d, "parts"),
                size(d, "chapters") + size(d, "codexChapters"),
                size(d, "scenes") + size(d, "codexScenes"), newProjectIds, warnings);
    }

    private List<Map<String, Object>> remapRows(String table, List<Map<String, Object>> rows,
            Map<UUID, UUID> idMap, UUID importingUserId, List<UUID> newProjectIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : rows) {
            result.add(remapRow(table, source, idMap, importingUserId, newProjectIds));
        }
        return result;
    }

    private Map<String, Object> remapRow(String table, Map<String, Object> source,
            Map<UUID, UUID> idMap, UUID importingUserId, List<UUID> newProjectIds) {
        LinkedHashMap<String, Object> target = new LinkedHashMap<>(source);

        UUID sourceId = uuidValue(target.get("id"));
        if (sourceId != null) {
            UUID newId = idMap.computeIfAbsent(sourceId, ignored -> UUID.randomUUID());
            target.put("id", newId.toString());
            if ("project".equals(table)) {
                newProjectIds.add(newId);
            }
        }

        if ("project".equals(table)) {
            target.put("owner_user_id", importingUserId.toString());
        } else {
            target.remove("owner_user_id");
            if (target.containsKey("user_id")) {
                target.put("user_id", importingUserId.toString());
            }
        }

        remapForeignKeys(target, idMap);
        return target;
    }

    private void remapForeignKeys(Map<String, Object> row, Map<UUID, UUID> idMap) {
        for (String key : new ArrayList<>(row.keySet())) {
            if (!isUuidLikeColumn(key) || row.get(key) == null) {
                continue;
            }
            if ("id".equals(key) || "owner_user_id".equals(key) || "user_id".equals(key)) {
                continue;
            }

            UUID sourceUuid = uuidValue(row.get(key));
            if (sourceUuid == null) {
                continue;
            }

            UUID mapped = idMap.get(sourceUuid);
            if (mapped != null) {
                row.put(key, mapped.toString());
            }
        }
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
                if (id == null) {
                    continue;
                }
                String s = String.valueOf(id);
                if (!ids.add(s)) {
                    errors.add("Duplicate exported id: " + s);
                }
            }
        }
    }

    private void validateReferences(Map<String, List<Map<String, Object>>> data, List<String> errors) {
        // V1 validates format shape and duplicate IDs only. Relationship validity
        // is enforced by database foreign keys during import. This deliberately
        // avoids rejecting optional AI/provider/user references that may point
        // outside the portable archive.
    }

    private List<UUID> ids(List<Map<String, Object>> rows) {
        List<UUID> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID id = uuidValue(row.get("id"));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private UUID uuidValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isUuidLikeColumn(String column) {
        return "id".equals(column) || column.endsWith("_id");
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
