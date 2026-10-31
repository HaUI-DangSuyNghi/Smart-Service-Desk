-- Trạng thái advisory của Service Desk; không thay đổi trạng thái Flowable.
CREATE TABLE servicedesk_enrichment_case (
    ticket_id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    context_revision bigint NOT NULL CHECK (context_revision >= 0),
    context_digest char(64),
    policy_digest char(64) NOT NULL,
    status varchar(40) NOT NULL CHECK (status IN (
        'NEW','EVALUATING','READY','OPTIONAL_ENRICHMENT','AWAITING_ANSWER',
        'REASSESS_QUEUED','MANUAL_REVIEW','TIMED_OUT','CANCELLED_BY_PROCESS')),
    round_count integer NOT NULL DEFAULT 0 CHECK (round_count BETWEEN 0 AND 2),
    manual_required boolean NOT NULL DEFAULT false,
    late_answer boolean NOT NULL DEFAULT false,
    deadline_at timestamptz,
    reminder_due_at timestamptz,
    reminded_at timestamptz,
    last_evaluated_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    reason_codes jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(reason_codes)='array'),
    readiness_json jsonb,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);

CREATE INDEX servicedesk_enrichment_due ON servicedesk_enrichment_case(deadline_at,ticket_id)
    WHERE status IN ('NEW','REASSESS_QUEUED','EVALUATING','AWAITING_ANSWER','OPTIONAL_ENRICHMENT');
CREATE INDEX servicedesk_enrichment_workspace ON servicedesk_enrichment_case(workspace,updated_at,ticket_id);

CREATE TABLE servicedesk_enrichment_outbox (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    context_revision bigint NOT NULL CHECK (context_revision >= 0),
    event_type varchar(40) NOT NULL CHECK (event_type IN (
        'EVALUATE_CONTEXT','QUESTION_REQUESTED','REMINDER_DUE','MANUAL_REVIEW_REQUIRED')),
    policy_digest char(64) NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING','PROCESSING','DONE','SUPERSEDED','DEAD')),
    available_at timestamptz NOT NULL,
    lease_until timestamptz,
    claim_id uuid,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 2),
    last_error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    UNIQUE (ticket_id,context_revision,event_type,policy_digest)
);
CREATE INDEX servicedesk_enrichment_jobs ON servicedesk_enrichment_outbox(workspace,available_at,id)
    WHERE event_type='EVALUATE_CONTEXT' AND state IN ('PENDING','PROCESSING');

CREATE TABLE servicedesk_enrichment_question_request (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    context_revision bigint NOT NULL CHECK (context_revision >= 0),
    round_count integer NOT NULL CHECK (round_count BETWEEN 1 AND 2),
    policy_digest char(64) NOT NULL,
    questions_json jsonb NOT NULL CHECK (jsonb_typeof(questions_json)='array'
        AND jsonb_array_length(questions_json) BETWEEN 1 AND 3
        AND octet_length(questions_json::text) <= 8192),
    delivery_state varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_state IN ('PENDING','SUPERSEDED')),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    UNIQUE (ticket_id,round_count)
);

-- Ledger metadata cho timer nền: không giả mạo actor người dùng để ghi audit Core.
CREATE TABLE servicedesk_enrichment_transition (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    context_revision bigint NOT NULL,
    case_version bigint NOT NULL,
    reason_code varchar(80) NOT NULL,
    origin varchar(24) NOT NULL CHECK (origin IN ('AUTHENTICATED_COMMAND','DURABLE_TIMER')),
    occurred_at timestamptz NOT NULL,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    UNIQUE (ticket_id,case_version,reason_code)
);
