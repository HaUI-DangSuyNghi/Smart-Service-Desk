CREATE TABLE servicedesk_work_release (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    task_id varchar(100) PRIMARY KEY,
    review_task_id varchar(100) NOT NULL UNIQUE,
    released_by varchar(100) NOT NULL,
    reason varchar(20) NOT NULL CHECK (reason IN ('WRONG_GROUP','HANDOVER','OTHER')),
    note varchar(1000) NOT NULL CHECK (length(trim(note)) > 0),
    worked_millis bigint NOT NULL CHECK (worked_millis >= 0),
    released_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);
ALTER TABLE servicedesk_sla ADD COLUMN released_at timestamptz;
ALTER TABLE servicedesk_sla ADD CONSTRAINT servicedesk_sla_release_state
    CHECK (released_at IS NULL OR (resolved_at IS NULL AND paused_at IS NULL AND released_at >= approved_at));
