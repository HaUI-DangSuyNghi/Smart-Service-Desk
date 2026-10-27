ALTER TABLE servicedesk_ticket ADD COLUMN process_key varchar(100) NOT NULL DEFAULT 'service-desk';
ALTER TABLE servicedesk_ticket ADD COLUMN worked_millis bigint NOT NULL DEFAULT 0 CHECK (worked_millis >= 0);
ALTER TABLE servicedesk_ticket ADD COLUMN awaiting_review boolean NOT NULL DEFAULT false;
CREATE TABLE servicedesk_review (
    ticket_id uuid PRIMARY KEY REFERENCES servicedesk_ticket(id),
    category varchar(20) NOT NULL CHECK (category IN ('HARDWARE','NETWORK','SOFTWARE','ACCOUNT','OTHER')),
    priority varchar(20) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    estimate_minutes integer NOT NULL CHECK (estimate_minutes BETWEEN 1 AND 10080),
    estimate_basis varchar(20) NOT NULL CHECK (estimate_basis IN ('DEFAULT','COORDINATOR')),
    assignee varchar(100),
    review_task_id varchar(100) NOT NULL,
    reviewed_by varchar(100) NOT NULL,
    reviewed_at timestamptz NOT NULL DEFAULT current_timestamp,
    reason varchar(1000) NOT NULL,
    impact varchar(2),
    urgency varchar(2)
);
CREATE TABLE servicedesk_estimate_history (
    id bigserial PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    review_task_id varchar(100) NOT NULL,
    category varchar(20) NOT NULL,
    priority varchar(20) NOT NULL,
    assignee varchar(100),
    minutes integer NOT NULL CHECK (minutes BETWEEN 1 AND 10080),
    basis varchar(20) NOT NULL CHECK (basis IN ('DEFAULT','COORDINATOR')),
    changed_by varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE INDEX servicedesk_review_queue ON servicedesk_ticket(workspace, process_key, created_at, id);
