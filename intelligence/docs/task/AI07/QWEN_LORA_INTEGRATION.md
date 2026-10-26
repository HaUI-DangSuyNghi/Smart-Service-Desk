# AI07 v1.8 — Tích hợp Qwen LoRA

Thay đổi bổ sung provider Qwen3.5/PEFT vào nền model-primary tại
`f3fb70707c921e639c168b80a01aa417b0dc2eb8`, trên nhánh phụ thuộc
`feature/ai07-qwen-lora-provider`. PR #43 vẫn open/draft. Chưa push/merge hoặc bật
backend. Chế độ phân loại mặc định của repo vẫn DISABLED.

## Phần đã triển khai

- Service Desk sở hữu prompt Kaggle và mapping A–F có phiên bản. A–E tương ứng
  ACCOUNT/HARDWARE/NETWORK/OTHER/SOFTWARE; F là ABSTAIN với category null. Cả 185
  message DEV gốc đã khớp cách dựng prompt, gồm spacing JSON, thứ tự key và Unicode.
- Công cụ riêng kiểm 7 file adapter đã pin, 192 tensor safetensors, rank 8/alpha 16,
  base receipt, tokenizer/template, lock thư viện và digest source. Loader còn đối chiếu
  khóa và giá trị tensor LoRA đã nạp với checkpoint.
- Runtime load/warmup trước khi ready, dùng một GPU worker và contract HTTP
  `classification-inference@0.1.0` hiện có. Deadline dừng worker, hạ readiness tới khi
  restart. Khi bận/chưa sẵn sàng trả 503, quá hạn trả 504, output sai trả 502; không
  retry hoặc fallback.
- Giữ nguyên orchestrator/transport Java và HDPI dùng chung. Score null, bằng chứng
  chưa xác minh, luôn cần review; priority độc lập/null và không có quyền vận hành.
  Guard giữ category từ model; SHADOW che phiếu. Ticket thật vẫn qua processing gate
  có chữ ký bên ngoài như trước.
- Docker chỉ chứa code/thư viện; mount model/policy read-only, mạng riêng, offline,
  tài nguyên hữu hạn và secret riêng. Bộ bàn giao có image TAR và script test loopback.
  Không đưa weights vào Git/wheel/image, không đưa dataset/predictions vào bộ test.

Verifier wheel trước đây chỉ chấp nhận `cloud_providers/` dù project integration đã
đóng gói `local_classification/`. Verifier hiện nhận cả hai namespace thuộc integration
và từ chối payload ngoài code. CI chạy fakes tổng hợp, không tải Qwen weights.

## Nguồn artifact và phép đo

| Artifact | Identity / ý nghĩa |
| --- | --- |
| Base repository | `unsloth/Qwen3.5-0.8B` |
| Revision phục vụ tải về | `23c69c53358a07516b5827588b3fdb12ae78fd65` |
| Số byte base weight | 1.746.942.600 |
| SHA-256 base weight | `04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696` |
| SHA-256 adapter | `895b4732e67b8a03fb7ccc2121f6ac87431af0530caf04129a83d492ec5f0c59` |
| Revision base lúc train | UNRECORDED; snapshot phục vụ được dựng lại |
| Huấn luyện | Artifact người dùng cung cấp: 741 machine-silver, 120 bước; không train lại tại đây |
| Export Kaggle | Người tạo báo cáo: 156/185 đồng thuận silver, proxy Macro-F1 0,841004 |
| GPU/replay cục bộ | PASS: 185/185 khớp từng dự đoán Kaggle; 156/185 đồng thuận silver, proxy Macro-F1 0,841004 |
| Phần cứng thực đo | RTX 3050 Laptop 4 GB; CUDA 12.8, PyTorch 2.11.0+cu128, Transformers 5.17.0, PEFT 0.21.2 |
| Warm latency replay | p50 475,221 ms; p95 712,937 ms; lớn nhất 1.039,197 ms; 0/185 vượt 3.200 ms |
| Load và warmup process mới | 38.692,092 ms; chưa xóa cache của hệ điều hành |
| VRAM PyTorch lớn nhất | 1.798.099.456 byte allocated; 1.811.939.328 byte reserved |
| Probe HTTP → HDPI → guard thật | PASS: SOFTWARE, score/priority null, review bắt buộc, SHADOW che phiếu; 630,384 ms |
| Stress tổng hợp | 5 diagnostic riêng, không phải Gold và không gộp vào 185 DEV |

Image phục vụ: `sha256:7ad420520547ab2a345bfdc355db709ef4513ac7480fac8faece1c966e3cce96`.
Model bundle: `a40d655f95f8ed6b6dfa89262b9bc1c99c014233e8ca7b23fe29c4f4dee9576d`.
Prompt/mapping/implementation: `ac07759e7e3cfdcddd5e1259fea78564b270aa46e8a680dc9d0df5ce7b661753`.

Lượt replay đầu hoàn thành đủ 185 inference và giữ journal nhưng bước dựng báo cáo
gặp lỗi chọn average binary cho bài toán đa lớp. Đã sửa thành macro và thêm regression
test. Lượt cuối chạy lại đầy đủ với startup receipt, đạt cùng 185/185 parity. Hai lượt
dùng cùng cohort: chỉ có 185 case DEV duy nhất, không cộng thành mẫu đánh giá lớn hơn.

Lượt tải base đầu bị cắt và bị checksum từ chối. Provisioner hiện kiểm byte range,
độ dài, blob/LFS hash của revision bất biến và tạo completion receipt. Runtime inference
không có đường tải model. Lock CUDA Windows/Linux tách khỏi requirements HDPI chung.

Replay kiểm đúng cohort 185 case, prompt và export gốc với importer v4 hiện có, giữ
toàn bộ attempt/lỗi, báo drift từng case. Transformers/PEFT FP16 có thể khác Unsloth;
không âm thầm thay điểm Kaggle. Timeout nghiên cứu 120 giây tách khỏi giới hạn phục vụ
3,2 giây và HTTP Java 4 giây.

## Kiểm chứng

| Kiểm tra | Kết quả |
| --- | --- |
| Python source đầy đủ | 1.418 passed, 7 skip tùy chọn |
| Installed wheel đầy đủ trong chế độ cô lập | 1.418 passed, 7 skip tùy chọn |
| Qwen tập trung, source / installed wheel | 94 passed mỗi bộ |
| Java/PostgreSQL mặc định | 338 passed, không skip |
| Java core-only | 78 passed, không skip |
| Java ứng dụng thứ hai, gồm HTTP classification | 25 passed, không skip |
| Biên source / mutation đối kháng | PASS / 39 passed |
| Provider ở domain thứ hai, không import app/GPU | PASS với source và wheel |
| Đối chiếu contract Qwen source/wheel | PASS |
| Python lint/format, cú pháp PowerShell | PASS |
| CI hosted tại SHA tích hợp | NOT_RUN; chưa push nhánh |

Các full suite chạy trước những chốt cuối về lock directive, tensor đã load và downloader;
bộ tập trung bao phủ contract cuối. Loader native được kiểm riêng bằng GPU thật.
Receipt và attempt riêng ở `.local/ai07/v18/`, không đưa vào Git.

Workflow nền `40268392046`, job `121395674027`, vẫn không có step đã thực thi:
START_FAILURE_UNDETERMINED. Không kiểm chứng được billing/runner bằng các công cụ
tài khoản hiện có. Không nhận đây là lỗi source hoặc ghi nhận hosted PASS. Review PR
chính thức vẫn là điều kiện bên ngoài.

## Phát hành và bàn giao

Production **NO_GO**. `selectedModel=null`, Gold chưa duyệt, quyền dữ liệu và chữ ký
bằng chứng độc lập còn chờ, chưa có holdout mới. Qwen là candidate engineering dựa
trên machine-silver. Không dùng token/chữ ký lab làm processing receipt cho khách hàng.
PROMOTED tiếp tục bị từ chối.

Xem [hướng dẫn runtime](../../../../infra/compose/README.ai07-qwen-lora.md) để build,
đóng băng bundle, replay, cấu hình backend được phép xử lý, thu hồi và rollback.
README trong bộ Docker có lệnh chạy dành cho thành viên.

Bộ bàn giao cục bộ: `D:\AI07-Qwen-LoRA-Test-Kit-v1.8`. Có 264 file được pin trong
manifest; không có dataset, prediction export, processing grant hoặc secret. Image
`qwen-image.tar.gz` có 4.186.193.616 byte. Đã kiểm toàn bộ checksum, xác minh archive
OCI giữ nguyên image digest và chạy `docker load` trả đúng ID image đã kiểm chứng.
Các script Start/Test/Stop và client trong bộ cuối khớp byte với bộ đã chạy GPU thật.

- SHA-256 `package.json`: `bbdf9eac19f5a63458fd7509c9c10af7409fc6841a3f58255853c5ebcc2834d8`.
- SHA-256 image nén: `5909115585f3b19ad8847724dd5b7411ed4c860085a820ea62ffbd42482ca168`.

## Giấy phép bên thứ ba

Toàn văn LICENSE Apache-2.0 của base Qwen được lưu nguyên byte tại
[licenses/models/qwen35-08b/LICENSE](../../../../licenses/models/qwen35-08b/LICENSE),
kèm revision và checksum. Không tự cấp license cho repo hoặc adapter chưa khai báo.
Bộ Docker giữ 104 notice từ 68 distribution cùng 123 file giấy phép CPython/Debian.
Hai wheel thiếu file license được bổ sung từ source Tokenizers 0.23.3 đã kiểm SHA và
archive EULA CUDA Toolkit 12.8.1 chính thức; xem
[provenance](../../../../licenses/runtime-supplements/provenance.json).
Giấy phép không thay thế quyền dataset, processing grant hoặc phê duyệt chất lượng.
