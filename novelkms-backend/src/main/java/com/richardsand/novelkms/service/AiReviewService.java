package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.richardsand.novelkms.ai.BookSummaryRequest;
import com.richardsand.novelkms.ai.EditorialRequest;
import com.richardsand.novelkms.ai.EditorialResult;
import com.richardsand.novelkms.ai.MemoryRequest;
import com.richardsand.novelkms.ai.MemoryResult;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.ai.ReviewRequest;
import com.richardsand.novelkms.ai.ReviewResult;
import com.richardsand.novelkms.ai.SummaryRequest;
import com.richardsand.novelkms.ai.SummaryResult;
import com.richardsand.novelkms.dao.MemoryTemplateDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.ai.AiCredentialDao;
import com.richardsand.novelkms.dao.ai.AiFormInstructionsDao;
import com.richardsand.novelkms.dao.ai.AiPromptTemplateDao;
import com.richardsand.novelkms.dao.ai.AiReviewDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.book.BookSummaryDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.chapter.ChapterEditorialDao;
import com.richardsand.novelkms.dao.chapter.ChapterMemoryDao;
import com.richardsand.novelkms.dao.chapter.ChapterSummaryDao;
import com.richardsand.novelkms.dao.codex.CodexCategoryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.ai.AiCredential;
import com.richardsand.novelkms.model.ai.AiReview;
import com.richardsand.novelkms.model.ai.AiReviewRecommendation;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.book.BookSummary;
import com.richardsand.novelkms.model.book.BookSummaryStatus;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.chapter.ChapterEditorial;
import com.richardsand.novelkms.model.chapter.ChapterMemory;
import com.richardsand.novelkms.model.chapter.ChapterMemoryStatus;
import com.richardsand.novelkms.model.chapter.ChapterSummary;
import com.richardsand.novelkms.model.chapter.ChapterSummaryStatus;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.model.codex.CodexCategory;
import com.richardsand.novelkms.model.codex.CodexField;
import com.richardsand.novelkms.model.codex.CodexSchema;

import jakarta.ws.rs.core.Response.Status;

/**
 * Orchestrates a synchronous review: assembles the text to review (a whole
 * chapter's scenes, or a single scene), resolves the user's AI credential and
 * model, calls the provider, and persists the result as an immutable review
 * artifact. A chapter review and a scene review are the same artifact differing
 * only in scope — a scene review records its parent chapter in {@code chapterId}
 * (so it groups under the chapter's AI workflow) and the scene in
 * {@code sceneId}.
 *
 * <p>
 * Configuration problems (no credential, non-manuscript target, empty target,
 * unsupported provider) are signalled via {@link ReviewException}; a provider
 * call failure is recorded as a FAILED review and returned normally so it
 * appears in history.
 */
public class AiReviewService {
    private static final Logger logger = LoggerFactory.getLogger(AiReviewService.class);

    /** Shared JSON mapper for parsing codex entry structured-field values. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default review categories sent to the provider. */
    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "Continuity", "Characterization", "Pacing", "Dialogue",
            "Historical Accuracy", "Technical Accuracy", "Clarity", "Grammar", "General Notes");

    private static final String SCENE_BREAK = "\n\n* * *\n\n";

    private final ChapterDao              chapterDao;
    private final SceneDao                sceneDao;
    private final BookDao                 bookDao;
    private final AiCredentialDao         credentialDao;
    private final AiReviewDao             reviewDao;
    private final AiFormInstructionsDao   formInstructionsDao;
    private final ChapterMemoryDao        chapterMemoryDao;
    private final MemoryTemplateDao       memoryTemplateDao;
    private final AiPromptTemplateDao     aiPromptTemplateDao;
    private final ChapterSummaryDao       chapterSummaryDao;
    private final ChapterEditorialDao     chapterEditorialDao;
    private final BookSummaryDao          bookSummaryDao;
    private final CodexDao                codexDao;
    private final CodexCategoryDao        codexCategoryDao;
    private final Map<String, AiProvider> providers;

    public AiReviewService(ChapterDao chapterDao, SceneDao sceneDao, BookDao bookDao,
            AiCredentialDao credentialDao, AiReviewDao reviewDao,
            AiFormInstructionsDao formInstructionsDao,
            ChapterMemoryDao chapterMemoryDao, MemoryTemplateDao memoryTemplateDao,
            AiPromptTemplateDao aiPromptTemplateDao,
            ChapterSummaryDao chapterSummaryDao, BookSummaryDao bookSummaryDao,
            ChapterEditorialDao chapterEditorialDao,
            CodexDao codexDao, CodexCategoryDao codexCategoryDao,
            Map<String, AiProvider> providers) {
        this.chapterDao           = chapterDao;
        this.sceneDao             = sceneDao;
        this.bookDao              = bookDao;
        this.credentialDao        = credentialDao;
        this.reviewDao            = reviewDao;
        this.formInstructionsDao  = formInstructionsDao;
        this.chapterMemoryDao     = chapterMemoryDao;
        this.memoryTemplateDao    = memoryTemplateDao;
        this.aiPromptTemplateDao  = aiPromptTemplateDao;
        this.chapterSummaryDao    = chapterSummaryDao;
        this.chapterEditorialDao  = chapterEditorialDao;
        this.bookSummaryDao       = bookSummaryDao;
        this.codexDao             = codexDao;
        this.codexCategoryDao     = codexCategoryDao;
        this.providers            = providers;
    }

    /** Immutable description of what is being reviewed, resolved before execution. */
    private record ReviewTarget(UUID chapterId, UUID sceneId, UUID bookId,
            String scopeWord, String unitLabel, String subtitle, String text,
            String priorContext, String referenceContext, String userGuidance) {
    }

    /**
     * Runs a chapter review and returns the resulting artifact (COMPLETED or
     * FAILED). Chapter ownership has already been enforced by the tenant filter
     * for the {@code chapters/{id}} path segment.
     *
     * @param credentialId        optional explicit credential; null = the user's default
     * @param modelOverride       optional model override; null/blank = credential or provider default
     * @param userGuidance        optional one-time author note for this run only; null/blank = none
     * @param includePinnedContext when true, pinned Codex entries (book + project)
     *                            are assembled into the review's reference context
     */
    public AiReview runChapterReview(UUID userId, UUID chapterId, UUID credentialId,
            String modelOverride, String userGuidance, boolean includePinnedContext) throws SQLException {
        logger.info("Starting AI chapter review: userId={}, chapterId={}, credentialId={}, modelOverride={}, includePinnedContext={}", userId, chapterId, credentialId, modelOverride, includePinnedContext);
        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "Chapter not found."));

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(Status.BAD_REQUEST, "not_manuscript",
                    "AI review is only available for manuscript chapters.");
        }

        String chapterText = assembleChapterText(chapterId);
        if (chapterText.isBlank()) {
            throw new ReviewException(Status.BAD_REQUEST, "empty_chapter",
                    "This chapter has no text to review yet.");
        }

        // Resolve the credential up front: its provider determines which preceding
        // chapters' memory documents are preferred when assembling prior context.
        AiCredential credential = resolveCredential(userId, credentialId);

        String priorContext     = assemblePriorContext(bookId, chapterId, credential.getProvider());
        String referenceContext = includePinnedContext ? assembleReferenceContext(bookId) : null;
        logger.debug("AI chapter review context assembled: chapterId={}, textChars={}, priorContextChars={}, referenceContextChars={}",
                chapterId, chapterText.length(), priorContext == null ? 0 : priorContext.length(), referenceContext == null ? 0 : referenceContext.length());
        ReviewTarget target = new ReviewTarget(chapterId, null, bookId,
                "chapter", chapterLabel(chapter), chapter.getSubtitle(), chapterText,
                priorContext, referenceContext, blankToNull(userGuidance));
        return execute(userId, target, credential, modelOverride);
    }

    /**
     * Runs a review of a single scene and returns the resulting artifact. Scene
     * ownership has already been enforced by the tenant filter for the
     * {@code scenes/{id}} path segment. The review is filed under the scene's
     * parent chapter so it appears in that chapter's AI workflow.
     *
     * @param userGuidance         optional one-time author note for this run only; null/blank = none
     * @param includePinnedContext when true, pinned Codex entries (book + project)
     *                             are assembled into the review's reference context
     */
    public AiReview runSceneReview(UUID userId, UUID sceneId, UUID credentialId,
            String modelOverride, String userGuidance, boolean includePinnedContext) throws SQLException {
        logger.info("Starting AI scene review: userId={}, sceneId={}, credentialId={}, modelOverride={}, includePinnedContext={}", userId, sceneId, credentialId, modelOverride, includePinnedContext);
        Scene scene = sceneDao.findById(sceneId)
                .orElseThrow(() -> new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "Scene not found."));

        UUID    chapterId = scene.getChapterId();
        Chapter chapter   = chapterId == null ? null : chapterDao.findById(chapterId).orElse(null);
        if (chapter == null) {
            throw new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "The scene's chapter was not found.");
        }

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(Status.PRECONDITION_REQUIRED, "not_manuscript",
                    "AI review is only available for manuscript scenes.");
        }

        String sceneText = htmlToPlainText(scene.getContent());
        if (sceneText.isBlank()) {
            throw new ReviewException(Status.BAD_REQUEST, "empty_scene",
                    "This scene has no text to review yet.");
        }

        String referenceContext = includePinnedContext ? assembleReferenceContext(bookId) : null;
        logger.debug("AI scene review context assembled: sceneId={}, chapterId={}, textChars={}, referenceContextChars={}",
                sceneId, chapterId, sceneText.length(), referenceContext == null ? 0 : referenceContext.length());
        AiCredential credential = resolveCredential(userId, credentialId);
        ReviewTarget target = new ReviewTarget(chapterId, sceneId, bookId,
                "scene", sceneLabel(scene), null, sceneText, null, referenceContext, blankToNull(userGuidance));
        return execute(userId, target, credential, modelOverride);
    }

    /**
     * Shared review pipeline: resolve provider/model from the already-resolved
     * credential, create the PENDING artifact, call the provider, and persist the
     * outcome. Identical for chapter and scene scope — only the
     * {@link ReviewTarget} differs. The caller resolves the credential first so a
     * chapter review can prefer that provider's memory documents for prior context.
     */
    private AiReview execute(UUID userId, ReviewTarget target, AiCredential credential,
            String modelOverride) throws SQLException {
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }

        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = resolveProjectId(target.bookId());

        AiFormInstructionsDao.Resolved form = formInstructionsDao.resolveForReview(userId, projectId, target.bookId());

        logger.debug("Resolved AI review execution context: userId={}, projectId={}, bookId={}, chapterId={}, sceneId={}, provider={}, model={}, formScope={}",
                userId, projectId, target.bookId(), target.chapterId(), target.sceneId(), provider.providerKey(), model, form.scope());

        UUID reviewId = reviewDao.createPending(userId, projectId, target.bookId(),
                target.chapterId(), target.sceneId(), provider.providerKey(), model,
                form.scope(), form.instructions(), target.userGuidance());
        logger.info("Created pending AI review: reviewId={}, scope={}, chapterId={}, sceneId={}, provider={}, model={}", reviewId, target.scopeWord(), target.chapterId(), target.sceneId(),
                provider.providerKey(), model);

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("AI review {} failed before provider call: decrypted API key unavailable", reviewId);
            reviewDao.failReview(reviewId, "Stored API key could not be read.");
            return reviewDao.findByIdForUser(reviewId, userId).orElseThrow();
        }

        ReviewRequest request = new ReviewRequest(
                apiKey, model, target.scopeWord(), target.unitLabel(), target.subtitle(),
                target.text(), DEFAULT_CATEGORIES, form.instructions(),
                target.priorContext(), target.referenceContext(), target.userGuidance());

        try {
            ReviewResult                        result = provider.review(request);
            List<AiReviewDao.NewRecommendation> recs   = new ArrayList<>();
            for (ReviewResult.Recommendation r : result.recommendations()) {
                recs.add(new AiReviewDao.NewRecommendation(
                        r.category(), r.severity(), r.location(), r.recommendation(),
                        r.codexCategory(), r.codexTitle(), r.anchorText()));
            }
            reviewDao.completeReview(reviewId, result.promptVersion(), result.rawJson(), recs);
        } catch (AiProviderException e) {
            logger.warn("AI review {} failed: {}", reviewId, e.getMessage());
            reviewDao.failReview(reviewId, e.getMessage());
        }

        return reviewDao.findByIdForUser(reviewId, userId).orElseThrow();
    }

    // ── Memory documents ──────────────────────────────────────────────────────

    /**
     * Generates (or regenerates) the memory document for a chapter by filling the
     * resolved memory template from the chapter's prose, then stores it (one
     * document per chapter, overwriting). Chapter ownership is enforced upstream
     * by the tenant filter (chapters/{id}).
     *
     * @param userGuidance optional one-time author note for this generation only;
     *                     null/blank = none
     */
    public ChapterMemory generateChapterMemory(UUID userId, UUID chapterId, UUID credentialId,
            String modelOverride, String userGuidance) throws SQLException {
        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "Chapter not found."));

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(Status.BAD_REQUEST, "not_manuscript",
                    "Memory documents are only available for manuscript chapters.");
        }

        String chapterText = assembleChapterText(chapterId);
        if (chapterText.isBlank()) {
            throw new ReviewException(Status.PRECONDITION_REQUIRED, "empty_chapter",
                    "This chapter has no text to summarize yet.");
        }

        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }
        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = resolveProjectId(bookId);

        String template = memoryTemplateDao.resolveForGeneration(userId, projectId, bookId).content();

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReviewException(Status.CONFLICT, "no_ai_credential", "Stored API key could not be read.");
        }

        String        note = blankToNull(userGuidance);
        MemoryRequest req  = new MemoryRequest(apiKey, model, chapterLabel(chapter), chapterText, template, note);
        try {
            MemoryResult result = provider.generateMemory(req);
            chapterMemoryDao.upsertGenerated(chapterId, provider.providerKey(), result.content(),
                    result.promptVersion(), model, note);
        } catch (AiProviderException e) {
            logger.error("Memory generation for chapter {} failed: {}", chapterId, e.getMessage());
            throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "memory_provider_error", e.getMessage());
        }
        return chapterMemoryDao.findByChapter(chapterId, provider.providerKey()).orElseThrow();
    }

    /** Returns the chapter's memory document for exactly the given provider, if any. */
    public Optional<ChapterMemory> getChapterMemory(UUID chapterId, String provider) throws SQLException {
        return chapterMemoryDao.findByChapter(chapterId, provider);
    }

    /**
     * Returns the chapter's preferred memory document — the {@code preferredProvider}
     * variant if present, else the most-recently-updated variant of any provider.
     */
    public Optional<ChapterMemory> getPreferredChapterMemory(UUID chapterId, String preferredProvider)
            throws SQLException {
        return chapterMemoryDao.findPreferred(chapterId, preferredProvider);
    }

    /** Lists every provider variant of the chapter's memory document (newest first). */
    public List<ChapterMemory> listChapterMemoryVariants(UUID chapterId) throws SQLException {
        return chapterMemoryDao.findAllByChapter(chapterId);
    }

    /** Saves an author edit to an existing memory document for the given provider (marks it EDITED). */
    public ChapterMemory editChapterMemory(UUID chapterId, String provider, String content) throws SQLException {
        if (!chapterMemoryDao.updateEdited(chapterId, provider, content)) {
            throw new ReviewException(Status.PRECONDITION_REQUIRED, "not_found",
                    "This chapter has no memory document to edit for that provider. Generate one first.");
        }
        return chapterMemoryDao.findByChapter(chapterId, provider).orElseThrow();
    }

    /** Clears a chapter's memory document for the given provider. Returns false if there was none. */
    public boolean deleteChapterMemory(UUID chapterId, String provider) throws SQLException {
        return chapterMemoryDao.delete(chapterId, provider);
    }

    // ── Editorials ────────────────────────────────────────────────────────────
    //
    // An editorial is a short, author-facing editorial reading of one chapter
    // (tone, genre drift, character arcs, storyline evolution). It is generated
    // from the chapter prose plus the SAME context a chapter review uses — the
    // preceding chapters' memory documents ("story so far") and, when opted in,
    // pinned Codex reference entries — but, unlike a memory document, its output
    // is never consumed by any other AI function. One per chapter, overwrite on
    // regenerate, no book-wide aggregate or staleness view.

    /**
     * Generates (or regenerates) the editorial for a chapter from its prose and
     * the same prior/reference context a chapter review uses, then stores it (one
     * editorial per chapter, overwriting). Chapter ownership is enforced upstream
     * by the tenant filter (chapters/{id}).
     *
     * @param userGuidance         optional one-time author note for this generation only; null/blank = none
     * @param includePinnedContext when true, pinned Codex entries (book + project)
     *                             are assembled into the editorial's reference context
     */
    public ChapterEditorial generateChapterEditorial(UUID userId, UUID chapterId, UUID credentialId,
            String modelOverride, String userGuidance, boolean includePinnedContext) throws SQLException {
        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "Chapter not found."));

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(Status.BAD_REQUEST, "not_manuscript",
                    "Editorials are only available for manuscript chapters.");
        }

        String chapterText = assembleChapterText(chapterId);
        if (chapterText.isBlank()) {
            throw new ReviewException(Status.BAD_REQUEST, "empty_chapter",
                    "This chapter has no text to give an editorial on yet.");
        }

        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }
        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = resolveProjectId(bookId);

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReviewException(Status.CONFLICT, "no_ai_credential", "Stored API key could not be read.");
        }

        String priorContext     = assemblePriorContext(bookId, chapterId, provider.providerKey());
        String referenceContext = includePinnedContext ? assembleReferenceContext(bookId) : null;
        logger.debug("Editorial context assembled: chapterId={}, textChars={}, priorContextChars={}, referenceContextChars={}",
                chapterId, chapterText.length(), priorContext == null ? 0 : priorContext.length(),
                referenceContext == null ? 0 : referenceContext.length());

        // Resolve the editorial system-prompt template from the cascade.
        String systemPrompt = aiPromptTemplateDao.resolveForGeneration(
                AiPromptTemplateDao.TemplateType.EDITORIAL, userId, projectId, bookId).content();

        String           note = blankToNull(userGuidance);
        EditorialRequest req  = new EditorialRequest(apiKey, model, chapterLabel(chapter), chapter.getSubtitle(),
                chapterText, priorContext, referenceContext, note, systemPrompt);
        try {
            EditorialResult result = provider.generateEditorial(req);
            chapterEditorialDao.upsertGenerated(chapterId, provider.providerKey(), result.content(),
                    result.promptVersion(), model, note);
        } catch (AiProviderException e) {
            logger.error("Editorial generation for chapter {} failed: {}", chapterId, e.getMessage());
            throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "editorial_provider_error", e.getMessage());
        }
        return chapterEditorialDao.findByChapter(chapterId, provider.providerKey()).orElseThrow();
    }

    /** Returns the chapter's editorial for exactly the given provider, if any. */
    public Optional<ChapterEditorial> getChapterEditorial(UUID chapterId, String provider) throws SQLException {
        return chapterEditorialDao.findByChapter(chapterId, provider);
    }

    /**
     * Returns the chapter's preferred editorial — the {@code preferredProvider}
     * variant if present, else the most-recently-updated variant of any provider.
     */
    public Optional<ChapterEditorial> getPreferredChapterEditorial(UUID chapterId, String preferredProvider)
            throws SQLException {
        return chapterEditorialDao.findPreferred(chapterId, preferredProvider);
    }

    /** Lists every provider variant of the chapter's editorial (newest first). */
    public List<ChapterEditorial> listChapterEditorialVariants(UUID chapterId) throws SQLException {
        return chapterEditorialDao.findAllByChapter(chapterId);
    }

    /** Saves an author edit to an existing editorial for the given provider (marks it EDITED). */
    public ChapterEditorial editChapterEditorial(UUID chapterId, String provider, String content) throws SQLException {
        if (!chapterEditorialDao.updateEdited(chapterId, provider, content)) {
            throw new ReviewException(Status.PRECONDITION_REQUIRED, "not_found",
                    "This chapter has no editorial to edit for that provider. Generate one first.");
        }
        return chapterEditorialDao.findByChapter(chapterId, provider).orElseThrow();
    }

    /** Clears a chapter's editorial for the given provider. Returns false if there was none. */
    public boolean deleteChapterEditorial(UUID chapterId, String provider) throws SQLException {
        return chapterEditorialDao.delete(chapterId, provider);
    }

    /**
     * Reports the memory-document status of every manuscript chapter in the book,
     * in linear book order, for the pre-review warning.
     */
    public List<ChapterMemoryStatus> bookMemoryStatus(UUID bookId, String provider) throws SQLException {
        List<ChapterMemoryDao.Row> rows                = chapterMemoryDao.bookChapterMemory(bookId, provider);
        List<ChapterMemoryStatus>  result              = new ArrayList<>();
        Instant                    maxEarlierGenerated = null;
        for (ChapterMemoryDao.Row row : rows) {
            String state = stateFor(row, maxEarlierGenerated);
            result.add(new ChapterMemoryStatus(
                    row.chapterId(), row.chapterNumber(), row.title(),
                    row.hasDoc(), row.generatedAt(), row.contentEditedAt(),
                    row.source(), state));
            if (row.generatedAt() != null
                    && (maxEarlierGenerated == null || row.generatedAt().isAfter(maxEarlierGenerated))) {
                maxEarlierGenerated = row.generatedAt();
            }
        }
        return result;
    }

    private String stateFor(ChapterMemoryDao.Row row, Instant maxEarlierGenerated) {
        if (row.generatedAt() == null) {
            return ChapterMemoryStatus.MISSING;
        }
        if (row.contentEditedAt() != null && row.generatedAt().isBefore(row.contentEditedAt())) {
            return ChapterMemoryStatus.STALE_CONTENT;
        }
        if (maxEarlierGenerated != null && row.generatedAt().isBefore(maxEarlierGenerated)) {
            return ChapterMemoryStatus.OUT_OF_SEQUENCE;
        }
        return ChapterMemoryStatus.OK;
    }

    private String assemblePriorContext(UUID bookId, UUID chapterId, String provider) throws SQLException {
        List<ChapterMemoryDao.Row> rows      = chapterMemoryDao.bookChapterMemory(bookId, provider);
        int                        targetSeq = Integer.MAX_VALUE;
        for (ChapterMemoryDao.Row row : rows) {
            if (row.chapterId().equals(chapterId)) {
                targetSeq = row.seq();
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ChapterMemoryDao.Row row : rows) {
            if (row.seq() >= targetSeq)
                continue;
            String plainContent = htmlToPlainText(row.content());
            if (plainContent.isBlank())
                continue;
            String title = (row.title() == null || row.title().isBlank())
                    ? "Chapter " + row.chapterNumber()
                    : row.title().trim();
            sb.append("=== Chapter ").append(row.chapterNumber()).append(": ").append(title);
            if (row.generatedAt() != null) {
                sb.append(" (memory generated ").append(row.generatedAt()).append(")");
            }
            sb.append(" ===\n").append(plainContent).append("\n\n");
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    // ── Pinned Codex reference context ────────────────────────────────────────

    /** Aggregate counts for the per-book pinned-context summary the review UI shows. */
    public record PinnedContextSummary(int entryCount, int wordCount) {
    }

    private String assembleReferenceContext(UUID bookId) throws SQLException {
        List<CodexDao.AiContextEntry> entries = collectPinnedEntries(bookId);
        if (entries.isEmpty()) {
            return null;
        }
        Map<String, CodexSchema> schemaByKey = schemasByCategoryKey();
        StringBuilder            sb          = new StringBuilder();
        String                   lastCat     = null;
        for (CodexDao.AiContextEntry e : entries) {
            String structured = renderStructuredFields(schemaByKey.get(e.categoryKey()), e.structuredData());
            String plain      = htmlToPlainText(e.content());
            if (structured.isBlank() && plain.isBlank()) {
                continue;
            }
            String category = (e.categoryTitle() == null || e.categoryTitle().isBlank())
                    ? "Codex" : e.categoryTitle().trim();
            if (!category.equals(lastCat)) {
                sb.append("# ").append(category).append("\n\n");
                lastCat = category;
            }
            String title = (e.title() == null || e.title().isBlank()) ? "Untitled" : e.title().trim();
            sb.append("=== ").append(title).append(" ===\n");
            if (!structured.isBlank()) {
                sb.append(structured).append("\n");
            }
            if (!plain.isBlank()) {
                sb.append(plain).append("\n");
            }
            sb.append("\n");
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    private Map<String, CodexSchema> schemasByCategoryKey() throws SQLException {
        Map<String, CodexSchema> byKey = new HashMap<>();
        for (CodexCategory cat : codexCategoryDao.findAll()) {
            if (cat.getSchema() != null) {
                byKey.put(cat.getCategoryKey(), cat.getSchema());
            }
        }
        return byKey;
    }

    private String renderStructuredFields(CodexSchema schema, String structuredJson) {
        if (schema == null || schema.getFields() == null
                || structuredJson == null || structuredJson.isBlank()) {
            return "";
        }
        Map<String, Object> values;
        try {
            values = MAPPER.readValue(structuredJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            logger.warn("Ignoring malformed scene.structured_data JSON: {}", ex.getMessage());
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CodexField field : schema.getFields()) {
            if (!field.isFeedsAi()) {
                continue;
            }
            Object raw   = values.get(field.getKey());
            String value = raw == null ? "" : raw.toString().trim();
            if (value.isEmpty()) {
                continue;
            }
            String label = (field.getLabel() == null || field.getLabel().isBlank())
                    ? field.getKey() : field.getLabel().trim();
            sb.append(label).append(": ").append(value).append("\n");
        }
        return sb.toString().strip();
    }

    public PinnedContextSummary pinnedContextSummary(UUID bookId) throws SQLException {
        List<CodexDao.AiContextEntry> entries = collectPinnedEntries(bookId);
        int words = 0;
        for (CodexDao.AiContextEntry e : entries) {
            words += e.wordCount();
        }
        return new PinnedContextSummary(entries.size(), words);
    }

    private List<CodexDao.AiContextEntry> collectPinnedEntries(UUID bookId) throws SQLException {
        List<CodexDao.AiContextEntry> entries = new ArrayList<>();
        Optional<Codex> bookCodex = codexDao.findByBookId(bookId);
        if (bookCodex.isPresent()) {
            entries.addAll(codexDao.listPinnedAiContextEntries(bookCodex.get().getId()));
        }
        UUID projectId = resolveProjectId(bookId);
        if (projectId != null) {
            Optional<Codex> projectCodex = codexDao.findByProjectId(projectId);
            if (projectCodex.isPresent()) {
                entries.addAll(codexDao.listPinnedAiContextEntries(projectCodex.get().getId()));
            }
        }
        return entries;
    }

    // ── Chapter and book summaries ────────────────────────────────────────────
    //
    // A separate artifact family from memory documents: a chapter summary is one
    // readable paragraph; the book summary is built entirely from the chapter
    // summaries (in book order), never the manuscript prose, because a full book
    // is too large to summarize reliably in one pass. Generating a summary never
    // touches a memory document and vice versa.

    /** Hard ceiling on book-summary length, passed to the provider and noted in prompts. */
    public static final int BOOK_SUMMARY_MAX_WORDS = 1000;

    /**
     * Generates (or regenerates) the summary for a chapter from its prose, then
     * stores it (one summary per chapter, overwriting). Chapter ownership is
     * enforced upstream by the tenant filter (chapters/{id}).
     *
     * @param userGuidance optional one-time author note for this generation only;
     *                     null/blank = none
     */
    public ChapterSummary generateChapterSummary(UUID userId, UUID chapterId, UUID credentialId,
            String modelOverride, String userGuidance) throws SQLException {
        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(Status.PRECONDITION_REQUIRED, "not_found", "Chapter not found."));

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(Status.BAD_REQUEST, "not_manuscript",
                    "Summaries are only available for manuscript chapters.");
        }

        String chapterText = assembleChapterText(chapterId);
        if (chapterText.isBlank()) {
            throw new ReviewException(Status.BAD_REQUEST, "empty_chapter",
                    "This chapter has no text to summarize yet.");
        }

        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }
        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = resolveProjectId(bookId);

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReviewException(Status.CONFLICT, "no_ai_credential", "Stored API key could not be read.");
        }

        // Resolve the chapter-summary system-prompt template from the cascade.
        String systemPrompt = aiPromptTemplateDao.resolveForGeneration(
                AiPromptTemplateDao.TemplateType.CHAPTER_SUMMARY, userId, projectId, bookId).content();

        String         note = blankToNull(userGuidance);
        SummaryRequest req  = new SummaryRequest(apiKey, model, chapterLabel(chapter), chapterText, note, systemPrompt);
        try {
            SummaryResult result = provider.generateChapterSummary(req);
            chapterSummaryDao.upsertGenerated(chapterId, provider.providerKey(), result.content(),
                    result.promptVersion(), model, note);
        } catch (AiProviderException e) {
            logger.error("Chapter-summary generation for chapter {} failed: {}", chapterId, e.getMessage());
            throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "summary_provider_error", e.getMessage());
        }
        return chapterSummaryDao.findByChapter(chapterId, provider.providerKey()).orElseThrow();
    }

    /** Returns the chapter's summary for exactly the given provider, if any. */
    public Optional<ChapterSummary> getChapterSummary(UUID chapterId, String provider) throws SQLException {
        return chapterSummaryDao.findByChapter(chapterId, provider);
    }

    /**
     * Returns the chapter's preferred summary — the {@code preferredProvider}
     * variant if present, else the most-recently-updated variant of any provider.
     */
    public Optional<ChapterSummary> getPreferredChapterSummary(UUID chapterId, String preferredProvider)
            throws SQLException {
        return chapterSummaryDao.findPreferred(chapterId, preferredProvider);
    }

    /** Lists every provider variant of the chapter's summary (newest first). */
    public List<ChapterSummary> listChapterSummaryVariants(UUID chapterId) throws SQLException {
        return chapterSummaryDao.findAllByChapter(chapterId);
    }

    /** Saves an author edit to an existing chapter summary for the given provider (marks it EDITED). */
    public ChapterSummary editChapterSummary(UUID chapterId, String provider, String content) throws SQLException {
        if (!chapterSummaryDao.updateEdited(chapterId, provider, content)) {
            throw new ReviewException(Status.BAD_REQUEST, "not_found",
                    "This chapter has no summary to edit for that provider. Generate one first.");
        }
        return chapterSummaryDao.findByChapter(chapterId, provider).orElseThrow();
    }

    /** Clears a chapter's summary for the given provider. Returns false if there was none. */
    public boolean deleteChapterSummary(UUID chapterId, String provider) throws SQLException {
        return chapterSummaryDao.delete(chapterId, provider);
    }

    /**
     * Returns every manuscript chapter of the book in linear book order with its
     * summary text and per-chapter staleness state.
     */
    public List<ChapterSummaryStatus> bookChapterSummaries(UUID bookId, String provider) throws SQLException {
        List<ChapterSummaryDao.Row> rows   = chapterSummaryDao.bookChapterSummaries(bookId, provider);
        List<ChapterSummaryStatus>  result = new ArrayList<>();
        for (ChapterSummaryDao.Row row : rows) {
            result.add(new ChapterSummaryStatus(
                    row.chapterId(), row.chapterNumber(), row.title(),
                    row.hasDoc(), row.content(), row.generatedAt(), row.contentEditedAt(),
                    row.source(), chapterSummaryStateFor(row)));
        }
        return result;
    }

    private String chapterSummaryStateFor(ChapterSummaryDao.Row row) {
        if (row.generatedAt() == null) {
            return ChapterSummaryStatus.MISSING;
        }
        if (row.contentEditedAt() != null && row.generatedAt().isBefore(row.contentEditedAt())) {
            return ChapterSummaryStatus.STALE_CONTENT;
        }
        return ChapterSummaryStatus.OK;
    }

    /**
     * Generates (or regenerates) the book summary entirely from the book's chapter
     * summaries, concatenated in linear book order. Fails if no chapter has a
     * summary yet.
     *
     * @param userGuidance optional one-time author note for this generation only;
     *                     null/blank = none
     */
    public BookSummary generateBookSummary(UUID userId, UUID bookId, UUID credentialId,
            String modelOverride, String userGuidance) throws SQLException {
        Book book = bookDao.findById(bookId)
                .orElseThrow(() -> new ReviewException(Status.NO_CONTENT, "not_found", "Book not found."));

        // Resolve the credential/provider first: which chapter-summary variant each
        // chapter contributes depends on the book summary's own generating provider.
        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }
        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = book.getProjectId();

        // Each chapter contributes this provider's summary where present, else its
        // most-recently-updated summary of any provider (the agreed fallback).
        String aggregate = assembleChapterSummaries(bookId, provider.providerKey());
        if (aggregate == null || aggregate.isBlank()) {
            throw new ReviewException(Status.BAD_REQUEST, "no_chapter_summaries",
                    "No chapter summaries exist for this book yet. Generate chapter summaries first.");
        }

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ReviewException(Status.CONFLICT, "no_ai_credential", "Stored API key could not be read.");
        }

        // Resolve the book-summary system-prompt template from the cascade.
        String systemPrompt = aiPromptTemplateDao.resolveForGeneration(
                AiPromptTemplateDao.TemplateType.BOOK_SUMMARY, userId, projectId, bookId).content();

        String             note = blankToNull(userGuidance);
        BookSummaryRequest req  = new BookSummaryRequest(
                apiKey, model, book.getTitle(), aggregate, BOOK_SUMMARY_MAX_WORDS, note, systemPrompt);
        try {
            SummaryResult result    = provider.generateBookSummary(req);
            int           wordCount = countWords(result.content());
            bookSummaryDao.upsertGenerated(bookId, provider.providerKey(), result.content(), wordCount,
                    result.promptVersion(), model, note);
        } catch (AiProviderException e) {
            logger.warn("Book-summary generation for book {} failed: {}", bookId, e.getMessage());
            throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "summary_provider_error", e.getMessage());
        }
        return bookSummaryDao.findByBook(bookId, provider.providerKey()).orElseThrow();
    }

    /** Returns the book's summary for exactly the given provider, if any. */
    public Optional<BookSummary> getBookSummary(UUID bookId, String provider) throws SQLException {
        return bookSummaryDao.findByBook(bookId, provider);
    }

    /**
     * Returns the book's preferred summary — the {@code preferredProvider} variant
     * if present, else the most-recently-updated variant of any provider.
     */
    public Optional<BookSummary> getPreferredBookSummary(UUID bookId, String preferredProvider)
            throws SQLException {
        return bookSummaryDao.findPreferred(bookId, preferredProvider);
    }

    /** Lists every provider variant of the book's summary (newest first). */
    public List<BookSummary> listBookSummaryVariants(UUID bookId) throws SQLException {
        return bookSummaryDao.findAllByBook(bookId);
    }

    /** Saves an author edit to an existing book summary for the given provider (marks it EDITED; re-counts words). */
    public BookSummary editBookSummary(UUID bookId, String provider, String content) throws SQLException {
        if (!bookSummaryDao.updateEdited(bookId, provider, content, countWords(htmlToPlainText(content)))) {
            throw new ReviewException(Status.BAD_REQUEST, "not_found",
                    "This book has no summary to edit for that provider. Generate one first.");
        }
        return bookSummaryDao.findByBook(bookId, provider).orElseThrow();
    }

    /** Clears a book's summary for the given provider. Returns false if there was none. */
    public boolean deleteBookSummary(UUID bookId, String provider) throws SQLException {
        return bookSummaryDao.delete(bookId, provider);
    }

    /**
     * Reports the book-summary status plus chapter-summary coverage, for the
     * book-summary card and the pre-generation warning.
     */
    public BookSummaryStatus bookSummaryStatus(UUID bookId, String provider) throws SQLException {
        List<ChapterSummaryDao.Row> rows            = chapterSummaryDao.bookChapterSummaries(bookId, provider);
        int                         chapterCount    = rows.size();
        int                         summarizedCount = 0;
        int                         staleCount      = 0;
        Instant                     latestSummaryAt = null;
        for (ChapterSummaryDao.Row row : rows) {
            if (row.hasDoc()) {
                summarizedCount++;
                if (latestSummaryAt == null || row.generatedAt().isAfter(latestSummaryAt)) {
                    latestSummaryAt = row.generatedAt();
                }
                if (ChapterSummaryStatus.STALE_CONTENT.equals(chapterSummaryStateFor(row))) {
                    staleCount++;
                }
            }
        }
        int missingCount = chapterCount - summarizedCount;

        Optional<BookSummary> summary = bookSummaryDao.findByBook(bookId, provider);
        boolean               hasDoc  = summary.isPresent();
        boolean               stale   = false;
        if (hasDoc) {
            Instant generatedAt = summary.get().getGeneratedAt();
            stale = missingCount > 0
                    || staleCount > 0
                    || (latestSummaryAt != null && generatedAt != null
                            && latestSummaryAt.isAfter(generatedAt));
        }

        return new BookSummaryStatus(
                bookId,
                hasDoc,
                summary.map(BookSummary::getGeneratedAt).orElse(null),
                summary.map(BookSummary::getWordCount).orElse(0),
                summary.map(BookSummary::getSource).orElse(null),
                stale,
                chapterCount, summarizedCount, missingCount, staleCount);
    }

    private String assembleChapterSummaries(UUID bookId, String provider) throws SQLException {
        List<ChapterSummaryDao.Row> rows = chapterSummaryDao.bookChapterSummaries(bookId, provider);
        StringBuilder               sb   = new StringBuilder();
        for (ChapterSummaryDao.Row row : rows) {
            String plainContent = htmlToPlainText(row.content());
            if (plainContent.isBlank())
                continue;
            String title = (row.title() == null || row.title().isBlank())
                    ? "Chapter " + row.chapterNumber()
                    : row.title().trim();
            sb.append("=== Chapter ").append(row.chapterNumber()).append(": ").append(title)
                    .append(" ===\n").append(plainContent).append("\n\n");
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    public AiReview promoteRecommendation(UUID userId, UUID reviewId, UUID recId,
            String codexCategoryOverride,
            String codexTitleOverride,
            String codexNoteOverride) throws SQLException {
        AiReview review = reviewDao.findByIdForUser(reviewId, userId)
                .orElseThrow(() -> new ReviewException(Status.NO_CONTENT));

        AiReviewRecommendation rec = null;
        if (review.getRecommendations() != null) {
            for (AiReviewRecommendation r : review.getRecommendations()) {
                if (r.getId().equals(recId)) {
                    rec = r;
                    break;
                }
            }
        }
        if (rec == null) {
            throw new ReviewException(Status.BAD_REQUEST, "recommendation_not_found", "Recommendation not found.");
        }
        if (rec.getPromotedSceneId() != null) {
            return review; // already promoted — idempotent
        }

        UUID projectId = review.getProjectId();
        if (projectId == null) {
            throw new ReviewException(Status.BAD_REQUEST, "no_project", "This review is not associated with a project.");
        }

        // When the author edits the note text in the promotion dialog, use their
        // version; otherwise fall back to the stored recommendation text.
        String noteText  = (codexNoteOverride != null && !codexNoteOverride.isBlank())
                ? codexNoteOverride.trim() : rec.getRecommendation();
        String category = resolveCodexCategory(firstNonBlank(codexCategoryOverride, rec.getCodexCategory()));
        String title    = firstNonBlank(
                codexTitleOverride,
                rec.getCodexTitle(),
                truncate(noteText, 80),
                "Untitled");
        String content  = "<p>" + escapeHtml(noteText) + "</p>";

        Codex   codex           = getOrCreateProjectCodex(projectId);
        Chapter categoryChapter = getOrCreateCategoryChapter(codex.getId(), category);

        Scene entry = sceneDao.create(categoryChapter.getId(), title, null);
        sceneDao.saveContent(entry.getId(), content, countWords(rec.getRecommendation()));

        reviewDao.markPromoted(recId, reviewId, entry.getId());
        reviewDao.updateRecommendationStatus(recId, reviewId, "PROMOTED");
        return reviewDao.findByIdForUser(reviewId, userId).orElseThrow();
    }

    private Codex getOrCreateProjectCodex(UUID projectId) throws SQLException {
        Optional<Codex> existing = codexDao.findByProjectId(projectId);
        if (existing.isPresent())
            return existing.get();
        Codex codex = codexDao.createForProject(projectId, null);
        for (CodexCategory cat : codexCategoryDao.findDefaults()) {
            chapterDao.createCodexChapter(codex.getId(), cat.getCategoryKey(), cat.getLabel());
        }
        return codex;
    }

    private Chapter getOrCreateCategoryChapter(UUID codexId, String categoryKey) throws SQLException {
        for (Chapter ch : chapterDao.findByCodexId(codexId)) {
            if (categoryKey.equals(ch.getCodexCategory()))
                return ch;
        }
        return chapterDao.createCodexChapter(codexId, categoryKey, labelFor(categoryKey));
    }

    private String resolveCodexCategory(String suggested) throws SQLException {
        if (suggested != null && !suggested.isBlank()) {
            String key = suggested.trim();
            for (CodexCategory cat : codexCategoryDao.findAll()) {
                if (cat.getCategoryKey().equalsIgnoreCase(key))
                    return cat.getCategoryKey();
            }
        }
        return "NOTES";
    }

    private String labelFor(String categoryKey) throws SQLException {
        for (CodexCategory cat : codexCategoryDao.findAll()) {
            if (cat.getCategoryKey().equalsIgnoreCase(categoryKey))
                return cat.getLabel();
        }
        return categoryKey;
    }

    private AiCredential resolveCredential(UUID userId, UUID credentialId) throws SQLException {
        if (credentialId != null) {
            return credentialDao.findById(credentialId, userId)
                    .orElseThrow(() -> new ReviewException(Status.CONFLICT, "credential_not_found",
                            "The selected AI credential was not found."));
        }
        return credentialDao.findDefault(userId)
                .orElseThrow(() -> new ReviewException(Status.CONFLICT, "no_ai_credential",
                        "No AI provider key is configured. Add one in Settings."));
    }

    /**
     * Returns the provider key of the user's default credential (the "default
     * provider"), or null when the user has no active credential. Used to resolve
     * the preferred provider for reads and status when the caller did not specify
     * one explicitly.
     */
    public String defaultProviderKey(UUID userId) throws SQLException {
        return credentialDao.findDefault(userId).map(AiCredential::getProvider).orElse(null);
    }

    private UUID resolveProjectId(UUID bookId) throws SQLException {
        Optional<Book> book = bookDao.findById(bookId);
        return book.map(Book::getProjectId).orElse(null);
    }

    private String assembleChapterText(UUID chapterId) throws SQLException {
        List<Scene>  scenes = sceneDao.findByChapterId(chapterId);
        List<String> chunks = new ArrayList<>();
        for (Scene scene : scenes) {
            String text = htmlToPlainText(scene.getContent());
            if (!text.isBlank())
                chunks.add(text);
        }
        return String.join(SCENE_BREAK, chunks);
    }

    private String htmlToPlainText(String html) {
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
        if (result.isEmpty()) {
            result = doc.text().trim();
        }
        return result;
    }

    private String chapterLabel(Chapter chapter) {
        String title = chapter.getTitle();
        if (title != null && !title.isBlank())
            return title.trim();
        return "Chapter " + chapter.getChapterNumber();
    }

    private String sceneLabel(Scene scene) {
        String title = scene.getTitle();
        if (title != null && !title.isBlank())
            return title.trim();
        return "Untitled scene";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value.trim();
        }
        return "";
    }

    private static String blankToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).trim();
    }

    private static int countWords(String text) {
        if (text == null)
            return 0;
        String trimmed = text.trim();
        if (trimmed.isEmpty())
            return 0;
        return trimmed.split("\\s+").length;
    }

    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
