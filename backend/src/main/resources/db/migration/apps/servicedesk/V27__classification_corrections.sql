ALTER TABLE servicedesk_review ADD COLUMN estimate_generation varchar(100);
ALTER TABLE servicedesk_review ADD COLUMN correction_incumbent varchar(100);
ALTER TABLE servicedesk_sla
    ADD COLUMN manual_priority varchar(20) CHECK (manual_priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    ADD COLUMN start_breached_history boolean NOT NULL DEFAULT false,
    ADD COLUMN resolve_breached_history boolean NOT NULL DEFAULT false,
    ADD COLUMN start_budget_adjustment_millis bigint NOT NULL DEFAULT 0,
    ADD COLUMN resolve_budget_adjustment_millis bigint NOT NULL DEFAULT 0;
DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN SELECT conname FROM pg_constraint
        WHERE conrelid='servicedesk_sla'::regclass AND contype='c'
        AND pg_get_constraintdef(oid) LIKE '%priority%base_priority%escalated_at%'
    LOOP
        EXECUTE format('ALTER TABLE servicedesk_sla DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE servicedesk_sla ADD CONSTRAINT servicedesk_sla_priority_amendment CHECK (
    priority=base_priority OR escalated_at IS NOT NULL OR manual_priority IS NOT NULL
);
CREATE TABLE servicedesk_classification_correction (
    operation_id uuid NOT NULL,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    process_instance_id varchar(100) NOT NULL,
    review_task_id varchar(100) NOT NULL,
    handle_task_id varchar(100) NOT NULL,
    field varchar(20) NOT NULL CHECK (field IN ('category','priority')),
    previous_value varchar(20) NOT NULL,
    new_value varchar(20) NOT NULL CHECK (new_value<>previous_value),
    changed_by varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL CHECK (length(trim(reason))>0),
    changed_at timestamptz NOT NULL,
    from_ai boolean NOT NULL DEFAULT false CHECK (NOT from_ai),
    PRIMARY KEY (operation_id,field),
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    CHECK ((field='category' AND previous_value IN ('HARDWARE','NETWORK','SOFTWARE','ACCOUNT','OTHER')
        AND new_value IN ('HARDWARE','NETWORK','SOFTWARE','ACCOUNT','OTHER'))
        OR (field='priority' AND previous_value IN ('LOW','MEDIUM','HIGH','CRITICAL')
        AND new_value IN ('LOW','MEDIUM','HIGH','CRITICAL')))
);
CREATE INDEX servicedesk_correction_history ON servicedesk_classification_correction(workspace,ticket_id,changed_at,operation_id);
CREATE TABLE servicedesk_sla_amendment (
    operation_id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    handle_task_id varchar(100) NOT NULL,
    before_state jsonb NOT NULL,
    after_state jsonb,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);
