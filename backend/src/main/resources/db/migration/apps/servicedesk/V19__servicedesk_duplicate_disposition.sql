CREATE TABLE servicedesk_duplicate_context (
    ticket_id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    context_revision bigint NOT NULL,
    context_digest char(64) NOT NULL,
    room varchar(100),
    object_key varchar(200),
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    FOREIGN KEY (workspace,ticket_id) REFERENCES servicedesk_ticket(workspace,id)
);
CREATE INDEX servicedesk_duplicate_room_object ON servicedesk_duplicate_context(workspace,room,object_key);

CREATE TABLE servicedesk_intake_notice (
    id uuid PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    workspace varchar(100) NOT NULL,
    context_revision bigint NOT NULL,
    kind varchar(40) NOT NULL,
    delivery_status varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE(ticket_id,context_revision,kind)
);

CREATE TABLE servicedesk_canonical_link (
    follower_id uuid PRIMARY KEY,
    root_id uuid NOT NULL,
    workspace varchar(100) NOT NULL,
    follower_revision bigint NOT NULL,
    root_revision bigint NOT NULL,
    category varchar(20) NOT NULL CHECK (category IN ('ACCOUNT','HARDWARE','NETWORK','SOFTWARE','OTHER')),
    state varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','CLOSED','SPLIT')),
    version bigint NOT NULL DEFAULT 1,
    public_status varchar(40) NOT NULL DEFAULT 'FOLLOWING',
    decision_id uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CHECK (follower_id<>root_id),
    FOREIGN KEY (workspace,follower_id) REFERENCES servicedesk_ticket(workspace,id),
    FOREIGN KEY (workspace,root_id) REFERENCES servicedesk_ticket(workspace,id)
);
CREATE INDEX servicedesk_canonical_root ON servicedesk_canonical_link(workspace,root_id) WHERE state='ACTIVE';
CREATE TABLE servicedesk_canonical_decision (
    id uuid PRIMARY KEY,
    follower_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    root_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    operation_id uuid NOT NULL,
    action varchar(20) NOT NULL,
    payload_digest char(64) NOT NULL,
    actor varchar(100) NOT NULL,
    reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE(follower_id,operation_id)
);
