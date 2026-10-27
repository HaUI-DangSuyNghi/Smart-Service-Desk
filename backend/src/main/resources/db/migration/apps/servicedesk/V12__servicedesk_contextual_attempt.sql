-- Mỗi input có ảnh hưởng tới model và mỗi quyền xử lý chỉ nhận tối đa một lượt gọi.
CREATE TABLE servicedesk_contextual_attempt (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL,
    input_digest char(64) NOT NULL,
    processing_digest char(64) NOT NULL,
    policy_digest char(64) NOT NULL,
    model_digest char(64) NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('PROCESSING','DONE','DEGRADED')),
    category varchar(24) CHECK (category IS NULL OR category IN ('ACCOUNT','HARDWARE','NETWORK','SOFTWARE','OTHER')),
    claim_id uuid NOT NULL,
    started_at timestamptz NOT NULL DEFAULT current_timestamp,
    completed_at timestamptz,
    PRIMARY KEY (workspace,ticket_id,input_digest,processing_digest,policy_digest,model_digest),
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);
