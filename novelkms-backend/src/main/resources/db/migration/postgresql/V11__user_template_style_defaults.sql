ALTER TABLE template ADD COLUMN user_id UUID REFERENCES app_user(id) ON DELETE CASCADE;
UPDATE template SET scope = 'SYSTEM' WHERE scope = 'GLOBAL';
CREATE UNIQUE INDEX uq_template_user_type ON template(user_id, template_type);
CREATE INDEX idx_template_user_lookup ON template(user_id, template_type, scope);

ALTER TABLE style ADD COLUMN user_id UUID REFERENCES app_user(id) ON DELETE CASCADE;
UPDATE style SET scope = 'SYSTEM' WHERE scope = 'GLOBAL';
CREATE UNIQUE INDEX uq_style_user_key ON style(user_id, style_key);
CREATE INDEX idx_style_user_lookup ON style(user_id, style_key, scope);
