# ADR-004 — Nền tảng truy xuất TF-IDF có kiểm chứng nguồn

Ngày: 17/10/2026. Trạng thái: triển khai library nội bộ; chờ BE duyệt tích hợp.
Baseline: `develop@f348beb200b84bb31f2550879d38e744fa695230`.

AI-03 chọn TF-IDF unigram/bigram, cosine trên vector L2, không dùng mô hình,
embedding hay dịch vụ bên ngoài. `SearchEngine` là điểm thay thế cho AI-04.
Điểm số dùng xếp hạng, không biểu thị xác suất hay độ tin cậy.

Core giữ quyền xác thực, phân quyền, metadata và file gốc. Adapter phải kiểm tra
quyền hiện tại trước mỗi lần chọn partition; `TrustedContext.auth_scope_fingerprint`
là scope opaque do adapter cấp, phải đổi khi tập tài liệu được phép đọc đổi.
Scope không bao giờ lấy từ request. Không mặc định mọi thành viên đọc cùng corpus.

Citation phải được resolver Core có phân quyền xác minh lại theo workspace,
metadata UUID, version, SHA-256 byte gốc và chunk. UUID hợp lệ chỉ là kiểm tra
định dạng. Tài liệu mất hoặc đổi version/checksum bị bỏ; mất quyền gây lỗi
`AuthorizationRejected`; resolver gián đoạn gây `CorpusUnavailable`.
Library mặc định truyền lỗi vận hành ra ngoài. Adapter có thể bật ánh xạ DEGRADED
sau khi thống nhất chính sách tích hợp; không chuyển lỗi phân quyền sang DEGRADED.

Chunker `markdown-paragraph@0.1.0` giữ đoạn và heading theo thứ tự, giới hạn
900 ký tự, không overlap. Normalizer dùng lại `text-normalization@0.1.0`
(NFC, casefold, khoảng trắng và biến thể Wi-Fi), giữ dấu tiếng Việt.

Không sửa profile AI-02 bất biến hoặc search schema v0.1. Evaluation dùng ID
`kb-*` riêng, không tạo Core binding giả. Fixture vẫn chưa ingest và còn chờ
chủ sở hữu xác nhận quyền; kết quả chỉ là hồi quy trên dữ liệu tự biên soạn.

Hệ quả: index có thể dựng lại; không cache kết quả; không có endpoint production.
BE-03-A (scope/quyền file), OWNER-03-A (quyền dữ liệu), QA và CI Linux/Windows
phải được kiểm chứng độc lập trước khi hợp nhất/phát hành.
