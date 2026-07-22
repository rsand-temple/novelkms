package com.richardsand.novelkms.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexSchema;

/**
 * Exports individual codex entries to DOCX and imports them back, using a
 * fixed round-trip contract: the exported structure is what the importer
 * expects. No third-party document structure is supported.
 *
 * <p><b>DOCX contract</b> (heading style → content):
 * <pre>
 *   Heading 1 — entry title
 *   Heading 3 — schema field label  (one H3 per field, in schema order)
 *   Normal    — field value text
 *   ...
 *   Heading 2 — "Description"  (sentinel; marks start of free body)
 *   Normal    — body paragraph(s)
 * </pre>
 *
 * <p>Categories with no schema (plain title-plus-body types) export only the
 * H1 title, H2 "Description", and body paragraphs.
 *
 * <p>On import the parser is lenient: unrecognized H3 labels are silently
 * skipped, and fields not present in the exported document retain their
 * current stored value.
 */
public class CodexExportService {

    private static final Logger       logger = LoggerFactory.getLogger(CodexExportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Document heading style ID used by POI when writing. */
    private static final String STYLE_H1 = "Heading1";
    private static final String STYLE_H2 = "Heading2";
    private static final String STYLE_H3 = "Heading3";

    /** H2 sentinel text that marks the beginning of the body section. */
    private static final String DESCRIPTION_SENTINEL = "Description";

    /**
     * Character budget for the body text included in the DOCX. Body beyond
     * this limit is truncated on export to keep file sizes reasonable; the
     * full body is always stored in the database.
     */
    private static final int MAX_BODY_EXPORT_CHARS = 100_000;

    private final SceneDao           sceneDao;
    private final ChapterDao         chapterDao;
    private final CodexTypeFieldDao  codexTypeFieldDao;

    public CodexExportService(SceneDao sceneDao, ChapterDao chapterDao,
            CodexTypeFieldDao codexTypeFieldDao) {
        this.sceneDao          = sceneDao;
        this.chapterDao        = chapterDao;
        this.codexTypeFieldDao = codexTypeFieldDao;
    }

    // =========================================================================
    // Result type returned to the caller on import
    // =========================================================================

    /**
     * Holds the imported values ready for the caller to persist via the
     * existing DAO methods ({@code sceneDao.update}, {@code saveContent},
     * {@code saveStructuredData}).
     */
    public record ImportResult(
            String title,
            String structuredDataJson,
            String bodyHtml,
            int    wordCount) {
    }

    // =========================================================================
    // Export
    // =========================================================================

    /**
     * Generates a DOCX byte array for the codex entry identified by
     * {@code sceneId}. The entry's title, structured fields (if any), and
     * body are all rendered following the documented contract.
     *
     * @param sceneId the scene id of the codex entry
     * @return DOCX bytes ready to stream to the client
     * @throws Exception if the scene or chapter is missing, or POI fails
     */
    public byte[] exportEntry(UUID sceneId) throws Exception {
        Scene   scene   = requireScene(sceneId);
        Chapter chapter = requireChapter(scene.getChapterId());

        CodexSchema schema = resolveSchema(chapter.getId());

        String entryTitle = scene.getTitle() == null || scene.getTitle().isBlank()
                ? "Untitled" : scene.getTitle().trim();

        logger.info("Exporting codex entry to DOCX: sceneId={}, title={}, categoryKey={}",
                sceneId, entryTitle, chapter.getCodexCategory());

        try (XWPFDocument doc = new XWPFDocument()) {
            // H1 — entry title
            addHeading(doc, STYLE_H1, entryTitle);

            // Structured fields
            if (schema != null && schema.getFields() != null) {
                Map<String, Object> fieldValues = parseStructuredData(scene.getStructuredData());
                for (CodexField field : schema.getFields()) {
                    addHeading(doc, STYLE_H3, field.getLabel() != null ? field.getLabel() : field.getKey());
                    String val = fieldValueString(fieldValues, field.getKey());
                    addNormal(doc, val);
                }
            }

            // H2 "Description" + body
            addHeading(doc, STYLE_H2, DESCRIPTION_SENTINEL);
            String bodyText = htmlToText(scene.getContent());
            if (bodyText.length() > MAX_BODY_EXPORT_CHARS) {
                bodyText = bodyText.substring(0, MAX_BODY_EXPORT_CHARS);
            }
            if (!bodyText.isBlank()) {
                for (String paragraph : bodyText.split("\n\n+")) {
                    String para = paragraph.strip();
                    if (!para.isEmpty()) {
                        addNormal(doc, para);
                    }
                }
            } else {
                addNormal(doc, "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    // =========================================================================
    // Import
    // =========================================================================

    /**
     * Parses a DOCX file uploaded by the author and saves the extracted
     * title, structured fields, and body directly to the database, then
     * returns the refreshed {@link Scene}.
     *
     * <p>The parser is lenient: H3 headings that don't match a schema field
     * label are silently skipped, and existing field values for fields not
     * present in the document are preserved.
     *
     * @param sceneId the codex entry scene to overwrite
     * @param stream  the uploaded DOCX data; the caller is responsible for closing it
     * @return the updated scene as stored in the database
     * @throws Exception if parsing fails or the scene/chapter is not found
     */
    public Scene importEntry(UUID sceneId, InputStream stream) throws Exception {
        Scene   scene   = requireScene(sceneId);
        Chapter chapter = requireChapter(scene.getChapterId());
        CodexSchema schema = resolveSchema(chapter.getId());

        logger.info("Importing codex entry from DOCX: sceneId={}, categoryKey={}",
                sceneId, chapter.getCodexCategory());

        ImportResult result;
        try (XWPFDocument doc = new XWPFDocument(stream)) {
            result = parseDocument(doc, schema, scene.getStructuredData());
        }

        // Save title
        if (result.title() != null && !result.title().isBlank()) {
            sceneDao.update(sceneId, result.title(), scene.getNotes());
        }

        // Save structured data (merge with existing to preserve _removedFields etc.)
        if (schema != null && result.structuredDataJson() != null) {
            sceneDao.saveStructuredData(sceneId, result.structuredDataJson());
        }

        // Save body
        if (result.bodyHtml() != null) {
            sceneDao.saveContent(sceneId, result.bodyHtml(), result.wordCount());
        }

        return sceneDao.findById(sceneId)
                .orElseThrow(() -> new IllegalStateException("Scene disappeared after import save"));
    }

    // =========================================================================
    // DOCX parsing
    // =========================================================================

    private ImportResult parseDocument(XWPFDocument doc, CodexSchema schema,
            String existingStructuredData) {

        // Build a label→key lookup from the schema (case-insensitive)
        Map<String, String> labelToKey = new LinkedHashMap<>();
        if (schema != null && schema.getFields() != null) {
            for (CodexField field : schema.getFields()) {
                String label = field.getLabel() != null ? field.getLabel().strip() : field.getKey();
                labelToKey.put(label.toLowerCase(), field.getKey());
            }
        }

        String title              = null;
        String currentFieldKey    = null;
        List<String> fieldParas   = new ArrayList<>();
        Map<String, String> imported = new LinkedHashMap<>();
        boolean descriptionStarted = false;
        List<String> bodyParas    = new ArrayList<>();

        for (XWPFParagraph para : doc.getParagraphs()) {
            int level = headingLevel(para);
            String text = para.getText();
            if (text == null) text = "";

            if (level == 1 && title == null) {
                // First H1 → entry title
                title = text.strip();
                continue;
            }

            if (level == 2 && DESCRIPTION_SENTINEL.equalsIgnoreCase(text.strip())) {
                // H2 "Description" → flush current field, start body collection
                flushField(currentFieldKey, fieldParas, imported);
                currentFieldKey = null;
                fieldParas      = new ArrayList<>();
                descriptionStarted = true;
                continue;
            }

            if (level == 3 && !descriptionStarted) {
                // H3 → flush previous field, start new one
                flushField(currentFieldKey, fieldParas, imported);
                fieldParas   = new ArrayList<>();
                String label = text.strip().toLowerCase();
                currentFieldKey = labelToKey.get(label);
                // currentFieldKey is null if the label doesn't match — paragraphs are discarded
                continue;
            }

            if (descriptionStarted) {
                bodyParas.add(text);
            } else if (currentFieldKey != null && level == 0) {
                if (!text.isBlank()) {
                    fieldParas.add(text.strip());
                }
            }
        }

        // Flush any remaining field
        flushField(currentFieldKey, fieldParas, imported);

        // Merge imported fields with existing structured data
        String mergedJson = mergeStructuredData(existingStructuredData, imported, schema);

        // Build body HTML
        StringBuilder bodyHtml = new StringBuilder();
        int wordCount = 0;
        for (String para : bodyParas) {
            if (!para.isBlank()) {
                bodyHtml.append("<p>").append(escapeHtml(para.strip())).append("</p>");
                wordCount += countWords(para);
            }
        }

        logger.debug("Codex import parsed: title={}, importedFields={}, bodyParas={}, wordCount={}",
                title, imported.size(), bodyParas.size(), wordCount);

        return new ImportResult(title, mergedJson, bodyHtml.toString(), wordCount);
    }

    private static void flushField(String fieldKey, List<String> paragraphs,
            Map<String, String> target) {
        if (fieldKey == null || paragraphs.isEmpty()) return;
        target.put(fieldKey, String.join("\n\n", paragraphs).strip());
    }

    /**
     * Merges the fields found in the imported document on top of the existing
     * stored structured data. Fields absent from the import retain their
     * current stored value. The {@code _removedFields} metadata key is
     * preserved from existing data.
     */
    private String mergeStructuredData(String existingJson, Map<String, String> imported,
            CodexSchema schema) {
        if (schema == null) return null;
        Map<String, Object> existing = parseStructuredData(existingJson);
        // Overlay imported values
        existing.putAll(imported);
        try {
            return MAPPER.writeValueAsString(existing);
        } catch (Exception e) {
            logger.warn("Could not serialize merged structured data: {}", e.getMessage());
            return existingJson;
        }
    }

    // =========================================================================
    // POI helpers — export
    // =========================================================================

    private static void addHeading(XWPFDocument doc, String styleId, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setStyle(styleId);
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
    }

    private static void addNormal(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "");
    }

    // =========================================================================
    // POI helpers — import
    // =========================================================================

    /**
     * Returns the heading level (1, 2, or 3) of the given paragraph, or 0 if
     * the paragraph is a normal (non-heading) paragraph.
     *
     * <p>We detect by {@code styleID} since we control the export format and
     * always write "Heading1", "Heading2", "Heading3". A case-insensitive,
     * whitespace-collapsed comparison handles any variation introduced by Word
     * or LibreOffice on round-trip.
     */
    private static int headingLevel(XWPFParagraph para) {
        String id = para.getStyleID();
        if (id == null) return 0;
        switch (id.toLowerCase().replace(" ", "").replace("-", "")) {
            case "heading1": case "1": return 1;
            case "heading2": case "2": return 2;
            case "heading3": case "3": return 3;
            default:                   return 0;
        }
    }

    // =========================================================================
    // Shared utilities
    // =========================================================================

    private static String htmlToText(String html) {
        if (html == null || html.isBlank()) return "";
        Document doc = Jsoup.parse(html);
        StringBuilder sb = new StringBuilder();
        for (Element el : doc.body().select("p, h1, h2, h3, h4, blockquote, li")) {
            String text = el.text().trim();
            if (!text.isEmpty()) sb.append(text).append("\n\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? doc.text().trim() : result;
    }

    private static Map<String, Object> parseStructuredData(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String fieldValueString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val == null ? "" : val.toString().trim();
    }

    /**
     * Resolves the round-trip schema from the entry's own Type instance — the
     * active {@code codex_type_field} rows of the parent category chapter (E8) —
     * rather than the retired system-global {@code codex_category} schema. Each
     * project owns its Type's fields, so a renamed or removed field affects only
     * that project's DOCX round-trip. Returns {@code null} when the Type has no
     * active fields, preserving the existing "plain title-plus-body" export/import
     * branch for schema-less types.
     */
    private CodexSchema resolveSchema(UUID typeId) throws SQLException {
        if (typeId == null) return null;
        List<CodexField> fields = codexTypeFieldDao.findActiveByType(typeId);
        if (fields == null || fields.isEmpty()) return null;
        return CodexSchema.builder().fields(fields).build();
    }

    private Scene requireScene(UUID sceneId) throws SQLException {
        return sceneDao.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Codex entry scene not found: " + sceneId));
    }

    private Chapter requireChapter(UUID chapterId) throws SQLException {
        if (chapterId == null) {
            throw new IllegalArgumentException("Scene has no parent chapter");
        }
        return chapterDao.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (String token : text.split("\\s+")) {
            if (!token.isEmpty()) count++;
        }
        return count;
    }
}
