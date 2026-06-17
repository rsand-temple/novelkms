ALTER TABLE project ADD COLUMN owner_user_id UUID REFERENCES app_user(id);

UPDATE project
SET owner_user_id = (SELECT id FROM app_user ORDER BY created_at LIMIT 1)
WHERE owner_user_id IS NULL
  AND (SELECT COUNT(*) FROM app_user) = 1;

CREATE INDEX idx_project_owner_user_id ON project(owner_user_id);
