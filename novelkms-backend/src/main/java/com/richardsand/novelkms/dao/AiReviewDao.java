package com.richardsand.novelkms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.richardsand.novelkms.model.AiReview;
import com.richardsand.novelkms.model.AiReviewRecommendation;

/**
 * Persistence for AI review artifacts and their recommendations. Reviews are
 * immutable once completed; a re-run creates a new row. The raw provider
 * response is stored in {@code response_json} for audit but is never selected
 * into the {@link AiReview} model.
 *
 * <p>A review's {@code scope} (CHAPTER / SCENE / BOOK) is not a column; it is
 * derived from {@code chapter_id} / {@code scene_id} by
 * {@link AiReview#deriveScope(UUID, UUID)} in {@link #mapReview(ResultSet)}.
 */
public class AiReviewDao {

    private static final String REVIEW_COLUMNS =
            "id, user_id, project_id, book_id, chapter_id, scene_id, provider, model, status, "
            + "submitted_at, completed_at, prompt_version, error_message, form_scope, form_instructions, "
            + "user_guidance";

    /** DAO-local carrier so this layer does not depend on the {@code ai} package. */
    public record NewRecommendation(String category, String severity, String location, String recommendation,
                                    String codexCategory, String codexTitle, String anchorText) {}

    private final DataSource dataSource;

    public AiReviewDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private AiReview mapReview(ResultSet rs) throws SQLException {
        UUID chapterId = rs.getObject("chapter_id", UUID.class);
        UUID sceneId   = rs.getObject("scene_id", UUID.class);
        return AiReview.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .bookId(rs.getObject("book_id", UUID.class))
                .chapterId(chapterId)
                .sceneId(sceneId)
                .scope(AiReview.deriveScope(chapterId, sceneId))
                .provider(rs.getString("provider"))
                .model(rs.getString("model"))
                .status(rs.getString("status"))
                .submittedAt(toInstant(rs.getTimestamp("submitted_at")))
                .completedAt(toInstant(rs.getTimestamp("completed_at")))
                .promptVersion(rs.getString("prompt_version"))
                .errorMessage(rs.getString("error_message"))
                .formScope(rs.getString("form_scope"))
                .formInstructions(rs.getString("form_instructions"))
                .userGuidance(rs.getString("user_guidance"))
                .build();
    }

    private AiReviewRecommendation mapRecommendation(ResultSet rs) throws SQLException {
        return AiReviewRecommendation.builder()
                .id(rs.getObject("id", UUID.class))
                .reviewId(rs.getObject("review_id", UUID.class))
                .seq(rs.getInt("seq"))
                .category(rs.getString("category"))
                .severity(rs.getString("severity"))
                .location(rs.getString("location"))
                .recommendation(rs.getString("recommendation"))
                .status(rs.getString("status"))
                .codexCategory(rs.getString("codex_category"))
                .codexTitle(rs.getString("codex_title"))
                .anchorText(rs.getString("anchor_text"))
                .promotedSceneId(rs.getObject("promoted_scene_id", UUID.class))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .build();
    }

    /**
     * Inserts a PENDING review row and returns its id. {@code sceneId} is null
     * for a chapter review and set for a scene review; {@code chapterId} is the
     * (parent) chapter in both cases. {@code userGuidance} is the optional
     * one-time author note for this run (null when none was supplied).
     */
    public UUID createPending(UUID userId, UUID projectId, UUID bookId, UUID chapterId, UUID sceneId,
                              String provider, String model, String formScope, String formInstructions,
                              String userGuidance)
            throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = "INSERT INTO ai_review "
                + "(id, user_id, project_id, book_id, chapter_id, scene_id, provider, model, status, "
                + " submitted_at, created_at, form_scope, form_instructions, user_guidance) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setObject(3, projectId);
            ps.setObject(4, bookId);
            ps.setObject(5, chapterId);
            ps.setObject(6, sceneId);
            ps.setString(7, provider);
            ps.setString(8, model);
            ps.setTimestamp(9, Timestamp.from(now));
            ps.setTimestamp(10, Timestamp.from(now));
            ps.setString(11, formScope);
            ps.setString(12, formInstructions);
            ps.setString(13, userGuidance);
            ps.executeUpdate();
        }
        return id;
    }

    /** Marks a review COMPLETED and inserts its recommendations in one transaction. */
    public void completeReview(UUID reviewId, String promptVersion, String responseJson,
                               List<NewRecommendation> recommendations) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ai_review SET status = 'COMPLETED', completed_at = ?, "
                        + "prompt_version = ?, response_json = ? WHERE id = ?")) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setString(2, promptVersion);
                    ps.setString(3, responseJson);
                    ps.setObject(4, reviewId);
                    ps.executeUpdate();
                }
                if (recommendations != null && !recommendations.isEmpty()) {
                    String insert = "INSERT INTO ai_review_recommendation "
                            + "(id, review_id, seq, category, severity, location, recommendation, status, "
                            + " codex_category, codex_title, anchor_text, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = c.prepareStatement(insert)) {
                        int seq = 1;
                        for (NewRecommendation r : recommendations) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, reviewId);
                            ps.setInt(3, seq++);
                            ps.setString(4, r.category());
                            ps.setString(5, r.severity());
                            ps.setString(6, r.location());
                            ps.setString(7, r.recommendation());
                            ps.setString(8, r.codexCategory());
                            ps.setString(9, r.codexTitle());
                            ps.setString(10, r.anchorText());
                            ps.setTimestamp(11, Timestamp.from(now));
                            ps.setTimestamp(12, Timestamp.from(now));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Marks a review FAILED with an error message. */
    public void failReview(UUID reviewId, String errorMessage) throws SQLException {
        String sql = "UPDATE ai_review SET status = 'FAILED', completed_at = ?, error_message = ? WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, truncate(errorMessage, 2000));
            ps.setObject(3, reviewId);
            ps.executeUpdate();
        }
    }

    /** Loads a review (with recommendations) scoped to the owning user. */
    public Optional<AiReview> findByIdForUser(UUID reviewId, UUID userId) throws SQLException {
        String sql = "SELECT " + REVIEW_COLUMNS + " FROM ai_review WHERE id = ? AND user_id = ? AND deleted_at IS NULL";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, reviewId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                AiReview review = mapReview(rs);
                review.setRecommendations(findRecommendations(c, reviewId));
                return Optional.of(review);
            }
        }
    }

    /**
     * Lists reviews for a chapter (newest first), without recommendations. Both
     * chapter-scope and scene-scope reviews are returned, because a scene review
     * carries its parent chapter in {@code chapter_id}; the caller distinguishes
     * them by {@code scope}/{@code sceneId}.
     */
    public List<AiReview> findByChapter(UUID chapterId) throws SQLException {
        String sql = "SELECT " + REVIEW_COLUMNS + " FROM ai_review WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY submitted_at DESC";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AiReview> result = new ArrayList<>();
                while (rs.next()) result.add(mapReview(rs));
                return result;
            }
        }
    }

    /**
     * Updates a recommendation's status, guarded by its parent review id. The
     * caller is responsible for first verifying the review belongs to the user
     * (these paths are not covered by the tenant filter).
     */
    public boolean updateRecommendationStatus(UUID recId, UUID reviewId, String status) throws SQLException {
        String sql = "UPDATE ai_review_recommendation SET status = ?, updated_at = ? "
                + "WHERE id = ? AND review_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, recId);
            ps.setObject(4, reviewId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Records the codex entry created when a recommendation is promoted. */
    public boolean markPromoted(UUID recId, UUID reviewId, UUID sceneId) throws SQLException {
        String sql = "UPDATE ai_review_recommendation SET promoted_scene_id = ?, updated_at = ? "
                + "WHERE id = ? AND review_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, sceneId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, recId);
            ps.setObject(4, reviewId);
            return ps.executeUpdate() > 0;
        }
    }

    private List<AiReviewRecommendation> findRecommendations(Connection c, UUID reviewId) throws SQLException {
        String sql = "SELECT id, review_id, seq, category, severity, location, recommendation, status, "
                + "codex_category, codex_title, anchor_text, promoted_scene_id, "
                + "created_at, updated_at FROM ai_review_recommendation WHERE review_id = ? ORDER BY seq";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AiReviewRecommendation> result = new ArrayList<>();
                while (rs.next()) result.add(mapRecommendation(rs));
                return result;
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
