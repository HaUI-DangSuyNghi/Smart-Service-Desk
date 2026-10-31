CREATE TABLE servicedesk_alert_monitor (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    process_instance_id varchar(100) NOT NULL,
    process_key varchar(100) NOT NULL,
    review_task_id varchar(100) NOT NULL,
    handle_task_id varchar(100) NOT NULL,
    category varchar(20) NOT NULL,
    assignee varchar(100),
    pending_at timestamptz,
    group_empty boolean NOT NULL DEFAULT false,
    group_generation integer NOT NULL DEFAULT 0 CHECK (group_generation>=0),
    assignee_off boolean NOT NULL DEFAULT false,
    off_generation integer NOT NULL DEFAULT 0 CHECK (off_generation>=0),
    active boolean NOT NULL DEFAULT true,
    checked_at timestamptz NOT NULL,
    PRIMARY KEY (workspace,handle_task_id),
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);
CREATE INDEX servicedesk_alert_poll ON servicedesk_alert_monitor(checked_at,workspace,handle_task_id) WHERE active;
