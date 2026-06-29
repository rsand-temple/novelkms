CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID NULL REFERENCES app_user(id) ON DELETE SET NULL,

    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role_not_blank CHECK (length(trim(role)) > 0)
);

CREATE INDEX ix_user_role_role ON user_role(role);