ALTER TABLE servicedesk_intake_admission
    ADD COLUMN processing_digest char(64),
    ADD COLUMN review_task_id varchar(100);
ALTER TABLE servicedesk_intake_receipt ADD COLUMN review_task_id varchar(100);

CREATE TABLE servicedesk_ai_feedback (
    id uuid PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    review_round integer NOT NULL,
    context_revision bigint NOT NULL,
    review_task_id varchar(100) NOT NULL,
    proposal jsonb,
    human_decision jsonb NOT NULL,
    actor varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL,
    evaluation_status varchar(40) NOT NULL DEFAULT 'NOT_REVIEWED_GOLD',
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE(ticket_id,review_task_id)
);
