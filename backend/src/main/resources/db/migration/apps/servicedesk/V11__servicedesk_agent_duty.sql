CREATE TABLE servicedesk_agent_duty (
    workspace varchar(50) NOT NULL,
    subject varchar(100) NOT NULL,
    on_duty boolean NOT NULL DEFAULT false,
    note varchar(500),
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (workspace, subject)
);
