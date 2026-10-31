-- R2 giai đoạn 2: ET theo trung vị thời gian làm của việc đã đóng cùng loại × ưu tiên
ALTER TABLE servicedesk_review DROP CONSTRAINT servicedesk_review_estimate_basis_check;
ALTER TABLE servicedesk_review ADD CONSTRAINT servicedesk_review_estimate_basis_check
    CHECK (estimate_basis IN ('DEFAULT','COORDINATOR','SIMILAR'));
-- Số việc dùng tính trung vị; chỉ đọc khi nguồn là SIMILAR (đổi nguồn khác không cần xóa)
ALTER TABLE servicedesk_review ADD COLUMN estimate_samples integer
    CHECK (estimate_samples IS NULL OR estimate_samples > 0);
ALTER TABLE servicedesk_estimate_history DROP CONSTRAINT servicedesk_estimate_history_basis_check;
ALTER TABLE servicedesk_estimate_history ADD CONSTRAINT servicedesk_estimate_history_basis_check
    CHECK (basis IN ('DEFAULT','COORDINATOR','SIMILAR'));
-- Đọc mẫu theo workspace, loại, ưu tiên khi duyệt
CREATE INDEX servicedesk_confirmation_closed ON servicedesk_confirmation(workspace, decided_at)
    WHERE decision IN ('REQUESTER','AUTO');
