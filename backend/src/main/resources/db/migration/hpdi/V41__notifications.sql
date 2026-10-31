-- Trung tâm thông báo: mọi đổi trạng thái của quy trình (NOTICE) và bản sao cảnh báo (ALERT) cho từng người nhận.
-- Mỗi dòng người nhận kiêm hàng đợi email: PENDING → SENT / FAILED (thử lại có giãn cách), NONE = chỉ trong app.
CREATE TABLE hpdi_notification (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    type varchar(60) NOT NULL CHECK (type ~ '^[A-Z][A-Z0-9_]{1,59}$'),
    category varchar(10) NOT NULL CHECK (category IN ('NOTICE', 'ALERT')),
    severity varchar(10) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title varchar(300) NOT NULL,
    body varchar(1000),
    link varchar(500),
    process_key varchar(100),
    business_key varchar(200),
    actor varchar(100),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE hpdi_notification_recipient (
    workspace varchar(100) NOT NULL,
    notification_id uuid NOT NULL REFERENCES hpdi_notification(id),
    subject varchar(100) NOT NULL,
    read_at timestamptz,
    email_status varchar(10) NOT NULL DEFAULT 'NONE' CHECK (email_status IN ('NONE', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    email_attempts int NOT NULL DEFAULT 0,
    email_next_at timestamptz,
    email_error varchar(300),
    emailed_at timestamptz,
    PRIMARY KEY (notification_id, subject)
);
CREATE INDEX hpdi_notification_inbox ON hpdi_notification_recipient (workspace, subject, notification_id);
CREATE INDEX hpdi_notification_mail ON hpdi_notification_recipient (email_next_at) WHERE email_status = 'PENDING';
