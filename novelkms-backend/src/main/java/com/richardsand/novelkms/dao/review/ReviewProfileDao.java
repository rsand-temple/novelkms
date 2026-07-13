package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.ReviewProfile;

/**
 * CRUD for {@code review_profile} — a user's public identity in the human-review
 * network.
 *
 * <p>The DAO owns two rules the schema alone cannot express:
 *
 * <ul>
 *   <li><b>Handle casing.</b> {@code handle} is stored exactly as the user typed
 *       it; {@code handle_lower} is written alongside it and drives every lookup
 *       and the uniqueness constraint. Callers pass handles in any casing and
 *       never touch {@code handle_lower}.</li>
 *   <li><b>Genre packing.</b> The wire contract is a list of strings; the column
 *       is one comma-separated VARCHAR. Nothing in Phase 1 queries a single
 *       genre, so a join table would buy nothing.</li>
 * </ul>
 *
 * <p>Reads are NOT filtered by visibility or status here — {@link #findByHandle}
 * will happily return a HIDDEN or SUSPENDED profile. That filtering is a
 * disclosure decision and belongs to the caller, which knows whether it is
 * serving the profile's owner (who must always see their own row), an admin, or
 * a stranger.
 */
public class ReviewProfileDao {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_HIDDEN = "HIDDEN";

    public static final String STATUS_ACTIVE    = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    private static final String COLS =
            "id, user_id, handle, display_name, bio, genres_written, genres_reviewed, "
          + "visibility, status, created_at, updated_at ";

    private final BasicDataSource ds;

    public ReviewProfileDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // =========================================================================
    // Handle helpers
    // =========================================================================

    /** The uniqueness key for a handle. Never persisted by the caller. */
    public static String normalize(String handle) {
        return handle == null ? null : handle.trim().toLowerCase(Locale.ROOT);
    }

    private static String packGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return null;
        }
        String joined = genres.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> unpackGenres(String packed) {
        if (packed == null || packed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(packed.split(","))
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .toList();
    }

    private ReviewProfile map(ResultSet rs) throws SQLException {
        return ReviewProfile.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .handle(rs.getString("handle"))
                .displayName(rs.getString("display_name"))
                .bio(rs.getString("bio"))
                .genresWritten(unpackGenres(rs.getString("genres_written")))
                .genresReviewed(unpackGenres(rs.getString("genres_reviewed")))
                .visibility(rs.getString("visibility"))
                .status(rs.getString("status"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // =========================================================================
    // Reads
    // =========================================================================

    public Optional<ReviewProfile> findByUserId(UUID userId) throws SQLException {
        return findOne("SELECT " + COLS + "FROM review_profile WHERE user_id = ?", userId);
    }

    public Optional<ReviewProfile> findById(UUID id) throws SQLException {
        return findOne("SELECT " + COLS + "FROM review_profile WHERE id = ?", id);
    }

    /** Case-insensitive. Returns HIDDEN/SUSPENDED rows too — the caller decides. */
    public Optional<ReviewProfile> findByHandle(String handle) throws SQLException {
        String key = normalize(handle);
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT " + COLS + "FROM review_profile WHERE handle_lower = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Whether a handle is already claimed. {@code excludeUserId} lets a user
     * "keep" their own handle when saving an otherwise-unchanged profile.
     */
    public boolean handleTaken(String handle, UUID excludeUserId) throws SQLException {
        String key = normalize(handle);
        if (key == null || key.isEmpty()) {
            return false;
        }
        String sql = "SELECT user_id FROM review_profile WHERE handle_lower = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                UUID owner = rs.getObject("user_id", UUID.class);
                return excludeUserId == null || !excludeUserId.equals(owner);
            }
        }
    }

    /** Bulk handle lookup — the queue and review lists resolve author/reviewer handles this way. */
    public List<ReviewProfile> findByUserIds(List<UUID> userIds) throws SQLException {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String placeholders = userIds.stream().map(u -> "?").collect(Collectors.joining(","));
        String sql = "SELECT " + COLS + "FROM review_profile WHERE user_id IN (" + placeholders + ")";
        List<ReviewProfile> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                ps.setObject(i + 1, userIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    private Optional<ReviewProfile> findOne(String sql, UUID key) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Claim a handle and create the caller's profile. The DB unique index on
     * {@code handle_lower} is the real arbiter of a race; the caller's
     * {@link #handleTaken} pre-check exists only to return a friendly 409 in the
     * common case.
     */
    public ReviewProfile create(UUID userId, ReviewProfile profile) throws SQLException {
        UUID   id     = UUID.randomUUID();
        String handle = profile.getHandle().trim();

        String sql = "INSERT INTO review_profile "
                + "(id, user_id, handle, handle_lower, display_name, bio, genres_written, "
                + " genres_reviewed, visibility, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, handle);
            ps.setString(4, normalize(handle));
            ps.setString(5, blankToNull(profile.getDisplayName()));
            ps.setString(6, blankToNull(profile.getBio()));
            ps.setString(7, packGenres(profile.getGenresWritten()));
            ps.setString(8, packGenres(profile.getGenresReviewed()));
            ps.setString(9, visibilityOrDefault(profile.getVisibility()));
            ps.setString(10, STATUS_ACTIVE);
            ps.executeUpdate();
        }

        return findById(id).orElseThrow(
                () -> new SQLException("review_profile row vanished immediately after insert: " + id));
    }

    /**
     * Update the caller's own profile. Scoped by {@code user_id}, so this cannot
     * touch anyone else's row regardless of what the payload claims.
     *
     * <p>{@code status} is deliberately not updatable here — suspension is an
     * admin action, not a user setting.
     */
    public Optional<ReviewProfile> update(UUID userId, ReviewProfile profile) throws SQLException {
        String handle = profile.getHandle().trim();

        String sql = "UPDATE review_profile SET "
                + "handle = ?, handle_lower = ?, display_name = ?, bio = ?, "
                + "genres_written = ?, genres_reviewed = ?, visibility = ?, "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ?";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, handle);
            ps.setString(2, normalize(handle));
            ps.setString(3, blankToNull(profile.getDisplayName()));
            ps.setString(4, blankToNull(profile.getBio()));
            ps.setString(5, packGenres(profile.getGenresWritten()));
            ps.setString(6, packGenres(profile.getGenresReviewed()));
            ps.setString(7, visibilityOrDefault(profile.getVisibility()));
            ps.setObject(8, userId);

            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }

        return findByUserId(userId);
    }

    /** Moderation: suspend or reinstate a profile. Admin-only callers. */
    public boolean setStatus(UUID userId, String status) throws SQLException {
        String sql = "UPDATE review_profile SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String visibilityOrDefault(String visibility) {
        return VISIBILITY_HIDDEN.equals(visibility) ? VISIBILITY_HIDDEN : VISIBILITY_PUBLIC;
    }
}
