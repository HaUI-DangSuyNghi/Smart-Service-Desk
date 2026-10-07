CREATE TABLE metadata (
    id uuid PRIMARY KEY, workspace varchar(100) NOT NULL,
    title varchar(200) NOT NULL, source varchar(1000) NOT NULL,
    version varchar(100) NOT NULL, usage_rights varchar(200) NOT NULL,
    checksum char(64) NOT NULL, owner varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE INDEX metadata_workspace ON metadata(workspace);
CREATE TABLE process_scope (
    id varchar(100) PRIMARY KEY, workspace varchar(100) NOT NULL,
    owner varchar(100) NOT NULL, process_key varchar(100) NOT NULL,
    business_key varchar(200) NOT NULL,
    UNIQUE(workspace, process_key, business_key)
);
CREATE TABLE audit_event (
    id uuid PRIMARY KEY, actor varchar(100) NOT NULL, action varchar(100) NOT NULL,
    resource varchar(200) NOT NULL, workspace varchar(100) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp, request_id varchar(100) NOT NULL
);
CREATE INDEX audit_workspace_time ON audit_event(workspace, occurred_at);
CREATE TABLE file_object (
    id uuid PRIMARY KEY, workspace varchar(100) NOT NULL, owner varchar(100) NOT NULL,
    storage_key uuid NOT NULL UNIQUE, media_type varchar(200) NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size BETWEEN 0 AND 5242880),
    checksum char(64) NOT NULL, created_at timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE TABLE file_link (
    file_id uuid REFERENCES file_object(id), resource_type varchar(100) NOT NULL,
    resource_id varchar(200) NOT NULL, PRIMARY KEY(file_id, resource_type, resource_id)
);
