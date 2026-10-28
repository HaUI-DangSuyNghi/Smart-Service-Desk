-- Lưu bổ sung theo ticket hiện có; không tạo hoặc thay đổi quy trình.
ALTER TABLE servicedesk_ticket
    ADD COLUMN context_revision bigint NOT NULL DEFAULT 0 CHECK (context_revision >= 0);

ALTER TABLE servicedesk_ticket ADD CONSTRAINT servicedesk_ticket_workspace_id UNIQUE (workspace, id);

CREATE TABLE servicedesk_ticket_clarification (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    actor varchar(100) NOT NULL,
    client_answer_id uuid NOT NULL,
    expected_revision bigint NOT NULL CHECK (expected_revision >= 0),
    new_revision bigint NOT NULL CHECK (new_revision = expected_revision + 1),
    answers_json jsonb NOT NULL CHECK (jsonb_typeof(answers_json) = 'array'
        AND jsonb_array_length(answers_json) BETWEEN 1 AND 8
        AND octet_length(answers_json::text) <= 16384),
    payload_digest char(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    FOREIGN KEY (workspace, ticket_id) REFERENCES servicedesk_ticket(workspace, id),
    UNIQUE (ticket_id, actor, client_answer_id),
    UNIQUE (ticket_id, new_revision)
);

CREATE INDEX servicedesk_clarification_snapshot
    ON servicedesk_ticket_clarification(workspace, ticket_id, new_revision);
