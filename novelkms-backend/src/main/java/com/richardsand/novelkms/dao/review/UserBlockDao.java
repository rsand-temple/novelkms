package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.review.BlockedUser;

/**
 * {@code user_block} — the directional block relationship, and its symmetric
 * read effect.
 *
 * <p>Blocking is directional in the row (one blocker, one blocked) but symmetric
 * in effect: if either party has blocked the other, neither sees the other's
 * requests, packages, reviews, or profile (spec §21, and the schema comment:
 * enforced in BOTH directions at read time). The queue, the writing/received
 * lists, and the package/profile read gates all consult {@link #blockedBetween}
 * before disclosing anything cross-user.
 *
 * <p>The read ({@link #blockedBetween}) shipped in slice 1C so those surfaces were
 * block-aware from the moment reviewing was possible. Slice 1F adds the write side
 * — {@link #block}, {@link #unblock}, {@link #listBlocked} — so a user can create
 * and manage the relationship the read side has been honoring all along.
 *
 * <p>All SQL is plain-standard and runs identically on default-mode H2 and
 * PostgreSQL. Scoping is in the SQL itself, not in a caller's memory: every write
 * carries {@code blocker_user_id = ?} so it can only ever touch the caller's own
 * blocks.
 */
public class UserBlockDao {

    private final BasicDataSource ds;

    public UserBlockDao(BasicDataSource ds) {
        this.ds = ds;
    }

    // =========================================================================
    // Reads
    // =========================================================================

    /**
     * Whether either user has blocked the other. Symmetric: the argument order does
     * not matter.
     */
    public boolean blockedBetween(UUID a, UUID b) throws SQLException {
        if (a == null || b == null) {
            return false;
        }
        String sql = "SELECT 1 FROM user_block WHERE "
                + "(blocker_user_id = ? AND blocked_user_id = ?) OR "
                + "(blocker_user_id = ? AND blocked_user_id = ?) LIMIT 1";

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, a);
            ps.setObject(2, b);
            ps.setObject(3, b);
            ps.setObject(4, a);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Whether {@code blocker} has specifically blocked {@code blocked} (directional). */
    public boolean isBlocked(UUID blocker, UUID blocked) throws SQLException {
        if (blocker == null || blocked == null) {
            return false;
        }
        String sql = "SELECT 1 FROM user_block WHERE blocker_user_id = ? AND blocked_user_id = ? LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, blocker);
            ps.setObject(2, blocked);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * The caller's own block list, newest first, resolved to the blocked user's
     * public handle so the UI can render and undo it. An INNER JOIN to
     * {@code review_profile} means a block whose target has since deleted their
     * profile drops out — harmless, because a profile-less user cannot participate
     * and so the block guards nothing. Never exposes the blocked user's id.
     */
    public List<BlockedUser> listBlocked(UUID blockerUserId) throws SQLException {
        String sql = "SELECT rp.handle, rp.display_name, ub.created_at "
                + "FROM user_block ub "
                + "JOIN review_profile rp ON rp.user_id = ub.blocked_user_id "
                + "WHERE ub.blocker_user_id = ? "
                + "ORDER BY ub.created_at DESC";

        List<BlockedUser> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, blockerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BlockedUser(
                            rs.getString("handle"),
                            rs.getString("display_name"),
                            rs.getTimestamp("created_at") == null
                                    ? null
                                    : rs.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return out;
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Records that {@code blocker} blocks {@code blocked}. Idempotent: blocking an
     * already-blocked user is a no-op that still reports success. The
     * {@code UNIQUE(blocker_user_id, blocked_user_id)} index is the real arbiter of
     * a race — a concurrent duplicate lands in the catch, where a re-check confirms
     * the row now exists and the call succeeds rather than 500s.
     *
     * @return {@code true} once the block exists (whether this call created it)
     */
    public boolean block(UUID blockerUserId, UUID blockedUserId) throws SQLException {
        if (isBlocked(blockerUserId, blockedUserId)) {
            return true;
        }
        String sql = "INSERT INTO user_block (id, blocker_user_id, blocked_user_id) VALUES (?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, blockerUserId);
            ps.setObject(3, blockedUserId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Lost a race against a concurrent identical block: the unique index
            // rejected the second insert. If the row is now present, the caller's
            // intent is satisfied; only a genuinely different failure rethrows.
            if (isBlocked(blockerUserId, blockedUserId)) {
                return true;
            }
            throw e;
        }
    }

    /**
     * Removes {@code blocker}'s block of {@code blocked}. Scoped to the caller as
     * blocker, so it can never lift someone else's block.
     *
     * @return whether a block row was actually removed
     */
    public boolean unblock(UUID blockerUserId, UUID blockedUserId) throws SQLException {
        String sql = "DELETE FROM user_block WHERE blocker_user_id = ? AND blocked_user_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, blockerUserId);
            ps.setObject(2, blockedUserId);
            return ps.executeUpdate() > 0;
        }
    }
}
