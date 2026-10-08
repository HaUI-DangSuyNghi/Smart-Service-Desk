# Phân biệt lỗi phần mềm và phần cứng

- Artifact ID: `kb-software-hardware-001`
- Version: `0.1.0`
- Source: `team-authored:knowledge/software-hardware-first-check`
- Rights status: `PENDING_OWNER_APPROVAL`; chưa cấp license, chưa ingest public corpus.

## Dấu hiệu SOFTWARE

Máy bật được, OS hoạt động, chỉ ứng dụng lỗi/crash, có thông báo cụ thể hoặc lỗi
xuất hiện sau update. Việc update xảy ra trước lỗi là tín hiệu cần kiểm chứng,
không tự chứng minh nguyên nhân. Internet bình thường nhưng một hệ thống vừa
cập nhật báo lỗi: ưu tiên kiểm tra ứng dụng thay vì mạng.

## Dấu hiệu HARDWARE

Không lên nguồn, hư hỏng vật lý rõ ràng, thiết bị ngoại vi không hoạt động hoặc
không được hệ thống nhận. Màn hình không tín hiệu cần ghi máy có còn chạy không,
cáp/trạng thái đèn nhìn thấy được; người dùng không tự tháo máy hoặc sửa nguồn.
Nếu có dấu hiệu cháy/điện bất thường, ngừng sử dụng và báo nhân sự phụ trách.

## Thiếu thông tin

“Máy không dùng được” chưa đủ kết luận. Hỏi máy có bật/đèn nguồn không, OS có vào
được không, toàn máy hay một ứng dụng lỗi, message là gì, phạm vi và workaround.
Nếu chưa rõ category/impact/urgency, `needs_review=true`. Trong fixture N1, OTHER
và MEDIUM chỉ là fallback chờ người review, không phải kết luận nguyên nhân.
