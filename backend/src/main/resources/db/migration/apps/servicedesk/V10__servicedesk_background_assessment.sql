CREATE TABLE servicedesk_assessment (
    ticket_id uuid PRIMARY KEY REFERENCES servicedesk_ticket(id),
    status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','READY','DEGRADED')),
    version integer NOT NULL DEFAULT 1 CHECK (version=1),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    available_at timestamptz NOT NULL DEFAULT current_timestamp,
    lease_token uuid,
    lease_until timestamptz,
    result jsonb,
    error_code varchar(50),
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK ((status='READY') = (result IS NOT NULL))
);
CREATE INDEX servicedesk_assessment_pending ON servicedesk_assessment(available_at,ticket_id) WHERE status='PENDING';
CREATE TABLE servicedesk_assessment_history (
    id bigserial PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_assessment(ticket_id),
    attempt integer NOT NULL,
    event varchar(20) NOT NULL,
    error_code varchar(50),
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp
);
