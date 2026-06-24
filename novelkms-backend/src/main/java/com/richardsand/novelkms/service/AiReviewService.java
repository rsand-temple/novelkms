package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.AiProviderException;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.ai.ReviewRequest;
import com.richardsand.novelkms.ai.ReviewResult;
import com.richardsand.novelkms.dao.AiCredentialDao;
import com.richardsand.novelkms.dao.AiFormInstructionsDao;
import com.richardsand.novelkms.dao.AiReviewDao;
import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.CodexCategoryDao;
import com.richardsand.novelkms.dao.CodexDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.AiCredential;
import com.richardsand.novelkms.model.AiReview;
import com.richardsand.novelkms.model.AiReviewRecommendation;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Codex;
import com.richardsand.novelkms.model.CodexCategory;
import com.richardsand.novelkms.model.Scene;

/**
 * Orchestrates a synchronous review: assembles the text to review (a whole
 * chapter's scenes, or a single scene), resolves the user's AI credential and
 * model, calls the provider, and persists the result as an immutable review
 * artifact. A chapter review and a scene review are the same artifact differing
 * only in scope — a scene review records its parent chapter in {@code chapterId}
 * (so it groups under the chapter's AI workflow) and the scene in
 * {@code sceneId}.
 *
 * <p>Configuration problems (no credential, non-manuscript target, empty target,
 * unsupported provider) are signalled via {@link ReviewException}; a provider
 * call failure is recorded as a FAILED review and returned normally so it
 * appears in history.
 */
public class AiReviewService {
    private static final Logger logger = LoggerFactory.getLogger(AiReviewService.class);

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
    private final CodexDao                codexDao;
    private final CodexCategoryDao        codexCategoryDao;
    private final Map<String, AiProvider> providers;

    public AiReviewService(ChapterDao chapterDao, SceneDao sceneDao, BookDao bookDao,
            AiCredentialDao credentialDao, AiReviewDao reviewDao,
            AiFormInstructionsDao formInstructionsDao,
            CodexDao codexDao, CodexCategoryDao codexCategoryDao,
            Map<String, AiProvider> providers) {
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.bookDao = bookDao;
        this.credentialDao = credentialDao;
        this.reviewDao = reviewDao;
        this.formInstructionsDao = formInstructionsDao;
        this.codexDao = codexDao;
        this.codexCategoryDao = codexCategoryDao;
        this.providers = providers;
    }

    /** Immutable description of what is being reviewed, resolved before execution. */
    private record ReviewTarget(UUID chapterId, UUID sceneId, UUID bookId,
                                String scopeWord, String unitLabel, String subtitle, String text) {}

    /**
     * Runs a chapter review and returns the resulting artifact (COMPLETED or
     * FAILED). Chapter ownership has already been enforced by the tenant filter
     * for the {@code chapters/{id}} path segment.
     *
     * @param credentialId  optional explicit credential; null = the user's default
     * @param modelOverride optional model override; null/blank = credential or provider default
     */
    public AiReview runChapterReview(UUID userId, UUID chapterId, UUID credentialId,
            String modelOverride) throws SQLException {
        Chapter chapter = chapterDao.findById(chapterId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "Chapter not found."));

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            throw new ReviewException(400, "not_manuscript",
                    "AI review is only available for manuscript chapters.");
        }

        String chapterText = assembleChapterText(chapterId);
        if (chapterText.isBlank()) {
            throw new ReviewException(400, "empty_chapter",
                    "This chapter has no text to review yet.");
        }

        ReviewTarget target = new ReviewTarget(chapterId, null, bookId,
                "chapter", chapterLabel(chapter), chapter.getSubtitle(), chapterText);
        return execute(userId, target, credentialId, modelOverride);
    }

    /**
     * Runs a review of a single scene and returns the resulting artifact. Scene
     * ownership has already been enforced by the tenant filter for the
     * {@code scenes/{id}} path segment. The review is filed under the scene's
     * parent chapter so it appears in that chapter's AI workflow.
     */
    public AiReview runSceneReview(UUID userId, UUID sceneId, UUID credentialId,
            String modelOverride) throws SQLException {
        Scene scene = sceneDao.findById(sceneId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "Scene not found."));

        UUID chapterId = scene.getChapterId();
        Chapter chapter = chapterId == null ? null : chapterDao.findById(chapterId).orElse(null);
        if (chapter == null) {
            throw new ReviewException(404, "not_found", "The scene's chapter was not found.");
        }

        UUID bookId = chapter.getBookId();
        if (bookId == null) {
            // A codex entry is stored as a scene under a codex (book_id null) chapter.
            throw new ReviewException(400, "not_manuscript",
                    "AI review is only available for manuscript scenes.");
        }

        String sceneText = htmlToPlainText(scene.getContent());
        if (sceneText.isBlank()) {
            throw new ReviewException(400, "empty_scene",
                    "This scene has no text to review yet.");
        }

        ReviewTarget target = new ReviewTarget(chapterId, sceneId, bookId,
                "scene", sceneLabel(scene), null, sceneText);
        return execute(userId, target, credentialId, modelOverride);
    }

    /**
     * Shared review pipeline: resolve credential/provider/model, create the
     * PENDING artifact, call the provider, and persist the outcome. Identical for
     * chapter and scene scope — only the {@link ReviewTarget} differs.
     */
    private AiReview execute(UUID userId, ReviewTarget target, UUID credentialId,
            String modelOverride) throws SQLException {
        AiCredential credential = resolveCredential(userId, credentialId);
        AiProvider   provider   = providers.get(credential.getProvider());
        if (provider == null) {
            throw new ReviewException(400, "unsupported_provider",
                    "Provider " + credential.getProvider() + " is not supported yet.");
        }

        String model     = firstNonBlank(modelOverride, credential.getDefaultModel(), provider.defaultModel());
        UUID   projectId = resolveProjectId(target.bookId());

        // Resolve the editorial "form" block (book -> project -> user -> system)
        // and record it on the review as immutable provenance.
        AiFormInstructionsDao.Resolved form =
                formInstructionsDao.resolveForReview(userId, projectId, target.bookId());

        UUID reviewId = reviewDao.createPending(userId, projectId, target.bookId(),
                target.chapterId(), target.sceneId(), provider.providerKey(), model,
                form.scope(), form.instructions());

        String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            reviewDao.failReview(reviewId, "Stored API key could not be read.");
            return reviewDao.findByIdForUser(reviewId, userId).orElseThrow();
        }

        ReviewRequest request = new ReviewRequest(
                apiKey, model, target.scopeWord(), target.unitLabel(), target.subtitle(),
                target.text(), DEFAULT_CATEGORIES, form.instructions());

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

    /**
     * Promotes a recommendation into the project Codex as a new entry, using the
     * model's suggested category and title. Idempotent: a recommendation already
     * promoted is returned unchanged. Returns the updated review (with
     * recommendations) so the panel can reflect the new promoted state.
     */
    public AiReview promoteRecommendation(UUID userId, UUID reviewId, UUID recId,
            String codexCategoryOverride,
            String codexTitleOverride) throws SQLException {
        AiReview review = reviewDao.findByIdForUser(reviewId, userId)
                .orElseThrow(() -> new ReviewException(404, "not_found", "Review not found."));

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
            throw new ReviewException(404, "recommendation_not_found", "Recommendation not found.");
        }
        if (rec.getPromotedSceneId() != null) {
            return review; // already promoted — idempotent
        }

        UUID projectId = review.getProjectId();
        if (projectId == null) {
            throw new ReviewException(400, "no_project", "This review is not associated with a project.");
        }

        String category = resolveCodexCategory(firstNonBlank(codexCategoryOverride, rec.getCodexCategory()));
        String title    = firstNonBlank(
                codexTitleOverride,
                rec.getCodexTitle(),
                truncate(rec.getRecommendation(), 80),
                "Untitled");
        String content  = "<p>" + escapeHtml(rec.getRecommendation()) + "</p>";

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
        // Seed the default category chapters, matching normal codex creation.
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
                    .orElseThrow(() -> new ReviewException(404, "credential_not_found",
                            "The selected AI credential was not found."));
        }
        return credentialDao.findDefault(userId)
                .orElseThrow(() -> new ReviewException(409, "no_ai_credential",
                        "No AI provider key is configured. Add one in Settings."));
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

    /**
     * Strips TipTap HTML to plain text, preserving block boundaries as blank
     * lines so the model sees paragraph structure. Inline image data URLs and
     * other markup are discarded.
     */
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
            // Fallback for content without recognized block elements.
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
