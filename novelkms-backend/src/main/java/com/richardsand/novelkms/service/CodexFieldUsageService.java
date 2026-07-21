package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.codex.CodexFieldUsage;

/**
 * Assembles the type-editor field-usage view (E6): a Type's fields — active and
 * soft-removed — each annotated with how many of the Type's entries currently
 * hold a non-blank value for that field.
 *
 * <p>The field definitions and their removed state come from
 * {@link CodexTypeFieldDao#findUsage(UUID)}; the per-key entry counts are
 * computed here by reading the Type's entry scenes
 * ({@link SceneDao#findByChapterId(UUID)} — a codex entry is a scene under the
 * Type's category chapter row) and parsing each entry's {@code structured_data}
 * JSON in Java. Counting in Java rather than in SQL is deliberate: the H2 test
 * database runs in DEFAULT mode with no reliable JSON operators, so a portable
 * {@code LIKE} match would be fragile (value substrings, removed keys), whereas
 * parsing the object is exact and identical on H2 and PostgreSQL.
 *
 * <p>"Holds a value" means the entry's {@code structured_data} object has the
 * field key mapped to a non-null value whose string form is non-blank after
 * trimming — an empty string, whitespace, or a missing key does not count. This
 * matches the design's warning wording ("N entries contain information in this
 * field"). Each entry contributes at most one increment per key because
 * {@code structured_data} is a flat object keyed by field.
 */
public class CodexFieldUsageService {

    private static final Logger logger = LoggerFactory.getLogger(CodexFieldUsageService.class);

    private static final ObjectMapper                     MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
    };

    private final CodexTypeFieldDao fieldDao;
    private final SceneDao          sceneDao;

    public CodexFieldUsageService(CodexTypeFieldDao fieldDao, SceneDao sceneDao) {
        this.fieldDao = fieldDao;
        this.sceneDao = sceneDao;
    }

    /**
     * All fields of the Type (active and soft-removed) in display order, each
     * carrying its current entry-usage count. Returns an empty list when the
     * Type has no fields; the caller is expected to have already confirmed the
     * Type is a live codex Type (the resource 404s otherwise before calling).
     */
    public List<CodexFieldUsage> usage(UUID typeId) throws SQLException {
        List<CodexFieldUsage> fields = fieldDao.findUsage(typeId);
        if (fields.isEmpty()) {
            return fields;
        }
        Map<String, Integer> counts = countByKey(typeId);
        List<CodexFieldUsage> result = new ArrayList<>(fields.size());
        for (CodexFieldUsage f : fields) {
            result.add(f.toBuilder()
                    .entryCount(counts.getOrDefault(f.getKey(), 0))
                    .build());
        }
        return result;
    }

    /**
     * Tallies, per field key, how many of the Type's entries hold a non-blank
     * value. A scene whose {@code structured_data} is null, blank, or unparseable
     * contributes nothing (fail-soft: a broken blob must not break the editor).
     */
    private Map<String, Integer> countByKey(UUID typeId) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        for (Scene scene : sceneDao.findByChapterId(typeId)) {
            String json = scene.getStructuredData();
            if (json == null || json.isBlank()) {
                continue;
            }
            Map<String, Object> data;
            try {
                data = MAPPER.readValue(json, OBJECT);
            } catch (Exception e) {
                logger.warn("Ignoring unparseable scene.structured_data for entry {}: {}",
                        scene.getId(), e.getMessage());
                continue;
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (hasValue(entry.getValue())) {
                    counts.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * True when a stored field value should count as "contains information": a
     * non-null value whose string form is non-blank after trimming.
     */
    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        return !String.valueOf(value).trim().isEmpty();
    }
}
