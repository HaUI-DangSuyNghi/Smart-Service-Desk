# Đề xuất rubric risk severity

Đây là bản dự thảo được tạo theo yêu cầu review, **chưa phải quyết định phê duyệt**.
Artifact có cấu trúc tại
`contracts/apps/servicedesk/intelligence/risk-severity-rubric-v0.1.proposal.json`.
API hiện tại tiếp tục `risk.level=null`; không dùng ngưỡng I/U hoặc category để
suy severity. Rubric này chưa được nối vào luồng tính priority hoặc workflow.

| Mức đề xuất | Điều kiện cần | Ví dụ để người duyệt phân xử |
|---|---|---|
| LOW | Hậu quả nhỏ, phạm vi rõ, biện pháp khôi phục đã kiểm chứng | Dữ liệu tạm có thể phục hồi từ bản sao đã kiểm tra; chưa có vấn đề nghiêm trọng hơn đang chờ xác minh |
| MEDIUM | Gián đoạn/tổn thất thực tế, khả năng khôi phục hoặc giới hạn lan truyền được xác minh | Dịch vụ ngừng tạm thời, đã cô lập nguyên nhân và kiểm chứng được đường khôi phục |
| HIGH | Hậu quả nghiêm trọng được xác minh | Truy cập trái phép đang được điều tra có chứng cứ; dữ liệu đã được xác nhận mất không thể phục hồi |
| CRITICAL | Nguy hại đang diễn ra hoặc hậu quả cực kỳ nghiêm trọng được reviewer có thẩm quyền xác minh | Thiết bị đang cháy; compromise chưa kiểm soát ở tài sản được tổ chức định nghĩa là trọng yếu |
| null | Thiếu chứng cứ, mâu thuẫn, chỉ giả định hoặc báo cáo chưa xác minh | Không thấy dữ liệu không chứng minh dữ liệu đã mất; chữ 'khẩn cấp' không chứng minh severity |

Độ chắc chắn (`VERIFIED/UNVERIFIED/CONFLICT/UNKNOWN`) phải ghi riêng. Không biến
lời người gửi thành sự thật đã kiểm chứng chỉ vì LLM đồng ý. Mỗi kết luận cần
quote, field và offset nguyên văn; reviewer có thể bổ sung receipt qua luồng được
phân quyền. Không yêu cầu người dùng cung cấp mật khẩu, OTP hay token.

Các ca âm tính bắt buộc gồm: diễn tập cháy, hướng dẫn phishing, sự cố đã khắc phục,
ticket I4/U4 nhưng không có hậu quả rủi ro được xác minh, nguồn chứng cứ mâu thuẫn,
và báo cáo không đề cập rủi ro. Không mặc định LOW cho các ca này.

Trước khi nối vào API, Process Owner/Security/Operations cần duyệt định nghĩa tài
sản trọng yếu, quyền xác minh và ngưỡng chất lượng theo từng loại cờ. Cần Gold được
double-review/phân xử và một quyết định phát hành ghim `rubricDigest`. API mới phải
phân biệt đề xuất chưa xác minh với level được phê duyệt; không tự bật severity
bằng một cờ cấu hình hay chữ ký xử lý dữ liệu thông thường.
