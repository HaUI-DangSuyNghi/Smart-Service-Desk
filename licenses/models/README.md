# Giấy phép model bên thứ ba

Thư mục này lưu nguyên văn giấy phép đi kèm model sử dụng trong AI07. Các file này
không cấp một giấy phép chung cho mã nguồn repo, dataset hoặc adapter do người dùng
cung cấp.

| Model | Phạm vi của bản tích hợp | Tài liệu |
| --- | --- | --- |
| `unsloth/Qwen3.5-0.8B` | Base phục vụ Qwen LoRA; revision đã pin trong provenance | [LICENSE đầy đủ](qwen35-08b/LICENSE), [nguồn/checksum](qwen35-08b/provenance.json) |
| Adapter Qwen LabOps | Giữ nguyên model card gốc trong artifact; card chưa khai báo license riêng | Không tự gán license của base cho adapter/dataset |
| Tev1 / Gemma / E5 của các nghiên cứu trước | Không nằm trong bộ Docker Qwen này | Trạng thái review quyền/license của các nghiên cứu cũ giữ nguyên; không suy ra quyền phân phối từ Qwen |

Bộ Docker xuất riêng còn chứa bản kiểm kê và toàn văn giấy phép của các thư viện
đã cài trong đúng image, cùng giấy phép CPython/Debian có sẵn trong image. Các file
được sao chép theo byte gốc và có checksum trong manifest bàn giao.
