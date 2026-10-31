ALTER TABLE file_object ADD COLUMN original_name varchar(200) NOT NULL DEFAULT 'download';
ALTER TABLE file_object ALTER COLUMN original_name DROP DEFAULT;
