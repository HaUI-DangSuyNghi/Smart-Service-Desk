CREATE TABLE hpdi_estimate_request (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    process_instance_id varchar(100) NOT NULL,
    task_id varchar(100) NOT NULL,
    generation varchar(100) NOT NULL,
    business_key varchar(255) NOT NULL,
    label text NOT NULL,
    requested_by varchar(100) NOT NULL,
    current_minutes integer NOT NULL CHECK (current_minutes BETWEEN 1 AND 10080),
    proposed_minutes integer NOT NULL CHECK (proposed_minutes BETWEEN 1 AND 10080),
    reason varchar(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_by varchar(100),
    decided_at timestamptz,
    decision_note varchar(1000),
    CHECK ((status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE UNIQUE INDEX hpdi_estimate_pending ON hpdi_estimate_request(workspace,task_id) WHERE status='PENDING';
CREATE INDEX hpdi_estimate_workspace ON hpdi_estimate_request(workspace,created_at DESC,id DESC);
CREATE INDEX hpdi_estimate_requester ON hpdi_estimate_request(workspace,requested_by,created_at DESC,id DESC);
ALTER TABLE hpdi_alert_event ADD COLUMN agent_suppressed boolean NOT NULL DEFAULT false;
