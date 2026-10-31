# Đánh giá Gold cho AI07 phân cấp

Trạng thái: công cụ kỹ thuật đã có; chưa có bộ Gold phân cấp được duyệt hoặc
holdout niêm phong cho ứng viên này. Không công bố chất lượng từ fixture tổng hợp.

## Chuẩn bị và duyệt dữ liệu riêng tư

Giữ 185 mẫu DEV machine-silver hiện có làm regression kỹ thuật. Chọn 150–300 mẫu
ban đầu từ các họ ticket khác nhau; đây là mục tiêu thu thập, không đảm bảo đủ
ý nghĩa thống kê. Phân tầng cả năm main category, child phổ biến/hiếm, đa triệu
chứng, thiếu thông tin, ngoài miền, tiếng Việt trộn tiếng Anh và các cực I/U.
Báo cáo support từng lớp/child, mở rộng lớp thiếu mẫu trước khi đưa ra kết luận.

Hai người duyệt độc lập từng mẫu và một bước phân xử bất đồng. Tách nhãn
mainCategory, subCategory, impact, urgency, riskFlags, evidence, needsClarification.
Cho phép null biểu thị chưa biết; không ép nhãn con vào `*_OTHER`. Taxonomy child
và rubric rủi ro phải được chủ miền phê duyệt trước khi dùng làm chuẩn phát hành.
Evidence là trích đoạn nguyên văn với field, quote, offset Unicode code point.
Không lưu mật khẩu, OTP, token hoặc dữ liệu riêng trong Git.

Mỗi dòng JSONL riêng tư gồm `sampleId`, `familyDigest`, `split`, `fields` chứa
title/description, và `labels` với các nhãn nêu trên. Xem
`contracts/apps/servicedesk/intelligence/private-hierarchical-review-v0.1.schema.json`.
Tách DEV khỏi training theo family; HOLDOUT độc lập với cả training và DEV, niêm
phong trước khi tuning. Không dùng lại HOLDOUT để tối ưu chính sách.

## Bản đóng băng và danh tính

Manifest có chữ ký HMAC-SHA256 do bên duyệt độc lập cấp, cùng định dạng envelope
`payload` base64 và `signature` hex của pipeline AI07 hiện có. Payload yêu cầu:

- `purpose=AI07_HIERARCHICAL_REVIEWED_EVALUATION`, `status=REVIEWED_AND_FROZEN`,
  `issuer`, thời hạn `expiresEpochSecond`, `rowsDigest`, `split` DEV hoặc HOLDOUT.
- `rightsReceipt`, tối thiểu hai `reviewerReceipts` khác nhau,
  `adjudicationReceipt`, `rubricDigest`, `evaluationPlanDigest`.
- `candidateModelDigest`, `candidatePolicyDigest`, `assessmentPolicyBundleDigest`.
- `trainingFamilyDigests`, `developmentFamilyDigests` để kiểm tra chống giao thoa.

Mọi receipt là SHA-256 của bằng chứng được lưu bên ngoài. Chữ ký xác thực bên cấp
và byte dữ liệu; không tự chứng minh nội dung receipt đã được tổ chức chấp thuận.
Không có lệnh CLI tự tạo Gold, tự ký grant hoặc tự promote.

Prediction JSONL gồm `caseId`, `result` là response assessment đúng schema và
`latencyMs` nếu đã đo thật. Mỗi mẫu đúng một attempt; phải lưu riêng attempt khác,
không chọn lại kết quả đẹp nhất. Đánh giá main classifier riêng bằng pipeline
`evaluate-reviewed-gold.py` hiện có, rồi đánh giá end-to-end bằng công cụ mới.

```powershell
# Khóa xác minh do bên duyệt độc lập cung cấp qua đường dẫn bí mật đã được duyệt.
$env:AI07_REVIEW_TRUST_KEY_FILE = '<private-owner-key-path>'
python scripts/apps/servicedesk/ai07/evaluate-hierarchical-assessment.py `
  --review-manifest '<private-manifest.json>' `
  --reviewed-rows '<private-reviewed.jsonl>' `
  --predictions '<private-predictions.jsonl>' `
  --output .local/ai07/v19/reviewed-report.json
```

Runner đối chiếu hash byte taxonomy/assessment/matrix từ backend; không có bản
chính sách Python thứ hai. Chỉ ghi report mới trong `.local/ai07`, không ghi đè
receipt cũ. Kiểm tra chữ ký, thời hạn, quyền dữ liệu, reviewer, family, danh tính
model/chính sách trước khi chấm. Ví dụ trong contract và unit test chỉ là fixture
tổng hợp thuộc dự án, không phải suy luận Qwen hay dữ liệu Gold thật.

## Chỉ số và gate

Report gồm main Macro-F1 trên mọi attempt, support/precision/recall/confusion,
coverage, abstention, invalid rate; child exact-match trên mẫu đủ nhãn, F1 từng
child, parent violation và nhận sai mẫu mơ hồ; I/U F1, phát hiện UNKNOWN, recall
U4 và false-positive U4. Child Macro-F1 có tính cả child chưa có support với F1=0;
luôn đọc support kèm theo. Attempt invalid vẫn tính là bỏ sót, không bị loại khỏi mẫu số.

Priority kiểm tra ma trận và exact-match với I/U đã phân xử. Risk chỉ chấm cờ,
không chấm severity. Evidence kiểm tra field và literal span cả ở attributes;
độ phù hợp về ý nghĩa vẫn cần reviewer. Không có score đã hiệu chuẩn thì
ECE/Brier/selective risk là `NOT_AVAILABLE`.

Latency p50/p95/p99 chỉ có khi nộp số đo; VRAM, cold-start, concurrency, thời gian
từng stage và load/fault test cần receipt vận hành riêng. Không dùng fixture
latency hay ~713 ms model-only để kết luận SLO end-to-end.

Process Owner/QA/Security phải khai báo ngưỡng trước khi chạy, gồm mức support,
U4 recall/false-positive, lỗi nghiêm trọng, coverage và latency. Matrix consistency,
parent invariant và literal provenance phải đạt 100% trên phần có thể kiểm chứng.
Report luôn giữ `selectedModel=null`, `productionDecision=NO_GO`; dữ liệu có chữ
ký không tự động thay thế gate rights, security, hosted CI hay quyết định phát hành.
