-- Bình luận trên ticket: ai xem được ticket thì đọc và viết được, chỉ thêm, không sửa/xoá
CREATE TABLE servicedesk_ticket_comment (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    ticket_id uuid NOT NULL REFERENCES servicedesk_ticket(id),
    author varchar(100) NOT NULL,
    body varchar(2000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX servicedesk_ticket_comment_ticket ON servicedesk_ticket_comment (workspace, ticket_id, created_at);
