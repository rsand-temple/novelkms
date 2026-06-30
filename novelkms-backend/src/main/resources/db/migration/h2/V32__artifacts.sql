-- ===========================================================================
-- V32 — Artifacts (non-manuscript project file/folder store)
--
-- A per-project file/folder area for material that is NOT part of the
-- manuscript: query letters, research PDFs, cover-art source images, etc. The
-- logical tree (names, hierarchy, ordering, Windows-style case rules, trash)
-- lives entirely here in SQL; the file bytes live on a host-mounted disk volume
-- keyed by an opaque blob id, so renames/moves are pure metadata and a future
-- Dropbox-style versioning phase can be added additively.
--
-- artifact_node is a self-referential tree. parent_id NULL means the (virtual)
-- project root — there is no root row. node_type is FOLDER or FILE; only FILE
-- nodes carry a blob_id and the denormalized size_bytes/content_type used by the
-- Explorer details view and the tree read (so neither needs a blob join).
--
-- Windows case convention: name is stored exactly as the author typed it;
-- name_normalized = lower(name) drives case-insensitive uniqueness WITHIN a
-- folder. Uniqueness is enforced in the DAO inside the mutating transaction
-- (collision -> 400), NOT by a DB unique index: trashed siblings must not block
-- reuse of a freed name, and H2 cannot express that as a filtered unique index.
--
-- Trash: an artifact folder or file is a per-user trash root using the same
-- root-stamping pattern as every other trashable entity (only the deleted root
-- is stamped with deleted_at + deleted_batch_id; descendants are hidden
-- transitively because every live read filters deleted_at IS NULL and is reached
-- only through its parent). Trashing frees no quota; only purge does.
--
-- artifact_blob is the content-store index. sha256 + size_bytes are captured now
-- (v1 stores one blob per upload, no dedup) so the versioning phase can add
-- content-addressed dedup/refcounting without re-hashing. user_id lets storage
-- quota be a single indexed SUM over a user's blobs across all their projects,
-- including blobs whose file nodes are currently in the trash.
--
-- artifact_quota_bytes on app_user is a nullable per-user override; NULL means
-- "use the config default" (artifacts.defaultUserQuotaBytes). The column exists
-- now so a later admin "grant more storage" action is purely additive, mirroring
-- how the manual `family` billing entitlement was forward-prepped.
--
-- All types here (UUID, VARCHAR, CHAR, BIGINT, INT, BOOLEAN, TIMESTAMP,
-- REFERENCES ... ON DELETE CASCADE) are valid in both H2 (MODE=PostgreSQL) and
-- PostgreSQL, so this file is identical to the postgresql dialect file. One
-- ALTER TABLE per column, per the H2 rule.
-- ===========================================================================

ALTER TABLE app_user ADD COLUMN artifact_quota_bytes BIGINT;

CREATE TABLE artifact_blob (
    id           UUID         NOT NULL PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    sha256       CHAR(64)     NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    storage_key  VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_artifact_blob_user   ON artifact_blob(user_id);
CREATE INDEX ix_artifact_blob_sha256 ON artifact_blob(sha256);

CREATE TABLE artifact_node (
    id               UUID         NOT NULL PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    parent_id        UUID         REFERENCES artifact_node(id) ON DELETE CASCADE,
    node_type        VARCHAR(10)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    name_normalized  VARCHAR(255) NOT NULL,
    display_order    INT          NOT NULL DEFAULT 0,
    blob_id          UUID         REFERENCES artifact_blob(id),
    size_bytes       BIGINT       NOT NULL DEFAULT 0,
    content_type     VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP,
    deleted_batch_id UUID
);

CREATE INDEX ix_artifact_node_project    ON artifact_node(project_id);
CREATE INDEX ix_artifact_node_parent     ON artifact_node(parent_id);
CREATE INDEX ix_artifact_node_normalized ON artifact_node(project_id, parent_id, name_normalized);
CREATE INDEX ix_artifact_node_deleted_at ON artifact_node(deleted_at);
