CREATE TABLE servicedesk_intake_admission (
    ticket_id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    context_revision bigint NOT NULL CHECK (context_revision >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    review_round integer NOT NULL DEFAULT 1 CHECK (review_round > 0),
    status varchar(40) NOT NULL DEFAULT 'NEW' CHECK (status IN (
        'NEW','NEEDS_ENRICHMENT','DUPLICATE_HOLD','FINAL_ASSESSMENT_QUEUED',
        'ASSESSING','REVIEW_RELEASED','MANUAL_REQUIRED','REVIEWED','CANONICAL_FOLLOWER')),
    deadline_at timestamptz NOT NULL,
    sealed_revision bigint,
    context_digest char(64),
    policy_digest char(64) NOT NULL,
    readiness_json jsonb,
    duplicate_json jsonb,
    final_json jsonb,
    attempt_key char(64),
    lease_token uuid,
    lease_until timestamptz,
    late_notice boolean NOT NULL DEFAULT false,
    reason varchar(1000),
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);
CREATE INDEX servicedesk_intake_due ON servicedesk_intake_admission(workspace,deadline_at,ticket_id)
    WHERE status NOT IN ('REVIEWED','REVIEW_RELEASED','MANUAL_REQUIRED','CANONICAL_FOLLOWER');

CREATE TABLE servicedesk_intake_receipt (
    id uuid PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_intake_admission(ticket_id),
    operation_id uuid NOT NULL,
    action varchar(40) NOT NULL,
    context_revision bigint NOT NULL,
    review_round integer NOT NULL,
    context_digest char(64),
    policy_digest char(64) NOT NULL,
    payload_digest char(64) NOT NULL,
    actor varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL,
    final_json jsonb,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE (ticket_id,operation_id)
);

CREATE TABLE servicedesk_final_attempt (
    identity char(64) PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_intake_admission(ticket_id),
    context_revision bigint NOT NULL,
    review_round integer NOT NULL,
    state varchar(20) NOT NULL CHECK (state IN ('RUNNING','DONE','DEGRADED','STALE')),
    lease_token uuid NOT NULL,
    result jsonb,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    finished_at timestamptz
);
