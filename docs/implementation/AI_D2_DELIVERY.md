# AI D2 — Tiến độ triển khai và bàn giao

Baseline: `050a32faf72c8306e719c8384a257b9b48fa2caa`, fetch ngày 27/10/2026.
Nhánh: `feature/ai-d2-intake-portable-delivery`. PR #47/#51 đã nằm trong baseline;
R1/R3 ở PR #52 là dependency bên backend, không triển khai lại trong nhánh này.

| Cụm | Nội dung | Trạng thái |
| --- | --- | --- |
| C0 | Pin baseline, ADR, phạm vi và acceptance | Đã ghi nhận |
| C1 | Server review admission, CAS, timeout/manual/rework | Đang triển khai |
| C2 | Sealed final contextual assessment và worker single-flight | Chưa kiểm chứng |
| C3 | Duplicate retrieval/incident, feedback và workflow/UI | Chưa kiểm chứng |
| C4 | Full model image, provenance, transport secret và Compose | Chưa kiểm chứng |
| C5 | Offline distribution/onboarding/acceptance | Chưa kiểm chứng |

ET vẫn là bảng mặc định và coordinator override. Không thêm effortSize hay model ET.
Auto canonical merge, chất lượng Gold, SLA/notification của toàn bộ ticket spec và
production GO cần quyết định/receipt độc lập; không dùng synthetic để thay thế.

Artifact local tìm thấy: adapter safetensors và tokenizer tại
`.local/ai07_qwen35_silver_lora_results`. Base serving snapshot và receipt quyền
phân phối chưa được xác minh. Không commit model, prediction, dữ liệu hoặc secrets.
