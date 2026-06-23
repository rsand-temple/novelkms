-- =============================================================================
-- NovelKMS  V17 — User settings
--
-- Two stores, both keyed to app_user (ON DELETE CASCADE), consistent with the
-- V9 auth model, V11 per-user template/style defaults, and V14 AI credentials.
--
--   editor_settings  — the cascading "document settings" bundle (font, line
--                      height, indents, paragraph spacing, scene break). One
--                      SYSTEM row (factory default, lazily seeded from
--                      EditorSettingsDefaults), one USER row per user, one
--                      PROJECT row per project override.
--                      Resolution for (user, project): PROJECT -> USER -> SYSTEM
--                      — mirrors the style/template cascade.
--
--   user_preference  — a flat per-user key/value store for UI preferences
--                      (e.g. skipDeleteConfirm). New preferences are new keys;
--                      no further migrations required.
--
-- TEXT and the DDL below are valid in both H2 (MODE=PostgreSQL) and PostgreSQL,
-- so this file is identical to the postgresql dialect file (as with V14).
--
-- NULLs are distinct in UNIQUE indexes (standard SQL, honored by both engines),
-- exactly as V5 already relies on for style override rows. SYSTEM rows leave
-- both owner ids NULL; SYSTEM uniqueness is enforced by the DAO's find-or-create
-- logic, again mirroring the style/template pattern.
-- =============================================================================

CREATE TABLE editor_settings (
    id          UUID         NOT NULL PRIMARY KEY,
    scope       VARCHAR(20)  NOT NULL,                                  -- SYSTEM | USER | PROJECT
    user_id     UUID                  REFERENCES app_user(id) ON DELETE CASCADE,  -- USER rows
    project_id  UUID                  REFERENCES project(id)  ON DELETE CASCADE,  -- PROJECT rows
    definition  TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_editor_settings_lookup ON editor_settings(scope);

-- One USER row per user; one PROJECT row per project. SYSTEM rows have both ids
-- NULL (multiple NULLs permitted), so SYSTEM uniqueness is enforced in the DAO.
CREATE UNIQUE INDEX uq_editor_settings_user    ON editor_settings(user_id);
CREATE UNIQUE INDEX uq_editor_settings_project ON editor_settings(project_id);

-- -----------------------------------------------------------------------------

CREATE TABLE user_preference (
    id          UUID         NOT NULL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    pref_key    VARCHAR(200) NOT NULL,
    pref_value  TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_user_preference       ON user_preference(user_id, pref_key);
CREATE INDEX        idx_user_preference_user ON user_preference(user_id);
