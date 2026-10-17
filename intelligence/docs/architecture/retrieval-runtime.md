# Runtime truy xuất AI-03

Luồng: kiểm tra contract → TrustedContext → profile chính xác → adapter xác nhận
scope hiện tại → registry chọn đúng partition → chuẩn hóa → chấm điểm → kiểm
chứng citation hiện tại → kiểm tra response → trace không chứa nội dung.

Partition bao gồm workspace, profile/version, hash scope, corpus version,
chunk/normalization version và engine/version/config. Không chọn `latest`.
`AuthorizedCorpusSource` là giao diện adapter, không phải một bộ cấp quyền.
Adapter production phải dùng API Core được hỗ trợ, kiểm tra cả metadata và file.
AI-03 chỉ cung cấp protocol và test double; chưa có credentials/adapter production.

Các mối đe dọa và biện pháp:

| Mối đe dọa | Kiểm soát |
| --- | --- |
| Giả workspace/scope từ client | TrustedContext bắt buộc; scope không thuộc request schema |
| Chấm điểm tài liệu không có quyền | Adapter xác nhận trước registry và engine; IDF cũng chỉ fit partition |
| Dùng index cũ sau thu hồi quyền | Adapter kiểm tra lại mỗi request, đổi scope; resolver kiểm tra lại từng citation |
| Nguồn thay đổi | So sánh version/checksum; bỏ hit cũ và đếm stale |
| Engine bịa chunk hoặc excerpt | Kiểm tra hit thuộc index; verifier dựng lại chunk từ nguồn Core |
| Artifact cục bộ giả citation | Runtime yêu cầu UUID và resolver; evaluation tách biệt |
| File index độc hại | JSON có giới hạn, dựng lại và so manifest; không pickle/joblib.load |
| Rò query/nội dung vào trace | Chỉ ghi ID tương quan, phiên bản, số lượng, thời gian và loại lỗi |

Query không có từ phù hợp trả SUCCESS rỗng, `needsReview=true`. Không thêm hit
điểm 0. Tie-break theo source ID rồi ordinal/chunk ID. Không gọi retrieval trong
triage; triage tiếp tục hoạt động khi corpus bị lỗi.

Giới hạn nội bộ được kiểm tra: query 8.192 ký tự, source 2 MiB, 4.096 chunk/source,
1.000 document/partition, tổng 16 MiB source/partition. Đây là chặn tài nguyên
cho baseline nhỏ, không phải SLA hoặc thay đổi schema dùng chung.
