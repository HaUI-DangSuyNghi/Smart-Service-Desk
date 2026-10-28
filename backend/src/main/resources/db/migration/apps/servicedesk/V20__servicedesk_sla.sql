CREATE TABLE servicedesk_sla (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    process_instance_id varchar(100) NOT NULL,
    review_task_id varchar(100) NOT NULL,
    handle_task_id varchar(100) NOT NULL,
    policy jsonb NOT NULL CHECK (jsonb_typeof(policy) = 'object'),
    base_priority varchar(20) NOT NULL CHECK (base_priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    priority varchar(20) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    approved_at timestamptz NOT NULL,
    start_by timestamptz NOT NULL,
    resolve_by timestamptz NOT NULL,
    first_started_at timestamptz,
    resolved_at timestamptz,
    paused_at timestamptz,
    escalated_at timestamptz,
    checked_at timestamptz NOT NULL,
    PRIMARY KEY (workspace, ticket_id, review_task_id),
    UNIQUE (workspace, handle_task_id),
    FOREIGN KEY (workspace, ticket_id) REFERENCES servicedesk_ticket(workspace, id),
    CHECK (start_by >= approved_at AND resolve_by >= start_by),
    CHECK (first_started_at IS NULL OR first_started_at >= approved_at),
    CHECK (resolved_at IS NULL OR (first_started_at IS NOT NULL AND resolved_at >= first_started_at)),
    CHECK (paused_at IS NULL OR (first_started_at IS NOT NULL AND resolved_at IS NULL AND paused_at >= first_started_at)),
    CHECK (priority = base_priority OR escalated_at IS NOT NULL)
);
CREATE INDEX servicedesk_sla_reconcile ON servicedesk_sla (checked_at, workspace, handle_task_id) WHERE resolved_at IS NULL;
CREATE INDEX servicedesk_sla_ticket ON servicedesk_sla (workspace, ticket_id, approved_at DESC);
