-- Hình thức làm việc của kỹ thuật viên theo workspace: SHIFT đăng ký từng ca, OFFICE trực giờ hành chính
-- (mọi ca trong Cấu hình, T2-T6, trừ ngày lễ) không cần đăng ký. Chưa có dòng = SHIFT.
CREATE TABLE hpdi_agent_work_mode (
    workspace varchar(100) NOT NULL,
    subject varchar(100) NOT NULL,
    mode varchar(16) NOT NULL CHECK (mode IN ('SHIFT', 'OFFICE')),
    updated_by varchar(100) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (workspace, subject)
);

-- Ngày lễ được nghỉ theo Bộ luật Lao động 2019 (Điều 112), ngày làm việc trong tuần.
-- Tết, ngày liền kề Quốc khánh và nghỉ bù do Chính phủ công bố hằng năm: "(dự kiến)" cần kiểm tra lại
-- trong Cấu hình khi có thông báo chính thức.
UPDATE hpdi_work_settings
SET payload = jsonb_set(payload, '{schedule,holidays}', '[
  {"date":"2026-01-01","name":"Tết Dương lịch"},
  {"date":"2026-02-16","name":"Tết Nguyên đán"},
  {"date":"2026-02-17","name":"Tết Nguyên đán"},
  {"date":"2026-02-18","name":"Tết Nguyên đán"},
  {"date":"2026-02-19","name":"Tết Nguyên đán"},
  {"date":"2026-02-20","name":"Tết Nguyên đán"},
  {"date":"2026-04-27","name":"Nghỉ bù Giỗ Tổ Hùng Vương"},
  {"date":"2026-04-30","name":"Ngày Chiến thắng"},
  {"date":"2026-05-01","name":"Quốc tế Lao động"},
  {"date":"2026-09-01","name":"Quốc khánh (dự kiến ngày liền kề)"},
  {"date":"2026-09-02","name":"Quốc khánh"},
  {"date":"2027-01-01","name":"Tết Dương lịch"},
  {"date":"2027-02-04","name":"Tết Nguyên đán (dự kiến)"},
  {"date":"2027-02-05","name":"Tết Nguyên đán (dự kiến)"},
  {"date":"2027-02-08","name":"Tết Nguyên đán (dự kiến)"},
  {"date":"2027-02-09","name":"Tết Nguyên đán (dự kiến)"},
  {"date":"2027-02-10","name":"Tết Nguyên đán (dự kiến)"},
  {"date":"2027-04-16","name":"Giỗ Tổ Hùng Vương"},
  {"date":"2027-04-30","name":"Ngày Chiến thắng"},
  {"date":"2027-05-03","name":"Nghỉ bù Quốc tế Lao động (dự kiến)"},
  {"date":"2027-09-02","name":"Quốc khánh"},
  {"date":"2027-09-03","name":"Quốc khánh (dự kiến ngày liền kề)"}
]'::jsonb)
WHERE payload ? 'schedule' AND NOT payload->'schedule' ? 'holidays';
