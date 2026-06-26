package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.TrashDao;
import com.richardsand.novelkms.dao.TrashDao.ChapterParents;
import com.richardsand.novelkms.dao.TrashDao.CodexOwner;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.TrashItem;

/**
 * The per-user trash can. Deleting an entity soft-deletes it and records a
 * {@code trash_batch} root; restore clears the flag (de-duplicating the title on
 * collision and appending to the end of the parent's order); purge permanently
 * removes the subtree.
 *
 * <p>The {@code trash*} entry points are invoked by the existing DELETE
 * endpoints (Project/Book/Chapter/Scene/AiReview resources), so "Delete" now
 * means "move to Trash" everywhere. Parts are not handled here — deleting a part
 * keeps its existing promote-and-hard-delete behavior.
 */
public class TrashService {

    private static final Logger logger = LoggerFactory.getLogger(TrashService.class);

    private final TrashDao    trashDao;
    private final ProjectDao  projectDao;
    private final BookDao     bookDao;
    private final ChapterDao  chapterDao;
    private final SceneDao    sceneDao;

    public TrashService(TrashDao trashDao, ProjectDao projectDao, BookDao bookDao,
            ChapterDao chapterDao, SceneDao sceneDao) {
        this.trashDao = trashDao;
        this.projectDao = projectDao;
        this.bookDao = bookDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
    }

    // =========================================================================
    // Trash (move to can)  — called from the entity DELETE endpoints
    // =========================================================================

    public Optional<TrashItem> trashProject(UUID userId, UUID id) throws SQLException {
        logger.info("Moving project to trash: userId={}, projectId={}", userId, id);
        return trashDao.trashProject(userId, id);
    }

    public Optional<TrashItem> trashBook(UUID userId, UUID id) throws SQLException {
        logger.info("Moving book to trash: userId={}, bookId={}", userId, id);
        return trashDao.trashBook(userId, id);
    }

    public Optional<TrashItem> trashChapter(UUID userId, UUID id) throws SQLException {
        logger.info("Moving chapter to trash: userId={}, chapterId={}", userId, id);
        return trashDao.trashChapter(userId, id);
    }

    public Optional<TrashItem> trashScene(UUID userId, UUID id) throws SQLException {
        logger.info("Moving scene to trash: userId={}, sceneId={}", userId, id);
        return trashDao.trashScene(userId, id);
    }

    public Optional<TrashItem> trashReview(UUID userId, UUID id) throws SQLException {
        logger.info("Moving AI review to trash: userId={}, reviewId={}", userId, id);
        return trashDao.trashReview(userId, id);
    }

    // =========================================================================
    // Listing
    // =========================================================================

    public List<TrashItem> list(UUID userId) throws SQLException {
        return trashDao.listForUser(userId);
    }

    // =========================================================================
    // Restore
    // =========================================================================

    /**
     * Restores the root of a trash batch. Throws {@link TrashException} 404 if
     * the batch is not the caller's, or 409 if the parent is itself trashed or
     * has been permanently removed (restore the parent first).
     */
    public TrashItem restore(UUID userId, UUID batchId) throws SQLException {
        logger.info("Restoring trash item: userId={}, batchId={}", userId, batchId);
        TrashItem batch = trashDao.findBatch(userId, batchId)
                .orElseThrow(() -> new TrashException(404, "not_found", "Trash item not found."));
        logger.debug("Resolved trash batch for restore: batchId={}, rootType={}, rootId={}", batchId, batch.getRootType(), batch.getRootId());

        switch (batch.getRootType()) {
            case "PROJECT"        -> restoreProject(userId, batch);
            case "BOOK"           -> restoreBook(batch);
            case "CHAPTER"        -> restoreManuscriptChapter(batch);
            case "CODEX_CATEGORY" -> restoreCodexCategory(batch);
            case "SCENE", "CODEX_ENTRY" -> restoreScene(batch);
            case "AI_REVIEW"      -> restoreReview(batch);
            default -> throw new TrashException(400, "unknown_type",
                    "Cannot restore unknown item type: " + batch.getRootType());
        }

        trashDao.deleteBatch(batchId);
        logger.info("Trash item restored: userId={}, batchId={}, rootType={}, rootId={}", userId, batchId, batch.getRootType(), batch.getRootId());
        return batch;
    }

    private void restoreProject(UUID userId, TrashItem batch) throws SQLException {
        List<Project> siblings = projectDao.findAllForUser(userId);
        String title = dedupe(batch.getRootTitle(), titles(siblings, Project::getTitle));
        trashDao.restoreProject(batch.getRootId(), title);
    }

    private void restoreBook(TrashItem batch) throws SQLException {
        UUID projectId = trashDao.bookProjectId(batch.getRootId())
                .orElseThrow(() -> gone("book"));
        if (projectDao.findById(projectId).isEmpty()) {
            throw parentTrashed("Restore the project first — it is in the trash.");
        }
        List<Book> siblings = bookDao.findByProjectId(projectId);
        String title = dedupe(batch.getRootTitle(), titles(siblings, Book::getTitle));
        int order = nextOrder(orders(siblings, Book::getDisplayOrder));
        trashDao.restoreBook(batch.getRootId(), title, order);
    }

    private void restoreManuscriptChapter(TrashItem batch) throws SQLException {
        ChapterParents parents = trashDao.chapterParents(batch.getRootId())
                .orElseThrow(() -> gone("chapter"));
        UUID bookId = parents.bookId();
        if (bookId == null || bookDao.findById(bookId).isEmpty()) {
            throw parentTrashed("Restore the book first — it is in the trash.");
        }
        // part_id may have been nulled by ON DELETE SET NULL while the chapter was
        // trashed; if it is still set, the part is alive (parts cascade with the book).
        List<Chapter> siblings = (parents.partId() != null)
                ? chapterDao.findByPartId(parents.partId())
                : chapterDao.findByBookId(bookId);
        String title = dedupe(batch.getRootTitle(), titles(siblings, Chapter::getTitle));
        int order = nextOrder(orders(siblings, Chapter::getDisplayOrder));
        trashDao.restoreChapter(batch.getRootId(), title, order);
    }

    private void restoreCodexCategory(TrashItem batch) throws SQLException {
        ChapterParents parents = trashDao.chapterParents(batch.getRootId())
                .orElseThrow(() -> gone("codex category"));
        UUID codexId = parents.codexId();
        if (codexId == null || !codexOwnerLive(codexId)) {
            throw parentTrashed("Restore the codex's project or book first — it is in the trash.");
        }
        List<Chapter> siblings = chapterDao.findByCodexId(codexId);
        String title = dedupe(batch.getRootTitle(), titles(siblings, Chapter::getTitle));
        int order = nextOrder(orders(siblings, Chapter::getDisplayOrder));
        trashDao.restoreChapter(batch.getRootId(), title, order);
    }

    private void restoreScene(TrashItem batch) throws SQLException {
        UUID chapterId = trashDao.sceneChapterId(batch.getRootId())
                .orElseThrow(() -> gone("scene"));
        if (chapterDao.findById(chapterId).isEmpty()) {
            throw parentTrashed("Restore the chapter first — it is in the trash.");
        }
        List<Scene> siblings = sceneDao.findByChapterId(chapterId);
        String title = dedupe(batch.getRootTitle(), titles(siblings, Scene::getTitle));
        int order = nextOrder(orders(siblings, Scene::getDisplayOrder));
        trashDao.restoreScene(batch.getRootId(), title, order);
    }

    private void restoreReview(TrashItem batch) throws SQLException {
        UUID chapterId = trashDao.reviewChapterId(batch.getRootId())
                .orElseThrow(() -> gone("review"));
        if (!trashDao.liveChapterExists(chapterId)) {
            throw parentTrashed("The chapter for this review is missing or in the trash; restore it first.");
        }
        trashDao.restoreReview(batch.getRootId());
    }

    private boolean codexOwnerLive(UUID codexId) throws SQLException {
        Optional<CodexOwner> owner = trashDao.codexOwner(codexId);
        if (owner.isEmpty()) return false;
        CodexOwner o = owner.get();
        if (o.projectId() != null) return projectDao.findById(o.projectId()).isPresent();
        if (o.bookId() != null) return bookDao.findById(o.bookId()).isPresent();
        return false;
    }

    // =========================================================================
    // Purge
    // =========================================================================

    /** Permanently removes one trash batch (and its descendants). */
    public TrashItem purge(UUID userId, UUID batchId) throws SQLException {
        logger.warn("Purging trash item permanently: userId={}, batchId={}", userId, batchId);
        TrashItem batch = trashDao.findBatch(userId, batchId)
                .orElseThrow(() -> new TrashException(404, "not_found", "Trash item not found."));
        logger.debug("Resolved trash batch for purge: batchId={}, rootType={}, rootId={}", batchId, batch.getRootType(), batch.getRootId());
        purgeRoot(batch);
        trashDao.deleteBatch(batchId);
        trashDao.sweepOrphans(userId);
        logger.warn("Trash item purged permanently: userId={}, batchId={}, rootType={}, rootId={}", userId, batchId, batch.getRootType(), batch.getRootId());
        return batch;
    }

    /** Permanently removes every item in the user's trash. Returns the count. */
    public int empty(UUID userId) throws SQLException {
        List<TrashItem> items = trashDao.listForUser(userId);
        logger.warn("Emptying trash permanently: userId={}, itemCount={}", userId, items.size());
        for (TrashItem item : items) {
            purgeRoot(item);
        }
        trashDao.deleteAllBatchesForUser(userId);
        trashDao.sweepOrphans(userId);
        logger.warn("Trash emptied permanently: userId={}, itemCount={}", userId, items.size());
        return items.size();
    }

    private void purgeRoot(TrashItem batch) throws SQLException {
        logger.debug("Purging trash root: rootType={}, rootId={}", batch.getRootType(), batch.getRootId());
        switch (batch.getRootType()) {
            case "PROJECT"                  -> trashDao.purgeProject(batch.getRootId());
            case "BOOK"                     -> trashDao.purgeBook(batch.getRootId());
            case "CHAPTER", "CODEX_CATEGORY" -> trashDao.purgeChapter(batch.getRootId());
            case "SCENE", "CODEX_ENTRY"     -> trashDao.purgeScene(batch.getRootId());
            case "AI_REVIEW"                -> trashDao.purgeReview(batch.getRootId());
            default -> { /* unknown type: leave the (orphan) batch for the sweep */ }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private interface TitleOf<T> {
        String title(T t);
    }

    private interface OrderOf<T> {
        int order(T t);
    }

    private static <T> List<String> titles(List<T> items, TitleOf<T> f) {
        List<String> out = new ArrayList<>(items.size());
        for (T t : items) {
            String title = f.title(t);
            if (title != null) out.add(title);
        }
        return out;
    }

    private static <T> List<Integer> orders(List<T> items, OrderOf<T> f) {
        List<Integer> out = new ArrayList<>(items.size());
        for (T t : items) out.add(f.order(t));
        return out;
    }

    /**
     * Returns base unchanged if blank or unused; otherwise appends the first free
     * " (n)" suffix. Blank titles (auto-numbered manuscript chapters/scenes) are
     * left blank so their "Chapter N" / "Part I" display is preserved.
     */
    private static String dedupe(String base, List<String> existing) {
        if (base == null || base.isBlank()) return base;
        Set<String> set = new HashSet<>(existing);
        if (!set.contains(base)) return base;
        for (int n = 1;; n++) {
            String candidate = base + " (" + n + ")";
            if (!set.contains(candidate)) return candidate;
        }
    }

    private static int nextOrder(List<Integer> orders) {
        int max = -1;
        for (int o : orders) max = Math.max(max, o);
        return max + 1;
    }

    private static TrashException parentTrashed(String message) {
        return new TrashException(409, "parent_unavailable", message);
    }

    private static TrashException gone(String what) {
        return new TrashException(409, "root_missing",
                "This " + what + " no longer exists and cannot be restored.");
    }
}
