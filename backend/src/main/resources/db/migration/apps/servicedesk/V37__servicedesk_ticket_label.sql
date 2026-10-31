-- Nhãn AI gán ngay khi chạy nền xong; điều phối viên duyệt thì servicedesk_review thay thế
CREATE TABLE servicedesk_ticket_label (
    ticket_id uuid PRIMARY KEY REFERENCES servicedesk_ticket(id),
    category varchar(20),
    priority varchar(20) NOT NULL,
    -- true: AI không tính được ưu tiên, dùng mức mặc định
    priority_defaulted boolean NOT NULL,
    impact varchar(2),
    urgency varchar(2),
    labeled_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
