CREATE TABLE hpdi_request_change (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    process_id varchar(100) NOT NULL,
    version bigint NOT NULL,
    action varchar(12) NOT NULL CHECK (action IN ('UPDATED','CANCELLED')),
    actor varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL,
    before_values jsonb NOT NULL,
    after_values jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE (workspace, process_id, version)
);
CREATE INDEX hpdi_request_change_history ON hpdi_request_change(workspace, process_id, version DESC);
