-- ET gán cùng nhãn AI (R2: trung vị việc tương tự hoặc bảng mặc định); chưa có loại thì không tính
ALTER TABLE servicedesk_ticket_label
    ADD COLUMN estimate_minutes integer,
    ADD COLUMN estimate_basis varchar(20),
    ADD COLUMN estimate_samples integer;
