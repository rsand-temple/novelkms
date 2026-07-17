package com.richardsand.novelkms.dao.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * Read side of {@code user_block}.
 *
 * <p>Blocking is directional in the row but symmetric in effect: if either party
 * has blocked the other, neither sees the other's requests, packages, or profile
 * (spec §21, and the schema comment: enforced in BOTH directions at read time).
 * The review-network read paths consult {@link #blockedBetween} before disclosing
 * anything cross-user.
 *
 * <p>Only the read exists in slice 1C. Creating and removing blocks is a slice-1F
 * surface; wiring the read now means the queue and package reads are block-aware
 * from the moment reviewing is possible, rather than being retrofitted onto a
 * queue already in the wild.
 */
public class UserBlockDao {

    private final BasicDataSource ds;

    public UserBlockDao(BasicDataSource ds) {
        this.ds = ds;
    }

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
}
