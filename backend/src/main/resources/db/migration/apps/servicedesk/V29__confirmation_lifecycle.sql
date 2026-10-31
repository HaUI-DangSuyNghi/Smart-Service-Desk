CREATE TABLE servicedesk_confirmation (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    process_instance_id varchar(100) NOT NULL,
    generation varchar(100) NOT NULL,
    handle_task_id varchar(100) NOT NULL,
    handle_resolved_at timestamptz NOT NULL,
    confirm_started_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    decision varchar(20) CHECK (decision IN ('REQUESTER','AUTO','REWORK')),
    decided_at timestamptz,
    decided_by varchar(100),
    reopen_deadline_at timestamptz,
    reopen_received_at timestamptz,
    reopen_received_reason varchar(20) CHECK (reopen_received_reason IN ('ASSIGNED','CANONICAL')),
    checked_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (workspace,process_instance_id,generation),
    UNIQUE (generation),
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    CHECK (deadline_at=confirm_started_at+interval '72 hours'),
    CHECK ((reopen_received_at IS NULL) = (reopen_received_reason IS NULL)),
    CHECK ((decision IS NULL AND decided_at IS NULL AND decided_by IS NULL)
        OR (decision IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL)),
    CHECK ((decision='REWORK' AND reopen_deadline_at IS NOT NULL)
        OR (decision IS DISTINCT FROM 'REWORK' AND reopen_deadline_at IS NULL AND reopen_received_at IS NULL))
);
CREATE INDEX servicedesk_confirmation_pending ON servicedesk_confirmation(checked_at,workspace,generation)
    WHERE decision IS NULL OR (decision='REWORK' AND reopen_received_at IS NULL);
