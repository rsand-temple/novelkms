package com.richardsand.novelkms.dao.artifact;

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

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.ArtifactNode;

/**
 * CRUD and tree mechanics for {@code artifact_node}. The DAO owns the
 * Windows-style case rule: {@code name} is stored exactly as authored,
 * {@code name_normalized} = lower(name) drives case-insensitive sibling
 * uniqueness. Collision detection runs against <em>live</em> siblings only, so a
 * trashed name can be reused. Mutations needing read-then-write atomicity (file
 * insert with a blob) accept an external {@link Connection}.
 *
 * <p>All live reads filter {@code deleted_at IS NULL}; descendants of a trashed
 * node are hidden transitively because they are only reachable through their
 * (trashed, therefore filtered) parent — but for safety the whole-project tree
 * read also relies on the caller never surfacing a child whose parent was
 * pruned, so it returns the flat live set and the frontend reconstructs the
 * tree, exactly like the manuscript nav.
 */
public class ArtifactNodeDao {

    public static final String TYPE_FOLDER = "FOLDER";
    public static final String TYPE_FILE   = "FILE";

    private final BasicDataSource ds;

    public ArtifactNodeDao(BasicDataSource ds) {
        this.ds = ds;
    }

    private static final String COLS =
            "id, project_id, parent_id, node_type, name, display_order, size_bytes, content_type, "
          + "created_at, updated_at ";

    private ArtifactNode map(ResultSet rs) throws SQLException {
        return ArtifactNode.builder()
                .id(rs.getObject("id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .parentId(rs.getObject("parent_id", UUID.class))
                .type(rs.getString("node_type"))
                .name(rs.getString("name"))
                .displayOrder(rs.getInt("display_order"))
                .sizeBytes(rs.getLong("size_bytes"))
                .contentType(rs.getString("content_type"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    // =========================================================================
    // Reads
    // =========================================================================

    /** The whole live tree for a project (flat, ordered) — the Explorer reads this. */
    public List<ArtifactNode> findLiveByProject(UUID projectId) throws SQLException {
        String sql = "SELECT " + COLS + "FROM artifact_node "
                + "WHERE project_id = ? AND deleted_at IS NULL "
                + "ORDER BY display_order, name";
        List<ArtifactNode> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Optional<ArtifactNode> findLiveById(UUID id) throws SQLException {
        return findOne("SELECT " + COLS + "FROM artifact_node WHERE id = ? AND deleted_at IS NULL", id);
    }

    /** Ignores deleted_at — used by restore to resolve the (possibly trashed) parent context. */
    public Optional<ArtifactNode> findAnyById(UUID id) throws SQLException {
        return findOne("SELECT " + COLS + "FROM artifact_node WHERE id = ?", id);
    }

    private Optional<ArtifactNode> findOne(String sql, UUID key) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Live children directly under a folder (parentId null = project root). */
    public List<ArtifactNode> liveChildren(UUID projectId, UUID parentId) throws SQLException {
        List<ArtifactNode> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = childrenStmt(c, projectId, parentId)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public int liveChildCount(UUID projectId, UUID parentId) throws SQLException {
        return liveChildren(projectId, parentId).size();
    }

    private PreparedStatement childrenStmt(Connection c, UUID projectId, UUID parentId) throws SQLException {
        if (parentId == null) {
            PreparedStatement ps = c.prepareStatement("SELECT " + COLS + "FROM artifact_node "
                    + "WHERE project_id = ? AND parent_id IS NULL AND deleted_at IS NULL "
                    + "ORDER BY display_order, name");
            ps.setObject(1, projectId);
            return ps;
        }
        PreparedStatement ps = c.prepareStatement("SELECT " + COLS + "FROM artifact_node "
                + "WHERE project_id = ? AND parent_id = ? AND deleted_at IS NULL "
                + "ORDER BY display_order, name");
        ps.setObject(1, projectId);
        ps.setObject(2, parentId);
        return ps;
    }

    /** Owning project, ignoring deleted_at (for in-resource ownership resolution). */
    public Optional<UUID> projectIdOf(UUID nodeId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT project_id FROM artifact_node WHERE id = ?")) {
            ps.setObject(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getObject("project_id", UUID.class)) : Optional.empty();
            }
        }
    }

    /** True if moving into {@code candidateParentId} would create a cycle (it is the node itself or a descendant). */
    public boolean wouldCycle(UUID movingNodeId, UUID candidateParentId) throws SQLException {
        UUID cursor = candidateParentId;
        // Walk ancestors of the target parent; if we reach the node being moved, it is a cycle.
        int guard = 0;
        while (cursor != null) {
            if (cursor.equals(movingNodeId)) {
                return true;
            }
            cursor = parentOf(cursor);
            if (++guard > 10_000) {
                return true; // defensive: corrupt tree, refuse the move
            }
        }
        return false;
    }

    private UUID parentOf(UUID id) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT parent_id FROM artifact_node WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject("parent_id", UUID.class) : null;
            }
        }
    }

    // =========================================================================
    // Uniqueness / ordering helpers
    // =========================================================================

    /** True when a live sibling already uses this normalized name (case-insensitive), excluding {@code excludeId}. */
    public boolean nameTakenLive(UUID projectId, UUID parentId, String nameNormalized, UUID excludeId)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM artifact_node WHERE project_id = ? "
                + "AND deleted_at IS NULL AND name_normalized = ? AND ");
        sql.append(parentId == null ? "parent_id IS NULL " : "parent_id = ? ");
        if (excludeId != null) {
            sql.append("AND id <> ? ");
        }
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setObject(i++, projectId);
            ps.setString(i++, nameNormalized);
            if (parentId != null) {
                ps.setObject(i++, parentId);
            }
            if (excludeId != null) {
                ps.setObject(i++, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int nextDisplayOrder(UUID projectId, UUID parentId) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(MAX(display_order), -1) + 1 FROM artifact_node "
                        + "WHERE project_id = ? AND deleted_at IS NULL AND ");
        sql.append(parentId == null ? "parent_id IS NULL" : "parent_id = ?");
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setObject(1, projectId);
            if (parentId != null) {
                ps.setObject(2, parentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // =========================================================================
    // Inserts
    // =========================================================================

    public ArtifactNode insertFolder(UUID projectId, UUID parentId, String name, String nameNormalized,
            int displayOrder) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
                INSERT INTO artifact_node
                  (id, project_id, parent_id, node_type, name, name_normalized, display_order,
                   blob_id, size_bytes, content_type, created_at, updated_at)
                VALUES (?, ?, ?, 'FOLDER', ?, ?, ?, NULL, 0, NULL, ?, ?)
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setObject(3, parentId);
            ps.setString(4, name);
            ps.setString(5, nameNormalized);
            ps.setInt(6, displayOrder);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.setTimestamp(8, Timestamp.from(now));
            ps.executeUpdate();
        }
        return ArtifactNode.builder()
                .id(id).projectId(projectId).parentId(parentId).type(TYPE_FOLDER)
                .name(name).displayOrder(displayOrder).sizeBytes(0).contentType(null)
                .createdAt(now).updatedAt(now)
                .build();
    }

    /**
     * Inserts a FILE node referencing an already-inserted blob. Runs on the
     * caller's connection so the blob and node commit together.
     */
    public ArtifactNode insertFile(Connection c, UUID projectId, UUID parentId, String name, String nameNormalized,
            int displayOrder, UUID blobId, long sizeBytes, String contentType) throws SQLException {
        UUID    id  = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
                INSERT INTO artifact_node
                  (id, project_id, parent_id, node_type, name, name_normalized, display_order,
                   blob_id, size_bytes, content_type, created_at, updated_at)
                VALUES (?, ?, ?, 'FILE', ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setObject(3, parentId);
            ps.setString(4, name);
            ps.setString(5, nameNormalized);
            ps.setInt(6, displayOrder);
            ps.setObject(7, blobId);
            ps.setLong(8, sizeBytes);
            ps.setString(9, contentType);
            ps.setTimestamp(10, Timestamp.from(now));
            ps.setTimestamp(11, Timestamp.from(now));
            ps.executeUpdate();
        }
        return ArtifactNode.builder()
                .id(id).projectId(projectId).parentId(parentId).type(TYPE_FILE)
                .name(name).displayOrder(displayOrder).sizeBytes(sizeBytes).contentType(contentType)
                .createdAt(now).updatedAt(now)
                .build();
    }

    /** The blob id backing a FILE node (null for a folder / missing node). */
    public Optional<UUID> blobIdOf(UUID nodeId) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT blob_id FROM artifact_node WHERE id = ?")) {
            ps.setObject(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getObject("blob_id", UUID.class)) : Optional.empty();
            }
        }
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    public boolean rename(UUID id, String name, String nameNormalized) throws SQLException {
        String sql = "UPDATE artifact_node SET name = ?, name_normalized = ?, updated_at = ? "
                + "WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, nameNormalized);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean move(UUID id, UUID newParentId, int displayOrder) throws SQLException {
        String sql = "UPDATE artifact_node SET parent_id = ?, display_order = ?, updated_at = ? "
                + "WHERE id = ? AND deleted_at IS NULL";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, newParentId);
            ps.setInt(2, displayOrder);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Swaps the blob backing a FILE node (in-place text save). Runs on the
     * caller's connection so old-blob-delete + new-blob-reference commit together.
     */
    public boolean updateBlob(Connection c, UUID nodeId, UUID blobId, long sizeBytes) throws SQLException {
        String sql = "UPDATE artifact_node SET blob_id = ?, size_bytes = ?, updated_at = ? "
                + "WHERE id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, blobId);
            ps.setLong(2, sizeBytes);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setObject(4, nodeId);
            return ps.executeUpdate() > 0;
        }
    }
}
