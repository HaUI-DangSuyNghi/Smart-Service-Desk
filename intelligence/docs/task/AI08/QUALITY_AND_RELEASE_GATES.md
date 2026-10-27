# Chất lượng và điều kiện phát hành AI08

## Phạm vi bằng chứng

Engine authoritative là Java trong ứng dụng Service Desk. Ba policy JSON nằm ở
`backend/src/main/resources/apps/servicedesk/intelligence`; report pin thêm taxonomy
và factual policy AI07 được tái sử dụng. Không thêm lượt Qwen, model hay vector DB.
Các policy và fixture được pin LF trong `.gitattributes` để digest giữ nguyên
khi checkout giữa Windows và Linux.

Fixture commit gồm **100 ca synthetic thuộc 10 family**, mỗi ca có nhãn slot,
missing slot, revision/đính chính và quyết định pair. Cohort gồm positive, khác
thiết bị/phòng, đã đóng, category chưa duyệt, thiếu ID, nhiều phòng, đính chính,
mâu thuẫn dè dặt và prompt injection. Đây là kiểm tra logic, **không phải Gold**.
Không dùng biến thể cùng family để tuyên bố hiệu quả trên holdout độc lập.

`ContextQualityEvaluationTest` chạy đúng engine, xác minh literal evidence theo
nguồn, đo slot/missing/pair với mẫu số cụ thể và xuất
`backend/target/context-quality-evaluation.json`. Đo CPU ở concurrency 1/2/4,
200 request/mức, 50 pair/request, warmup trước đo. Báo p50/p95/p99 và degraded;
không đưa thời gian DB, authorization hay HTTP vào số CPU.

`scripts/apps/servicedesk/ai08/evaluate-context.py` đối chiếu fixture, policy và
Surefire XML, bắt buộc có suite PostgreSQL/Flowable không skipped. Receipt ghi
SHA, trạng thái working tree, digest, mẫu số, support theo quyết định và giới hạn.
Không ghi đè receipt cũ. Không thể dùng receipt này thay chữ ký của người duyệt.

## Gate kỹ thuật

| Gate | Điều kiện |
| --- | --- |
| Identity | Bổ sung đúng ticket, không tạo ticket/process/task mới |
| Giao dịch | Replay một sự kiện; đổi payload/stale revision trả 409; audit rollback đồng bộ |
| Context | Quote/offset đúng nguồn; hỗ trợ Unicode, đính chính; không bịa slot |
| Readiness | Profile theo nhiệm vụ; tối đa 3 câu hỏi; questionId ổn định để client chống lặp |
| Dedup | R10 hard blockers, category verified, shared mapping phải được duyệt |
| Privacy | Không quét chéo workspace hoặc tiết lộ ID ứng viên cho requester |
| Revocation | Snapshot/candidate/workflow/grant thay đổi thì fail closed |
| Mode | Default off, SHADOW không lộ suy luận, PROMOTED bị chặn |
| Contract | Additive; từ chối authority, confidence số, field ngoài allowlist |
| Boundary | Source guard, mutation guard, ArchUnit, core-only, second-domain |
| Regression | Java/DB và Python source/wheel; AI07 v0.2 giữ nguyên |

questionId ổn định theo profile/slot/trạng thái. Client chỉ hiện một lần cho cùng
questionId còn pending; đây không phải dịch vụ push có sổ theo dõi đã gửi câu hỏi.

CPU mục tiêu: extraction p95 ≤30 ms; 50 pair p95 ≤50 ms. Mục tiêu phụ thuộc phần
cứng và cần đọc report thực tế. Không tính request suy giảm thành request thành công.
Candidate scan bounded là thuộc tính đã test, không phải bằng chứng recall trên Gold.

## Gate production còn phải hoàn thành

- Process owner duyệt room/object/shared-service semantics, ai có quyền link và
  giao dịch canonical workflow. AI vẫn không tự gộp.
- Thu thập tập được người kiểm duyệt, phân xử nhãn khó và chia theo incident family;
  đóng băng holdout/manifest độc lập trước đo.
- Đo precision MATCH với mẫu số và khoảng tin cậy; ngưỡng đề xuất 0,98 cần xác nhận.
  Recall@20 đề xuất 0,95 chỉ cho cohort có đủ blocking facts; công bố ca bị loại.
- Kết nối category đã được duyệt từ nguồn nghiệp vụ; hiện runtime chưa có nguồn này.
- Đo SLO truy vấn DB và HTTP, timeout/soak trên tải dự kiến; synthetic CPU không thay thế.
- Xác nhận quyền dữ liệu, security review, kế hoạch vận hành, rollback và chữ ký rollout.
- Hosted CI phải chạy trên đúng SHA hoặc có ngoại lệ được chấp thuận. Chưa chạy là
  `UNVERIFIED`; chỉ ghi `BLOCKED_BILLING` khi có bằng chứng billing, không tự suy đoán.

Quyết định production hiện là **NO_GO**. Không dùng điểm synthetic hoặc trạng thái
test local để mở `PROMOTED`, tự cấp grant hoặc diễn giải thành cho phép auto-link.
