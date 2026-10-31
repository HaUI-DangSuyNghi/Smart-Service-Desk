# AI07 v1.10 — xử lý review chất lượng và đo vận hành

## P0: facts định lượng và thời gian

Chính sách assessment được nâng patch lên `servicedesk-assessment@0.1.1` trong
artifact dòng `assessment-policy-v0.1.json`. Bộ trích xuất đọc grammar từ artifact
này, không sao chép chính sách sang Python. Chính sách vẫn DRAFT, mọi đề xuất cần
người duyệt và không thay đổi trạng thái/priority thực của ticket.

Ví dụ `14 máy trong phòng B305 không truy cập được Internet, tiết học bắt đầu sau
8 phút` tạo facts số lượng 14 và thời gian tương đối 8 phút, với bằng chứng nguyên
văn và offset Unicode code point. Kết quả đề xuất là I2/U4/HIGH. Không suy toàn bộ
phòng từ việc nhắc B305. I3 cần scope cả phòng hoặc một phòng được mô tả rõ là
đối tượng bị ảnh hưởng; nhiều phòng có thể tạo I4.

Số lượng phải liên kết với triệu chứng trong cùng mệnh đề. Một phòng có 14 máy
nhưng chỉ một máy hỏng tạo I1; thông tin kho 14 máy đơn thuần không tạo impact.
Số âm, số thập phân/nhóm số không được hỗ trợ, lượng ước chừng hoặc giả định được
để unknown. Không cộng các con số ở nhiều triệu chứng thành tổng chưa được xác minh.

U4 dùng event học/thi/deadline tương lai gắn trực tiếp với khoảng thời gian, đổi
đơn vị phút/giờ bằng số thập phân chính xác. Ngưỡng **15 phút là đề xuất DRAFT**
để mở rộng các ví dụ 10/15 phút trước đây; chưa phải SLA được Process Owner duyệt.
Thời gian sửa máy hoặc tải đề không tự tạo U4. Sự cố giả định, đã khắc phục và
thông tin quá khứ được loại khỏi facts ở mệnh đề tương ứng. U1–U3 tiếp tục dùng
các tín hiệu thận trọng hiện có; đây chưa phải bộ hiểu ngôn ngữ tự nhiên tổng quát.

Response thêm các attribute `affectedCount`, `deadlineMinutes`. `deadlineMinutes`
không phải timestamp hay SLA timer. Đó là khoảng thời gian được thuật lại: khi
không có neo thời gian đã xác minh, U4 chỉ là đề xuất theo lời mô tả, không chứng
minh hạn chót còn hiệu lực khi chạy lại ticket cũ. Bundle digest thay đổi, nên grant
cũ không cấp quyền cho bộ chính sách mới. Bên cấp grant phải đối chiếu hash mới.

## P1: nhiều triệu chứng và sub-tag

Resolver chỉ chọn child trong parent Qwen đã đề xuất. Một child phù hợp duy nhất
vẫn được giữ khi có triệu chứng thuộc parent khác; disposition thêm
`CROSS_CATEGORY_SYMPTOMS_REQUIRE_REVIEW`. Bằng chứng của parent khác không được dùng
làm bằng chứng cho child đã chọn. Hai child cùng parent vẫn AMBIGUOUS; chỉ có bằng
chứng ở parent khác vẫn TAXONOMY_CONFLICT. Không ghi đè main category.

Guard `REVIEW_CONFLICT` yêu cầu duyệt, không còn làm mọi tác vụ abstain đồng loạt.
Từng bộ đánh giá tự xử lý mâu thuẫn của nó. `SAFETY_GATE` cho prompt injection vẫn
chặn trích xuất. Không thay đổi default DISABLED hoặc các gate processing/promotion.

Benchmark riêng `multi-symptom-v0.1.json` có 30 ca tổng hợp được đóng băng: 20 ca
unique child có thêm triệu chứng khác miền, 10 ca ambiguity/unsupported. Parent
là đầu vào mô phỏng, không phải dự đoán Qwen thật. Báo cáo có coverage/abstention
theo cohort; không dùng tập này để công bố accuracy Gold hay chọn champion.

## P1: rubric risk severity

Đã soạn [đề xuất rubric](RISK_SEVERITY_PROPOSAL.md) và artifact JSON tương ứng.
Chưa có phê duyệt của Process Owner/Security/Operations, do đó API vẫn giữ level
null. Không biến rubric do agent soạn thành quyết định phê duyệt. Cần tập Gold
cho hậu quả/độ chắc chắn, reviewer có thẩm quyền và release receipt trước khi nối
severity vào hợp đồng chính thức. Risk tiếp tục độc lập với ma trận priority.

## P1: tải đồng thời và khởi động process

`TicketAssessmentQwenLoadIT` chỉ chạy khi opt-in, dùng dữ liệu tổng hợp riêng tư.
Runner tạo project, network, DB và token riêng; kiểm hash kit Qwen trước khi chạy.
Model/bundle mount read-only, không mở cổng model ra host, dọn container/network
tạm sau chạy và giữ receipt trong `.local/ai07/review-performance`.

```powershell
./scripts/apps/servicedesk/ai07/benchmark-qwen-assessment.ps1 `
  -QwenKitDirectory .local/ai07/v18/smoke-kit-r2 `
  -Samples 100 -ColdCycles 2 -BuildImage ai07-v19-maven:local
```

Mỗi cold cycle khởi động process model mới và đo thời gian quan sát readiness
bằng Docker healthcheck, sau đó đo request đầu, ba lượt warmup và ba phase mức
đồng thời 1/2/4. Các request đến theo batch đồng thời, dùng barrier và chờ cả batch
xong trước batch kế tiếp; đây chưa phải tải open-loop với arrival rate cố định.
Worker Qwen đã chạy warmup nội bộ trước khi ready. Trường `firstInferenceMs`
trong receipt là request assessment đầu tiên **sau** readiness, không phải phép
đo GPU inference chưa warmup. Cache file/OS có thể còn nóng; readiness có độ phân giải theo lịch
healthcheck. Không gọi các số này là cold boot của máy hay thời gian load kernel
chính xác. Source fingerprint và policy digest phải giữ nguyên trong mỗi run.

Report tách p50/p95/p99 của request thành công khỏi tất cả attempt, số degraded/
từ chối, mã lỗi và goodput thành công. Không coi độ trễ từ chối nhanh là phục vụ
nhanh. Semaphore/circuit/timeout hiện có không được tự nới để làm benchmark đẹp.
Request dùng MockMvc + DB + HTTP Python + Qwen thật; chưa gồm OIDC/gateway hoặc
processing grant thật, nên chưa đủ để kết luận SLO production.

Chưa có SLO được chủ hệ thống duyệt: mọi report giữ
`sloDecision=NOT_EVALUATED_NO_APPROVED_SLO`, `productionDecision=NO_GO`.
Không lấy trung bình các p95 giữa phase/run làm p95 tổng.

## Kiểm chứng và số đo đã hoàn tất

- Java/Linux/Postgres sạch: **469 pass**, không failure/error/skip; Windows: **155 pass**.
- Python nguồn: **1.479 pass, 7 skip** tùy chọn; kiểm tra hierarchy trên wheel đã cài: **50 pass**.
- Strict boundary và **39 mutation** qua; foundation **145 ca âm tính** qua.
- Multi-symptom: **20/20** ca unique child được giữ, **10/10** ca ambiguity/unsupported
  vẫn abstain theo kỳ vọng. Đây là regression tổng hợp, chưa phải Gold hoặc F1 Qwen.
- Hai cold cycle chạy thành công; kiểm tra số lượng ticket/task/audit sau assessment
  không đổi. Có 600 attempt đo tải, thêm hai request đầu và sáu request warmup riêng.

Receipt không chứa ticket text, token hay grant tại
[evidence/v1.10-operational-summary.json](evidence/v1.10-operational-summary.json).
Run `400088ea662f4ea6aff612516f4dfbe7`, ghi nhận lúc `2026-10-26T17:05:00Z`
(00:05 ngày 27/10/2026 tại Việt Nam). Policy digest là
`ed6b1ebfa821a8f417e9e2d3278057651b485ca869458aacef9e067a6d61a958`.
Source fingerprint giữ nguyên trước/sau từng cycle; image model và Maven đều được ghim SHA.

| Đồng thời | Xử lý thành công / attempt hai cycle | Degraded | p95 thành công của từng cycle (ms) | p99 thành công của từng cycle (ms) | Goodput (request/giây) |
|---|---|---|---|---|---|
| 1 | 200/200 | 0% | 500,80 / 487,90 | 524,69 / 518,92 | 2,21 / 2,26 |
| 2 | 100/200 | 50% | 482,25 / 482,84 | 489,04 / 504,01 | 2,29 / 2,29 |
| 4 | 50/200 | 75% | 539,16 / 498,96 | 616,82 / 499,47 | 2,07 / 2,21 |

Readiness quan sát được: **71,93 / 71,35 giây**, gồm khởi động/load/warmup và độ trễ
healthcheck. Request assessment đầu sau readiness: **1.071,71 / 1.021,26 ms**.
Không dùng hai mẫu này để công bố p95 cold-start. Phase đồng thời 4 chỉ có 25
request thành công mỗi cycle; p99 nearest-rank khi đó chính là giá trị lớn nhất.

Degraded ở mức đồng thời 2/4 mang `INTELLIGENCE_UNAVAILABLE`, phù hợp với bulkhead
một request của transport. p95 thấp không chứng minh hệ thống phục vụ được mọi
request. Trước production cần chốt workload, arrival rate, tỷ lệ degraded chấp nhận
được, SLO và chiến lược admission/queue với Operations; không tự tăng concurrency
trên GPU để đổi lấy báo cáo đẹp. Chưa có Gold, ký duyệt severity hoặc SLO production.
