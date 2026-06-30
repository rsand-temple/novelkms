CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    target_user_id UUID NULL REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64) NULL,
    entity_id VARCHAR(128) NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_admin_audit_log_admin_user ON admin_audit_log(admin_user_id);
CREATE INDEX ix_admin_audit_log_target_user ON admin_audit_log(target_user_id);
CREATE INDEX ix_admin_audit_log_created_at ON admin_audit_log(created_at);