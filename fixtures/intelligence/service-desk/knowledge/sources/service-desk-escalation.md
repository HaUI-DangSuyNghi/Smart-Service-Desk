# Quy tắc evidence và escalation cho Service Desk

- Artifact ID: `kb-escalation-001`
- Version: `0.1.0`
- Source: `team-authored:knowledge/service-desk-escalation`
- Rights status: `PENDING_OWNER_APPROVAL`; chưa cấp license, chưa ingest public corpus.

## Evidence tối thiểu

Ticket nên có triệu chứng, phạm vi, thời điểm, phòng/môi trường, workaround và error
message khi có. Chỉ ghi thông tin cần xử lý; không ghi credential/secret hoặc dữ
liệu người dùng thật vào bộ dữ liệu mẫu. Evidence INPUT phải là trích đoạn có thật
trong title/description, không phải câu được engine tự thêm.

## Khi cần thêm thông tin

Không xác định được category, impact, urgency hoặc root cause sơ bộ thì đặt review
true và hỏi thêm. OTHER ngoài taxonomy với đủ evidence khác với OTHER fallback vì
thiếu thông tin. Evidence mâu thuẫn phải được giải thích thay vì che bằng confidence.

## Escalation

Coordinator review sớm khi nhiều phòng bị ảnh hưởng, thi/giảng dạy bị chặn, account
hoặc quyền lỗi diện rộng, sự cố tái diễn, evidence mâu thuẫn hoặc category không rõ.
AI chỉ gợi ý category, priority, support group và knowledge evidence. AI không tự
assign/close ticket, complete workflow, grant access hay assign privileged role.

Nếu Intelligence không sẵn sàng, tiếp tục manual triage. Khi retrieval tồn tại,
citation cần Core workspace + metadata UUID và version/checksum của nguồn đã
authorize. ID `kb-*` local trong N1 chưa phải citation Core và chưa được ingest.
