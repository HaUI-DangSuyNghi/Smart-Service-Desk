-- Audit: thêm loại đối tượng và chi tiết (thiết kế mục 8.1)
ALTER TABLE audit_event ADD COLUMN resource_type varchar(100);
-- Bản ghi cũ: lấy loại đối tượng từ tiền tố action (metadata.create → metadata)
UPDATE audit_event SET resource_type = split_part(action, '.', 1);
ALTER TABLE audit_event ALTER COLUMN resource_type SET NOT NULL;
ALTER TABLE audit_event ADD COLUMN details jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX audit_workspace_action ON audit_event(workspace, action, occurred_at);
