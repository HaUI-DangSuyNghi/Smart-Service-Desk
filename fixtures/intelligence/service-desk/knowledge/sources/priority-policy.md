# Chính sách đánh giá Impact và Urgency

- Artifact ID: `kb-priority-001`
- Version: `0.1.0`
- Source: `team-authored:knowledge/priority-policy`
- Rights status: `PENDING_OWNER_APPROVAL`; chưa cấp license, chưa ingest public corpus.

## Impact

I1: một user/thiết bị. I2: nhóm nhỏ/vài thiết bị, không phải toàn bộ phòng/lớp.
I3: toàn phòng/lớp/lab. I4: nhiều phòng, dịch vụ dùng chung hoặc diện rộng.
Số lượng đơn thuần không quyết định level; phải biết tổng phạm vi bị chặn.

## Urgency

U1: workaround được phép, không ảnh hưởng đáng kể hiện tại. U2: công việc bị ảnh
hưởng nhưng có thể chờ. U3: chặn hoạt động hiện tại. U4: chặn hoạt động quan trọng
hoặc cấp bách, cần phản ứng ngay. Priority không phụ thuộc category đơn thuần.

| Impact / urgency | U1 | U2 | U3 | U4 |
| --- | --- | --- | --- | --- |
| I1 | LOW | LOW | MEDIUM | HIGH |
| I2 | LOW | MEDIUM | HIGH | HIGH |
| I3 | MEDIUM | HIGH | HIGH | CRITICAL |
| I4 | HIGH | HIGH | CRITICAL | CRITICAL |

## Ví dụ và quyết định con người

Một máy mất Wi-Fi nhưng dùng được hotspot theo quy định, không chặn công việc:
I1/U1 → LOW. Cả lab mất Internet, chặn thực hành: I3/U3 → HIGH. Cả lab không mở
ứng dụng thi ngay trước giờ thi: I3/U4 → CRITICAL.

Thiếu impact hoặc urgency thì hỏi thêm, không invent level. Fixture ambiguous dùng
MEDIUM tạm thời với review true; đây không phải một ô matrix. Human được override;
future application nên lưu suggested/final priority và accepted/overridden để đánh
giá, thay vì để Intelligence tự đổi workflow hoặc state Core.
