CREATE TABLE servicedesk_ticket (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    creator varchar(100) NOT NULL,
    client_request_id uuid NOT NULL,
    title varchar(200) NOT NULL CHECK (length(btrim(title)) > 0),
    description varchar(4000) NOT NULL CHECK (length(btrim(description)) > 0),
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    process_instance_id varchar(100) NOT NULL UNIQUE REFERENCES process_scope(id),
    UNIQUE (workspace, creator, client_request_id)
);
CREATE INDEX servicedesk_ticket_workspace_created ON servicedesk_ticket(workspace, created_at DESC, id DESC);
CREATE INDEX servicedesk_ticket_creator_created ON servicedesk_ticket(workspace, creator, created_at DESC, id DESC);
