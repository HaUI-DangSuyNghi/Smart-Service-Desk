-- R7: ticket AI gợi ý CRITICAL đang chờ duyệt; worker nhắc một lần khi quá hạn rồi đóng
CREATE TABLE servicedesk_critical_watch (
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    process_instance_id varchar(100) NOT NULL,
    process_key varchar(100) NOT NULL,
    generation varchar(200) NOT NULL,
    advised_at timestamptz NOT NULL,
    closed_at timestamptz,
    PRIMARY KEY (workspace, ticket_id, generation)
);
CREATE INDEX servicedesk_critical_watch_open ON servicedesk_critical_watch(advised_at) WHERE closed_at IS NULL;
