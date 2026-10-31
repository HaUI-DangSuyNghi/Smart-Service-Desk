-- Đăng ký ca theo ô (ngày × ca trong Cấu hình): mỗi ô một dòng, đăng ký / huỷ / duyệt từng ô
CREATE TABLE hpdi_agent_slot (
    id uuid PRIMARY KEY,
    workspace varchar(100) NOT NULL,
    subject varchar(100) NOT NULL,
    slot_date date NOT NULL,
    shift_name varchar(50) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED')),
    registered_by varchar(100) NOT NULL,
    decided_by varchar(100),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at timestamptz,
    CHECK (ends_at > starts_at),
    UNIQUE (workspace, subject, slot_date, shift_name)
);
CREATE INDEX hpdi_agent_slot_week ON hpdi_agent_slot (workspace, slot_date);
CREATE INDEX hpdi_agent_slot_now ON hpdi_agent_slot (workspace, subject, starts_at) WHERE status = 'APPROVED';

-- Cấu hình đăng ký ca: mặc định không cho T7/CN, không cần duyệt
UPDATE hpdi_work_settings
SET payload = payload || '{"schedule":{"weekendRegistration":false,"approvalRequired":false}}'::jsonb
WHERE NOT payload ? 'schedule';
