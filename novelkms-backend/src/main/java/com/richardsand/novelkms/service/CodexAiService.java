package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.AiProviderException;
import com.richardsand.novelkms.ai.CodexFillRequest;
import com.richardsand.novelkms.ai.CodexFillResult;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.ai.AiCredentialDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.chapter.ChapterSummaryDao;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.ai.AiCredential;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.model.codex.CodexCategory;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexSchema;

import jakarta.ws.rs.core.Response.Status;

/**
 * Orchestrates AI-driven fill-in of a single codex entry. The service:
 * <ol>
 * <li>Resolves the entry's schema from its parent codex category.</li>
 * <li>Assembles manuscript context from chapter summaries (required; throws
 * {@code no_chapter_summaries} when none exist).</li>
 * <li>Assembles pinned codex entries as reference context.</li>
 * <li>Calls the resolved {@link AiProvider#fillCodexEntry} method.</li>
 * <li>Returns the {@link CodexFillResult} for the caller (resource) to relay
 * to the frontend without auto-saving, so the author reviews the
 * suggestions before they are committed.</li>
 * </ol>
 *
 * <p>
 * <b>Codex scope.</b> A codex is scoped to exactly one project (series-wide)
 * or one book — exactly one of {@code projectId}/{@code bookId} is set on the
 * {@link Codex} row. Manuscript context is assembled to match:
 * <ul>
 * <li><b>Book-scoped codex</b> ({@code bookId != null}): chapter summaries of
 * that one book, in reading order.</li>
 * <li><b>Project-scoped codex</b> ({@code projectId != null}): chapter
 * summaries of every book in the project, concatenated book by book — a
 * character or canon entry in a series-wide codex needs the whole series
 * as context, not one book.</li>
 * </ul>
 * If no chapter summaries exist in the relevant scope, a
 * {@link ReviewException} with key {@code no_chapter_summaries} is thrown so the
 * author knows to generate summaries first — otherwise the model has nothing to
 * work from and returns all-empty fields.
 */
public class CodexAiService {

    private static final Logger       logger = LoggerFactory.getLogger(CodexAiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Maximum characters of chapter summary text sent as manuscript context. */
    private static final int MAX_MANUSCRIPT_CHARS = 40_000;

    private final SceneDao                sceneDao;
    private final ChapterDao              chapterDao;
    private final BookDao                 bookDao;
    private final CodexDao                codexDao;
    private final CodexCategoryDao        codexCategoryDao;
    private final AiCredentialDao         credentialDao;
    private final ChapterSummaryDao       chapterSummaryDao;
    private final Map<String, AiProvider> providers;

    public CodexAiService(SceneDao sceneDao, ChapterDao chapterDao, BookDao bookDao,
            CodexDao codexDao, CodexCategoryDao codexCategoryDao,
            AiCredentialDao credentialDao, ChapterSummaryDao chapterSummaryDao,
            Map<String, AiProvider> providers) {
        this.sceneDao = sceneDao;
        this.chapterDao = chapterDao;
        this.bookDao = bookDao;
        this.codexDao = codexDao;
        this.codexCategoryDao = codexCategoryDao;
        this.credentialDao = credentialDao;
        this.chapterSummaryDao = chapterSummaryDao;
        this.providers = providers;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generates AI-suggested field values and a body description for the given
     * codex entry scene and returns them without saving.
     *
     * @param userId       the authenticated user requesting the fill
     * @param sceneId      the codex entry scene to fill
     * @param credentialId optional specific credential to use; null uses the
     *                     user's default
     * @param userGuidance optional one-time author note for this single call
     * @return suggested fields and body — the caller/resource passes these to
     *         the frontend, which applies them to the form and triggers autosave
     * @throws SQLException    if a DAO operation fails
     * @throws ReviewException for all configuration/validation problems, using
     *                         the same exception type as AiReviewService for
     *                         consistent resource error handling
     */
    public CodexFillResult fillCodexEntry(UUID userId, UUID sceneId,
            UUID credentialId, String userGuidance) throws SQLException {

        logger.info("Codex AI fill started: userId={}, sceneId={}, credentialId={}",
                userId, sceneId, credentialId);

        // ── Load the entry and its hierarchy ───────────────────────────────
        Scene scene = sceneDao.findById(sceneId)
                .orElseThrow(() -> new ReviewException(Status.BAD_REQUEST,
                        "not_found", "Codex entry not found."));

        UUID    chapterId = scene.getChapterId();
        Chapter chapter   = chapterId == null ? null
                : chapterDao.findById(chapterId).orElse(null);
        if (chapter == null || chapter.getCodexId() == null) {
            throw new ReviewException(Status.BAD_REQUEST, "not_codex_entry",
                    "This scene is not a codex entry — AI fill is only available for codex entries.");
        }

        Codex codex = codexDao.findById(chapter.getCodexId())
                .orElseThrow(() -> new ReviewException(Status.BAD_REQUEST,
                        "not_found", "The entry's codex could not be found."));

        // ── Resolve category and schema ─────────────────────────────────────
        String        categoryKey   = chapter.getCodexCategory();
        CodexCategory category      = resolveCategory(categoryKey);
        CodexSchema   schema        = category != null ? category.getSchema() : null;
        String        categoryLabel = category != null && category.getLabel() != null
                ? category.getLabel()
                : (categoryKey != null ? categoryKey : "Codex Entry");

        // ── Resolve AI credential and provider ──────────────────────────────
        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported.");
        }
        String model  = firstNonBlank(credential.getDefaultModel(), provider.defaultModel());
        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReviewException(Status.CONFLICT, "no_ai_credential",
                    "Stored API key could not be read.");
        }

        // ── Assemble manuscript context from chapter summaries ───────────────
        // A codex is scoped to either one book or a whole project (series-wide).
        // Book-scoped: summaries of that book. Project-scoped: summaries of every
        // book in the project. (The previous version only handled book scope, so
        // a project-scoped codex — the common case for series-wide characters —
        // received no context and the model returned all-empty fields.)
        String manuscriptContext = assembleManuscriptContext(codex, credential.getProvider());

        if (manuscriptContext == null) {
            throw new ReviewException(Status.BAD_REQUEST, "no_chapter_summaries",
                    "Chapter summaries are required for AI codex fill, and none were found for this "
                            + (codex.getBookId() != null ? "book" : "project") + ". Please generate chapter "
                            + "summaries for your manuscript chapters first (right-click a chapter → "
                            + "Generate chapter summary), then try again.");
        }

        // ── Assemble pinned codex reference context ─────────────────────────
        String referenceContext = assembleReferenceContext(chapter.getCodexId(),
                categoryKey, scene.getTitle());

        // ── Build prompt ingredients ────────────────────────────────────────
        String schemaDescription = buildSchemaDescription(schema);
        String existingFields    = buildExistingFieldsText(schema, scene.getStructuredData());
        String entryTitle        = scene.getTitle() != null && !scene.getTitle().isBlank()
                ? scene.getTitle().trim()
                : "Untitled";

        logger.debug("Codex AI fill context assembled: entryTitle={}, provider={}, model={}, "
                + "codexScope={}, schemaFields={}, manuscriptContextChars={}, referenceContextChars={}, "
                + "userGuidanceChars={}",
                entryTitle, credential.getProvider(), model,
                codex.getBookId() != null ? "BOOK" : "PROJECT",
                schema != null && schema.getFields() != null ? schema.getFields().size() : 0,
                manuscriptContext.length(),
                referenceContext == null ? 0 : referenceContext.length(),
                userGuidance == null ? 0 : userGuidance.length());

        CodexFillRequest req = new CodexFillRequest(
                apiKey, model, entryTitle, categoryLabel,
                schemaDescription, existingFields,
                manuscriptContext, referenceContext,
                blankToNull(userGuidance));

        // ── Call provider ───────────────────────────────────────────────────
        try {
            CodexFillResult result = provider.fillCodexEntry(req);
            logger.info("Codex AI fill completed: sceneId={}, provider={}, fieldCount={}",
                    sceneId, credential.getProvider(), result.fields().size());
            return result;
        } catch (AiProviderException e) {
            logger.error("Codex AI fill provider error: sceneId={}, provider={}, error={}",
                    sceneId, credential.getProvider(), e.getMessage());
            throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "provider_error", e.getMessage());
        }
    }

    // =========================================================================
    // Context assembly
    // =========================================================================

    /**
     * Assembles chapter summaries as manuscript context, matching the codex's
     * scope:
     * <ul>
     * <li>Book-scoped codex ({@code codex.bookId != null}): the summaries of
     * that one book.</li>
     * <li>Project-scoped codex ({@code codex.projectId != null}): the
     * summaries of every book in the project, concatenated book by book
     * under a book heading.</li>
     * </ul>
     * Returns null when no chapter has a summary in the relevant scope, so the
     * caller can require the author to generate summaries first. Text is capped
     * at {@link #MAX_MANUSCRIPT_CHARS}.
     */
    private String assembleManuscriptContext(Codex codex, String provider) throws SQLException {
        StringBuilder sb = new StringBuilder();

        if (codex.getBookId() != null) {
            // Book-scoped codex — one book's summaries.
            appendBookSummaries(sb, codex.getBookId(), null, provider);
        } else if (codex.getProjectId() != null) {
            // Project-scoped (series-wide) codex — every book in the project.
            List<Book> books     = bookDao.findByProjectId(codex.getProjectId());
            boolean    multiBook = books.size() > 1;
            for (Book book : books) {
                String bookHeading = multiBook
                        ? (book.getTitle() != null && !book.getTitle().isBlank()
                                ? book.getTitle().trim()
                                : "Untitled Book")
                        : null;
                appendBookSummaries(sb, book.getId(), bookHeading, provider);
            }
        }

        String result = sb.toString().strip();
        if (result.isEmpty())
            return null;

        if (result.length() > MAX_MANUSCRIPT_CHARS) {
            result = result.substring(0, MAX_MANUSCRIPT_CHARS) + "\n[Context truncated]";
        }
        return result;
    }

    /**
     * Appends one book's chapter summaries (in reading order) to {@code sb}.
     * When {@code bookHeading} is non-null (project-scoped, multi-book codex),
     * a "## Book Title" heading precedes the chapters so the model can tell
     * books apart. Chapters without a summary are skipped.
     */
    private void appendBookSummaries(StringBuilder sb, UUID bookId, String bookHeading,
            String provider) throws SQLException {
        List<ChapterSummaryDao.Row> rows = chapterSummaryDao.bookChapterSummaries(bookId, provider);

        boolean wroteHeading = false;
        for (ChapterSummaryDao.Row row : rows) {
            if (row.content() == null || row.content().isBlank())
                continue;

            if (bookHeading != null && !wroteHeading) {
                sb.append("## ").append(bookHeading).append("\n\n");
                wroteHeading = true;
            }

            String label = (row.title() != null && !row.title().isBlank())
                    ? "Chapter " + row.chapterNumber() + ": " + row.title()
                    : "Chapter " + row.chapterNumber();
            sb.append(label).append("\n");
            sb.append(htmlToPlainText(row.content())).append("\n\n");
        }
    }

    /**
     * Assembles pinned codex entries from the same codex as reference context,
     * excluding the entry being filled (to avoid circular self-reference).
     * Returns null when no pinned entries have content.
     */
    private String assembleReferenceContext(UUID codexId, String excludedCategoryKey,
            String excludedTitle) throws SQLException {
        List<CodexDao.AiContextEntry> entries = codexDao.listPinnedAiContextEntries(codexId);
        if (entries.isEmpty())
            return null;

        // Pre-load schemas for structured field rendering
        Map<String, CodexSchema> schemaByKey = new HashMap<>();
        for (CodexCategory cat : codexCategoryDao.findAll()) {
            if (cat.getSchema() != null)
                schemaByKey.put(cat.getCategoryKey(), cat.getSchema());
        }

        StringBuilder sb      = new StringBuilder();
        String        lastCat = null;
        for (CodexDao.AiContextEntry e : entries) {
            // Skip the entry being filled if it is itself pinned
            if (excludedTitle != null
                    && excludedCategoryKey != null
                    && excludedCategoryKey.equals(e.categoryKey())
                    && excludedTitle.equalsIgnoreCase(e.title())) {
                continue;
            }

            String structured = renderStructuredFields(schemaByKey.get(e.categoryKey()),
                    e.structuredData());
            String plain      = htmlToPlainText(e.content());
            if (structured.isBlank() && plain.isBlank())
                continue;

            String cat = (e.categoryTitle() != null && !e.categoryTitle().isBlank())
                    ? e.categoryTitle().trim()
                    : "Codex";
            if (!cat.equals(lastCat)) {
                sb.append("# ").append(cat).append("\n\n");
                lastCat = cat;
            }

            String title = (e.title() != null && !e.title().isBlank())
                    ? e.title().trim()
                    : "Untitled";
            sb.append("=== ").append(title).append(" ===\n");
            if (!structured.isBlank())
                sb.append(structured).append("\n");
            if (!plain.isBlank())
                sb.append(plain).append("\n");
            sb.append("\n");
        }

        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    private String renderStructuredFields(CodexSchema schema, String structuredJson) {
        if (schema == null || schema.getFields() == null
                || structuredJson == null || structuredJson.isBlank()) {
            return "";
        }
        Map<String, Object> values;
        try {
            values = MAPPER.readValue(structuredJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CodexField field : schema.getFields()) {
            if (!field.isFeedsAi())
                continue;
            Object raw   = values.get(field.getKey());
            String value = raw == null ? "" : raw.toString().trim();
            if (value.isEmpty())
                continue;
            String label = field.getLabel() != null ? field.getLabel() : field.getKey();
            sb.append(label).append(": ").append(value).append("\n");
        }
        return sb.toString().strip();
    }

    // =========================================================================
    // Prompt building helpers
    // =========================================================================

    /**
     * Builds a human-readable description of the schema fields that the AI
     * should fill in. Includes field key, label, type, valid options (for
     * SELECT), and help text.
     */
    private static String buildSchemaDescription(CodexSchema schema) {
        if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
            return "(no structured fields — provide a body description only)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Structured fields to fill in (use the exact field key names in your JSON response):\n");
        for (CodexField field : schema.getFields()) {
            sb.append("- ").append(field.getKey())
                    .append(" (").append(field.getLabel() != null ? field.getLabel() : field.getKey())
                    .append("): ").append(field.getType());
            if ("SELECT".equals(field.getType())
                    && field.getOptions() != null && !field.getOptions().isEmpty()) {
                sb.append(" — valid options: ").append(String.join(", ", field.getOptions()));
            }
            if (field.getHelp() != null && !field.getHelp().isBlank()) {
                sb.append(" — ").append(field.getHelp().strip());
            }
            sb.append("\n");
        }
        return sb.toString().strip();
    }

    /**
     * Formats the entry's currently-stored field values as a human-readable
     * block so the AI knows what the author has already written and must not
     * contradict.
     */
    private static String buildExistingFieldsText(CodexSchema schema, String structuredJson) {
        if (schema == null || schema.getFields() == null
                || structuredJson == null || structuredJson.isBlank()) {
            return "none";
        }
        Map<String, Object> data;
        try {
            data = MAPPER.readValue(structuredJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (CodexField field : schema.getFields()) {
            Object val = data.get(field.getKey());
            if (val == null)
                continue;
            String text = val.toString().trim();
            if (text.isEmpty() || "_removedFields".equals(field.getKey()))
                continue;
            String label = field.getLabel() != null ? field.getLabel() : field.getKey();
            sb.append(label).append(": ").append(text).append("\n");
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? "none" : result;
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private CodexCategory resolveCategory(String categoryKey) throws SQLException {
        if (categoryKey == null || categoryKey.isBlank())
            return null;
        for (CodexCategory cat : codexCategoryDao.findAll()) {
            if (categoryKey.equals(cat.getCategoryKey()))
                return cat;
        }
        return null;
    }

    private AiCredential resolveCredential(UUID userId, UUID credentialId) throws SQLException {
        if (credentialId != null) {
            return credentialDao.findById(credentialId, userId)
                    .orElseThrow(() -> new ReviewException(Status.CONFLICT,
                            "credential_not_found", "The selected AI credential was not found."));
        }
        return credentialDao.findDefault(userId)
                .orElseThrow(() -> new ReviewException(Status.CONFLICT,
                        "no_ai_credential", "No AI provider key is configured. Add one in Settings."));
    }

    private static String htmlToPlainText(String html) {
        if (html == null || html.isBlank())
            return "";
        Document      doc = Jsoup.parse(html);
        StringBuilder sb  = new StringBuilder();
        for (Element el : doc.body().select("p, h1, h2, h3, h4, blockquote, li")) {
            String text = el.text().trim();
            if (!text.isEmpty())
                sb.append(text).append("\n\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? doc.text().trim() : result;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank())
                return v.trim();
        }
        return "";
    }

    private static String blankToNull(String value) {
        if (value == null)
            return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
