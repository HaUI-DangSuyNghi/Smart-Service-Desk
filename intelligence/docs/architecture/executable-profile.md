# Profile thực thi AI-01.5

Trạng thái: PROPOSED, chờ BE và Process Owner review. Profile mới là
`service-desk@0.2.0`, rules `0.2.0`; taxonomy, normalization và priority policy
tiếp tục dùng bản v0.1. Contract wire giữ nguyên `0.1.0`.

Registry chỉ nhận identity/version chính xác; v0.1 là tài liệu lịch sử, không
thực thi. Không tự chuyển version không tồn tại sang bản mới nhất. Các file được
khóa bằng SHA-256 trong `profiles/immutable-artifacts.json`; thay đổi nội dung
phải có version mới. Lock chỉ là bằng chứng toàn vẹn trong repo, không là chữ ký
hay approval. Reviewer cần kiểm tra mọi thay đổi lock của version đã tồn tại.

Luật chỉ nhận literal và count template có đúng một `{count}` với khoảng số
nguyên hữu hạn 2–9999. Không hỗ trợ regex do người cấu hình cung cấp; trường
`regex`/`pattern` bị từ chối. Runtime biên dịch pattern nội bộ từ literal đã
escape và tối đa bốn chữ số; không có regex tùy ý hoặc backtracking lồng nhau.
Tín hiệu được normalize cùng input, có biên từ Unicode, đánh giá từng field.
Không nối hai field để tạo ra một evidence không tồn tại.

Category được thu thập đầy đủ. Một candidate được chọn trực tiếp; nhiều candidate
chỉ chọn khi đúng một override có evidence cho tất cả nhóm `allOf`, winner
đã match và toàn bộ đối thủ nằm trong `overruledRules`. Nhiều winner hoặc không
có winner dẫn đến review. Thứ tự YAML không quyết định kết quả. SOFTWARE thắng
NETWORK khi có bằng chứng các website khác hoạt động và lỗi sau cập nhật;
ACCOUNT thắng NETWORK khi có lỗi khóa/xác thực và Wi-Fi hoạt động với người khác.

Impact/urgency được giải quyết riêng. `overrides` chỉ loại các level được nêu
tường minh: I3 loại I2 count/group; I4 loại I2/I3; U4 loại U3. Những tổ hợp còn
nhiều level là ambiguity. Không tự chọn mức cao nhất. Không đủ category/impact/
urgency thì dùng toàn bộ fallback của policy, không bịa level. Category fallback
rule là dấu vết dùng OTHER, không khẳng định có tín hiệu OTHER trong input.

Priority và group chỉ đọc từ policy/taxonomy. Matched rules theo thứ tự category,
conflict, impact, urgency, priority, review; mỗi nhóm sắp rule ID. Evidence chỉ
substring nguyên bản, deduplicate theo field/text, tối đa 16 mục; fallback dùng
title nguyên bản nếu không có match. Mọi kết quả chỉ là gợi ý cho người dùng.

Normalizer giữ NFC, casefold, collapse whitespace và alias Wi-Fi theo v0.1.
Unicode/runtime được pin trong package và trace. `normalizationVersion` chỉ ở
trace nội bộ vì schema wire v0.1 không cho phép trường này.

Exception taxonomy: `ProfileNotFound`, `ProfileInvalid`,
`UnsupportedProfileVersion`, `ContractValidationError`, `EngineConfigurationError`,
`EngineUnavailable`, `AuthorizationRejected`, `CorpusUnavailable`,
`CitationVerificationError`. Chỉ `EngineUnavailable` là operational failure của
AI-02 và được map sang DEGRADED. Lỗi cấu hình, lỗi lập trình, contract và quyền
truy cập được raise; không che thành engine unavailability. Hai exception corpus/
citation được dành cho AI-03, chưa có retrieval implementation.
