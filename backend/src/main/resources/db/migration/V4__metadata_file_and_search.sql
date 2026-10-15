ALTER TABLE file_object ADD CONSTRAINT file_object_workspace_id UNIQUE (workspace, id);
ALTER TABLE metadata ADD COLUMN file_id uuid;
ALTER TABLE metadata ADD CONSTRAINT metadata_file_workspace
    FOREIGN KEY (workspace, file_id) REFERENCES file_object(workspace, id);
CREATE INDEX metadata_workspace_created_id ON metadata(workspace, created_at DESC, id DESC);
