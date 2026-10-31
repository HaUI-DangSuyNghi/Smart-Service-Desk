# Nghiệp vụ: phân loại, giao việc và hàng đợi ticket

Mô tả luồng và luật để các nhóm BE, AI, FE code theo. Con số ghi _(cấu hình)_ là giá trị mặc định, admin hệ thống chỉnh được (mục 6). Hợp đồng API theo plan này: mục 8.

**Vai trò:** người dùng (`requester`), kỹ thuật viên (`agent`, thuộc một hoặc nhiều tổ), điều phối viên (`coordinator`).

| Vai trò | Được làm | Giới hạn |
| --- | --- | --- |
| `requester` | Gửi ticket, xem ticket của mình, xác nhận / báo "Chưa xong" | Không xem ticket người khác |
| `agent` | Xem ticket trong workspace, xử lý ticket được giao | **Không xử lý ticket do chính mình gửi** |
| `coordinator` | Xem mọi ticket workspace, duyệt, giao, giao lại | Muốn tự xử lý phải có thêm quyền `agent` |
| `platform-admin` | Cấu hình hệ thống (mục 6) | **Không** tự có quyền xem ticket của các workspace |

- Cả ba vai trò đều gửi được ticket. Một người có thể nhiều vai trò, vẫn chịu các giới hạn trên.
- Quyền tách theo **workspace**: có quyền ở workspace A không có nghĩa được vào workspace B.
- Người thao tác lấy từ phiên đăng nhập, không tin trường người gửi do trình duyệt đưa lên.
- Xem được ticket **không** có nghĩa xem được toàn bộ lịch sử quy trình; lịch sử chỉ mở cho người liên quan.

---

## 1. Luồng

1. **Người dùng gửi ticket** (R0).
2. **AI đánh giá** (mục 2): backend tự gọi AI **ở nền** ngay sau khi tạo (không bắt người dùng chờ), lưu kết quả vào ticket. Backend gán thời gian ước tính (ET) theo R2.
3. **Thiếu thông tin** → hiện câu hỏi gợi ý (`nextQuestions`) cho điều phối viên hỏi người dùng. _(Vòng "người dùng trả lời → AI đánh giá lại" để giai đoạn sau.)_
4. **Có thể trùng ticket đang mở** → R10 _(chờ chốt)_.
5. **Hệ thống đề xuất** tổ (R1) và kỹ thuật viên (R2, R3).
6. **Điều phối viên duyệt**: giữ hoặc sửa loại lỗi, ưu tiên, ET, người xử lý (R4).
   AI không chắc, loại OTHER, AI lỗi hoặc tổ không ai trực → điều phối viên tự chọn.
7. **Vào hàng đợi** của kỹ thuật viên (R4, R5). Thông báo người được giao và người dùng.
8. **Kỹ thuật viên xử lý**: Bắt đầu → (Đang chờ ↔ Tiếp tục) → Báo xong (R6).
9. **Theo dõi**: cảnh báo sắp/quá hạn, giao lại khi bị bỏ quên (R7, R8).
10. **Người dùng xác nhận** → đóng, hoặc "chưa xong" → quay lại (R11).
11. **Lưu kết quả** để AI học và đo độ chính xác (mục 5).
12. **Thống kê** theo phạm vi quyền (R12).

---

## 2. AI trả về

Theo khả năng hiện tại của nhóm AI (triage `service-desk@0.3.0` trên `develop`, AI07 `assistant/assess` và `assistant/classify` ở nhánh `feature/ai07-ticket-intelligence`). Đầu vào: chỉ tiêu đề + mô tả đã lưu của ticket.

**AI trả được (dùng ngay)**

| Trường | Ví dụ / giá trị | Dùng để | Nguồn |
| --- | --- | --- | --- |
| `category` | `HARDWARE` · `NETWORK` · `SOFTWARE` · `ACCOUNT` · `OTHER`; `null` khi AI không chắc (`ABSTAIN`) | Chọn tổ (R1) | Triage, AI07 |
| `impact` | `I1` một máy · `I2` vài máy · `I3` cả phòng · `I4` nhiều phòng | Tính ưu tiên | Triage |
| `urgency` | `U1` có cách tạm · `U2` chờ được · `U3` bị chặn · `U4` rất gấp | Tính ưu tiên | Triage |
| `priority` | `LOW` · `MEDIUM` · `HIGH` · `CRITICAL` (bảng mục 4) | Xếp hàng, SLA | Triage |
| `suggestedGroup` | `IT-HARDWARE`… | Gợi ý tổ | Triage |
| `evidence` | Đoạn trích nguyên văn từ tiêu đề / mô tả | Người duyệt thấy căn cứ | Triage |
| `explanation` | `classificationReason`, `priorityReason` (câu tiếng Việt) | Người duyệt hiểu vì sao | AI07 |
| `needsReview`, `reasonCode` | `CLASSIFIED` · `ROOT_CAUSE_CONFLICT` · `INSUFFICIENT_INFORMATION` | Đánh dấu cho điều phối viên | Triage |
| `nextQuestions` | Tối đa 2 câu **cố định**, chỉ khi thiếu thông tin | Hỏi thêm người dùng (bước 3) | AI07 |
| `guidance` | Trích dẫn nguyên văn từ tài liệu, kèm nguồn và phiên bản nguồn | Kỹ thuật viên / người dùng tham khảo | AI06, AI07 |

**AI không làm – phương án thay thế**

| Trường trong thiết kế ban đầu | Thay bằng |
| --- | --- |
| `confidence`, `alternatives` | **Bỏ.** AI không có độ tin cậy đã hiệu chuẩn; dùng `needsReview` và `category = null` (`ABSTAIN`) |
| `missingInfo` + câu hỏi có lựa chọn | `nextQuestions` cố định |
| `estimatedEffortMinutes` (ET) | **Backend tính** (R2) |
| `incidentSignal` | **Backend đếm** ticket cùng loại, cùng khu vực (R10), không cần AI |
| `location`, `device`, `summary`, `duplicateOf` | Chờ chốt R10 |

**Giai đoạn sau** _(nhóm AI nhận khi có dữ liệu)_

| Trường | Dùng để |
| --- | --- |
| `effortSize`: `SMALL` · `MEDIUM` · `LARGE`; không chắc thì `ABSTAIN` | Backend chọn ET theo cỡ (R2 giai đoạn 3) |
| Đánh giá lại sau khi người dùng trả lời câu hỏi | Bước 3 |

**Cách backend dùng AI**
- Sau khi tạo ticket (R0), backend gọi AI **ở nền**; phản hồi tạo ticket không chờ AI.
- Kết quả **lưu vào ticket** (`assessment`) kèm phiên bản: `PENDING` (đang chạy) → `READY`, hoặc `DEGRADED` (AI lỗi / tắt). Mở ticket nhiều lần vẫn thấy cùng một gợi ý, không gọi AI lại.
- FE **không** gọi API AI để phân loại; chỉ đọc `assessment` trong ticket và điền sẵn vào form duyệt (R4).
- Riêng `guidance` (hướng dẫn theo câu hỏi người dùng nhập), FE gọi trực tiếp như hiện tại.

**Yêu cầu chung:** chỉ là gợi ý (AI không tự giao việc hay đổi trạng thái) · có phiên bản đi kèm · không đủ căn cứ thì nói rõ, không đoán · chỉ dùng dữ liệu người hỏi được xem.

- **Kiểm quyền hai lần:** trước khi gọi AI và trước khi trả kết quả (quyền có thể bị thu hồi trong lúc chờ).
- **Đầu vào lấy từ ticket đã lưu**, không nhận nội dung tự do từ trình duyệt.
- **AI lỗi hoặc đang tắt** không chặn luồng: ticket vẫn đi tiếp, điều phối viên tự chọn (bước 6).
- `guidance` thiếu căn cứ trả `INSUFFICIENT_EVIDENCE`; có căn cứ thì kèm nguồn **và phiên bản nguồn**.

---

## 3. Luật nghiệp vụ

### R0. Gửi ticket

| Trường | Luật |
| --- | --- |
| `title` | 1–200 ký tự |
| `description` | 1–4.000 ký tự |
| `clientRequestId` | UUID mỗi lần bấm gửi, chống bấm gửi hai lần |

- Lưu ticket, khởi tạo quy trình và ghi nhật ký **cùng một giao dịch**: hỏng một phần thì huỷ hết, không có ticket dở dang.
- Gửi lại cùng `clientRequestId` (cùng người, cùng workspace) → trả `409`, không tạo ticket mới. Khác với **ticket trùng** ở R10 (hai người báo cùng một lỗi).
- Ticket chỉ được tạo qua API của app Service Desk; gọi thẳng API khởi tạo quy trình của Core không sinh ticket.

### R1. Chọn tổ

| Loại lỗi | Tổ | Group Keycloak |
| --- | --- | --- |
| HARDWARE | Kỹ thuật phần cứng | `/workspaces/<ws>/hardware` |
| NETWORK | Kỹ thuật mạng | `/workspaces/<ws>/network` |
| SOFTWARE | Hỗ trợ phần mềm | `/workspaces/<ws>/software` |
| ACCOUNT (quên mật khẩu, khoá tài khoản, quyền) | Quản trị hệ thống | `/workspaces/<ws>/sysadmin` |
| OTHER | Điều phối viên tự chọn | — |

- Tổ là group **cùng cấp với `agent`**. Kỹ thuật viên thuộc `agent` và ít nhất một tổ.
- Làm được nhiều mảng thì thuộc nhiều tổ. Ví dụ anh A làm cả phần cứng và phần mềm → thuộc cả `hardware` và `software`; tải của anh A tính chung (R2).
- Danh sách tổ ở trên là mặc định. Đơn vị triển khai tự thêm, bớt hoặc gộp tổ trong file cấu hình (ví dụ thêm tổ "Thiết bị phòng học" cho máy chiếu, loa).

**Gán kỹ thuật viên vào tổ bằng file cấu hình**
- Danh sách tổ và thành viên khai báo sẵn trong file cấu hình Keycloak của dự án (`infra/keycloak/realm-template.json`, cùng chỗ khai báo tài khoản demo).
- Người lấy dự án về **sửa file này** theo nhân sự thực tế của họ (ai thuộc tổ nào, ở workspace nào); README hướng dẫn cách sửa.
- Chạy `scripts/start.ps1` thì Keycloak **tự khởi tạo** tổ và thành viên từ file. Sửa file sau khi đã chạy thì chạy lại với `-Reset` để nạp lại (Keycloak chỉ tự nạp lần đầu).

### R2. Thời gian ước tính và chọn kỹ thuật viên

**ET (thời gian ước tính)** = số phút **làm** dự kiến, không tính thời gian chờ. **Backend tính**, AI không trả số phút.

Lấy theo thứ tự, có nguồn trên thì bỏ qua nguồn dưới:

| Thứ tự | Nguồn | Khi nào | `estimateBasis` hiển thị |
| --- | --- | --- | --- |
| 1 | Điều phối viên nhập | Khi duyệt (R4) hoặc đồng ý yêu cầu sửa của kỹ thuật viên | "Điều phối viên đặt" |
| 2 | Trung vị theo **loại × ưu tiên × cỡ việc** | Giai đoạn 3, AI trả `effortSize` và nhóm đó đủ mẫu | "Trung vị 18 ticket tương tự (cỡ LỚN)" |
| 3 | Trung vị theo **loại × ưu tiên** | Nhóm đó đủ mẫu _(cấu hình: 20 ticket)_ | "Trung vị 42 ticket tương tự" |
| 4 | Bảng mặc định | Chưa đủ dữ liệu | "Mặc định theo loại" |

**Bảng mặc định** _(cấu hình, đơn vị: phút làm)_

| Loại lỗi | LOW | MEDIUM | HIGH | CRITICAL |
| --- | --- | --- | --- | --- |
| HARDWARE | 45 | 60 | 90 | 120 |
| NETWORK | 30 | 45 | 60 | 90 |
| SOFTWARE | 30 | 30 | 45 | 60 |
| ACCOUNT | 15 | 15 | 20 | 30 |
| OTHER | 30 | 45 | 60 | 90 |

- Ưu tiên cao thường do **ảnh hưởng rộng** (I3 cả phòng, I4 nhiều phòng, mục 4) → nhiều máy, nhiều thiết bị hơn → lâu hơn.
- HARDWARE lâu nhất: phải đến tận nơi, có thể tháo lắp, thay linh kiện. ACCOUNT nhanh nhất: thường làm từ xa (mở khoá, đặt lại mật khẩu, cấp quyền).
- Số làm tròn bội 15 phút. Đây là điểm khởi đầu; đủ dữ liệu thì trung vị thay thế (thứ tự 3).

**Cách tính trung vị**
- Mẫu = thời gian làm thực tế (tổng các khoảng Đang làm, mục 5) của ticket **Đã đóng**.
- Chỉ lấy ticket đóng trong **6 tháng gần nhất** _(cấu hình)_, để theo kịp thay đổi của đội.
- Bỏ giá trị bất thường: dưới 2 phút hoặc trên 8 giờ _(cấu hình)_ (thường do quên bấm Bắt đầu / Tạm dừng).
- Dùng **trung vị**, không dùng trung bình cộng, để vài ticket rất dài không kéo lệch.
- Tính lại định kỳ (mỗi đêm) hoặc khi gán ET; ET đã gán cho ticket thì không tự đổi theo.

**Sửa ET**
- Kỹ thuật viên thấy ET không hợp lý thì **xin ước tính lại** kèm lý do; điều phối viên đồng ý hoặc từ chối.
- Mỗi lần sửa ET đều được lưu (mục 5).

**Lộ trình**

| Giai đoạn | Ai làm | Nội dung |
| --- | --- | --- |
| 1 | Backend | Bảng mặc định, điều phối viên sửa, ghi thời gian làm thực tế |
| 2 | Backend | Đủ mẫu thì dùng trung vị loại × ưu tiên |
| 3 | AI + Backend | AI đọc nội dung, trả `effortSize`; backend tính trung vị theo cỡ. AI `ABSTAIN` thì dùng mức giai đoạn 2 |

AI chỉ **phân loại cỡ việc** (việc AI làm tốt, như phân loại lỗi), không đoán số phút và không cần học lại: có thêm dữ liệu thì backend tự tính lại.

**Chọn người** trong tổ:
1. Chỉ xét người **đang trực** (R3), **loại người gửi ticket** (không ai tự xử lý ticket của mình).
2. **Tải** = tổng phút còn phải làm:

   | Ticket đang giữ | Tính |
   | --- | --- |
   | Chưa bắt đầu | ET |
   | Đang làm | ET − đã làm (tối thiểu 10 phút) |
   | Đang chờ | Phần còn lại × 0,5 _(cấu hình)_ |

3. Đề xuất người **tải thấp nhất**. Chênh dưới 15 phút _(cấu hình)_ thì chọn người hôm nay đã nhận ít việc hơn, rồi xoay vòng.
4. Ghi lý do đề xuất lên ticket.

**Ví dụ.** _"Máy 15 phòng 1304 không lên nguồn"_ → HARDWARE, ET 60 phút (bảng mặc định).

| Người | Đang giữ | Tải |
| --- | --- | --- |
| Anh A | Đang làm còn 20 phút + chưa bắt đầu 15 phút | **35** |
| Anh B | Đang làm còn 60 phút + chờ linh kiện 60 × 0,5 | **90** |
| Chị C | Nghỉ | — |

→ Đề xuất **anh A**: _"A còn ~35 phút việc, B ~90 phút"_. Điều phối viên duyệt. Sau đó A thấy phải thay nguồn, xin sửa ET thành 120 phút; điều phối viên đồng ý.

### R3. Ai đang trực

**Đang trực** = có ca trong lịch trực (Sáng/Chiều) và không có đơn nghỉ đã duyệt cho ca đó, và không bị tắt tay.
_(Cách lập lịch và duyệt đơn là nghiệp vụ riêng; ở đây chỉ dùng kết quả.)_

**Giai đoạn 1 – chưa có lịch: nút bật/tắt**
- Mỗi người xử lý có trạng thái **Đang trực / Nghỉ**, lưu theo **người × workspace** trong DB của khối H (không lưu ở Keycloak attribute: đổi liên tục, token giữ giá trị cũ).
- Kỹ thuật viên tự bật/tắt của mình; điều phối viên bật/tắt cho người trong workspace. Mỗi lần đổi ghi lịch sử (ai, lúc nào).
- Mọi luật chỉ gọi **một hàm** `isOnDuty(người, workspace, thời điểm)`. Giai đoạn 1 hàm này đọc nút bật/tắt.
- Tắt trực → coi như **người được duyệt nghỉ** ở dưới (việc chưa bắt đầu được chọn người lại, việc đang làm báo điều phối viên).

**Giai đoạn 2 – có lịch trực:** chỉ đổi phần bên trong `isOnDuty` (tính từ lịch + đơn nghỉ). Nút bật/tắt giữ lại làm **ghi đè tạm thời** (vắng đột xuất trong ca). R2, R4 và API không đổi.

- Chỉ người đang trực mới được giao việc mới.
- **Tổ không ai trực** → điều phối viên giao tạm người tổ khác hoặc để chờ ca sau.
- **Người được duyệt nghỉ** → ticket chưa bắt đầu của họ được chọn người lại; ticket đang làm do điều phối viên quyết.
- **ET dài hơn thời gian ca còn lại** của người được đề xuất → điều phối viên quyết khi duyệt.
- **Hết ca chưa xong**: kỹ thuật viên làm thêm giờ cho xong, **hoặc** bàn giao cho người khác (ghi rõ đã làm đến đâu), điều phối viên duyệt.
- Giờ tính SLA = giờ các ca, trừ CRITICAL đếm liên tục.

### R4. Duyệt và hàng đợi

- Ticket chỉ vào hàng đợi của kỹ thuật viên **sau khi điều phối viên duyệt** (ET + người xử lý). Chưa duyệt thì nằm ở hàng chờ điều phối viên.
- Thứ tự trong hàng đợi:
  1. Ticket **Đang làm** luôn đứng đầu, không bị đẩy xuống.
  2. Còn lại sắp theo: **ưu tiên** (kể cả bậc được nâng do chờ lâu, R5) → **hạn SLA** gần trước → **tạo** trước.
- Ticket gấp vào sau được chèn đúng vị trí: sau việc đang làm, trước việc kém ưu tiên hơn.
- Kỹ thuật viên không có việc Đang làm: ticket đầu hàng được gợi ý "Làm ngay".
- **Dự kiến bắt đầu** = bây giờ + phần còn lại của việc đang làm + ET các ticket xếp trước (tính trong giờ ca). Báo cho người dùng.

### R5. Hạn xử lý (SLA)

**SLA = hạn chót** cho mỗi ticket, tính theo mức ưu tiên:

| Ưu tiên | Phải **bắt đầu** trước _(cấu hình)_ | Phải **xong** trước _(cấu hình)_ |
| --- | --- | --- |
| CRITICAL | 15 phút | 2 giờ |
| HIGH | 1 giờ | 8 giờ |
| MEDIUM | 4 giờ | 2 ngày |
| LOW | 1 ngày | 5 ngày |

_Ví dụ: ticket MEDIUM gửi lúc 8:00 → phải có người bắt đầu làm trước 12:00, và xong trước 8:00 hai ngày sau._

**Hạn dùng để làm gì**
- Xếp hàng: cùng mức ưu tiên thì ticket **gần hạn hơn** làm trước (R4).
- Nhắc nhở: sắp hết hạn hoặc quá hạn thì cảnh báo (R8).
- Báo người dùng: _"Yêu cầu sẽ được xử lý trước 12:00"_.
- Đo chất lượng: bao nhiêu % ticket xong đúng hạn.

**Chống bị bỏ quên**
Ticket chờ đã **quá nửa** _(cấu hình)_ thời gian "phải bắt đầu" mà chưa ai làm thì tự **lên 1 bậc ưu tiên** (ví dụ LOW → MEDIUM), để không bị các ticket mới chen mãi. Tối đa lên HIGH.

**Không tính vào hạn**
- Thời gian ngoài ca trực (R3), trừ CRITICAL.
- Thời gian **chờ người dùng trả lời**: lỗi do người dùng chậm, không phải kỹ thuật viên.

### R6. Trạng thái

| Trạng thái | Nghĩa | Tính vào tải |
| --- | --- | --- |
| Chờ duyệt | Đã có đề xuất, chờ điều phối viên | Không |
| Đã giao – chưa bắt đầu | Trong hàng đợi, chưa làm | Có |
| **Đang làm** (In Progress) | Đang xử lý | Có |
| **Đang chờ** (Pending) | **Đã làm dở**, tạm dừng vì chờ người dùng / linh kiện / bên ngoài (bắt buộc ghi lý do) | × 0,5 |
| Chờ xác nhận | Kỹ thuật viên báo xong | Không |
| Mở lại | Người dùng báo "Chưa xong" | Có |
| Đã đóng | Hoàn tất | Không |

- Mỗi người tối đa **1 ticket Đang làm** _(cấu hình)_.
- Hệ thống **không bao giờ tự** đổi trạng thái ticket Đang làm.
- **Trả việc** (sai tổ…) phải ghi lý do → quay lại điều phối viên.

### R7. Ticket khẩn cấp (CRITICAL)

Ticket CRITICAL (ví dụ _"Phòng thi 1304 mất mạng, thi sau 15 phút"_) chỉ có 15 phút để bắt đầu, nên được đi **đường ưu tiên**:

1. **Báo ngay** cho kỹ thuật viên được đề xuất và điều phối viên (thông báo nổi bật, không chờ họ tự mở hàng đợi).
2. **Không tự bắt bỏ dở việc khác.** Kỹ thuật viên đang làm việc khác thì **tự quyết** có tạm dừng việc đó (chuyển sang Đang chờ) để làm CRITICAL trước hay không, vì chỉ họ biết việc hiện tại có dừng được không.
3. **Duyệt**: vẫn phải điều phối viên duyệt như R4, nhưng quá **5 phút** _(cấu hình)_ chưa ai duyệt thì tự vào hàng đợi của người được đề xuất.

### R8. Cảnh báo và giao lại

| Sự kiện _(ngưỡng: cấu hình)_ | Xử lý |
| --- | --- |
| Quá hạn "phải bắt đầu" mà chưa bắt đầu | Báo điều phối viên để giao lại |
| Còn 20% thời gian SLA | Cảnh báo người xử lý và điều phối viên |
| Quá SLA | Đánh dấu quá hạn, đưa lên đầu danh sách điều phối viên |
| Đang chờ quá 3 ngày | Nhắc kỹ thuật viên |

### R9. Con người sửa kết quả AI

- Kỹ thuật viên và điều phối viên sửa được loại lỗi và ưu tiên; chỉ điều phối viên đổi người xử lý.
- Đổi loại lỗi dẫn tới đổi tổ → đề xuất người lại (R2), điều phối viên duyệt.
- Mỗi lần sửa đều lưu: giá trị AI, giá trị mới, ai sửa, lý do.

### R10. Ticket trùng và sự cố chung

> **Chờ chốt.** AI hiện không trích được phòng / thiết bị, mà luật dưới cần hai thông tin này. Hai hướng:
> 1. **Form gửi ticket có ô chọn** tòa / phòng / thiết bị → backend so khớp, chính xác, không cần AI (cần đổi R0).
> 2. Chờ nhóm AI trích xuất `location`, `device` (giai đoạn sau).
>
> Khi chưa chắc dữ liệu đúng: chỉ **gợi ý "có thể trùng"** cho điều phối viên quyết, không tự gộp. Phần dưới là luật đích.

**Trùng** = đủ cả 4 điều kiện → **tự gộp**:

| Điều kiện | Trùng | Không trùng |
| --- | --- | --- |
| Cùng phòng | 1304 – 1304 | 1304 – 1301 |
| Cùng đối tượng | máy chiếu – máy chiếu; máy 15 – máy 15 | máy 15 – máy 3 |
| Cùng loại lỗi | HARDWARE – HARDWARE | HARDWARE – NETWORK |
| Ticket trước còn mở | đang xử lý | đã đóng (lỗi tái diễn → ticket mới) |

- Người gửi sau thấy: _"Đã có người báo lúc 8:00, đang xử lý. Bạn sẽ được báo khi xong."_ kèm nút **Tách riêng** nếu gộp nhầm.
- Ticket gộp không vào hàng đợi, không tính tải. Ticket gốc đóng → ticket gộp đóng theo, báo người gửi.

**Sự cố chung** (nhiều phòng, chung nguyên nhân, `incidentSignal`): **không tự gộp**. Báo điều phối viên; điều phối viên quyết có gom thành một sự cố hay không.

### R11. Người dùng xác nhận

Agent **Báo xong** → **Chờ xác nhận** (agent rảnh tay nhận việc khác ngay).

| Người dùng | Kết quả |
| --- | --- |
| Bấm "Đã ổn", hoặc không phản hồi sau 3 ngày _(cấu hình)_ | **Đã đóng** |
| Bấm "Chưa xong" | **Mở lại**: báo agent và điều phối viên; ticket lên đầu hàng đợi của agent (sau việc đang làm) |

Ticket Mở lại:
- Đi tiếp trên **quy trình cũ** của ticket, không tạo ticket mới.
- Agent bấm Bắt đầu → Đang làm → Báo xong → lại Chờ xác nhận.
- Quá 15 phút _(cấu hình)_ agent chưa nhận lại → cảnh báo điều phối viên.
- Agent không trực → báo điều phối viên ngay để giao người khác.
- Mở lại từ lần 2 → điều phối viên được đánh dấu để cân nhắc đổi người.

### R12. Thống kê

- Đếm theo **phạm vi quyền**: `requester` thấy ticket của mình; `agent`, `coordinator` thấy cả workspace.
- Lọc theo **thời điểm tạo ticket**, không phải thời điểm xong. `from` gồm mốc đầu, `to` không gồm mốc cuối.
- Trạng thái lấy từ quy trình (Flowable), không có cột trạng thái riêng.
- Các nhóm hiện có: `awaitingHandling`, `awaitingConfirmation`, `completed`, và `unclassified` (dữ liệu không khớp trạng thái chuẩn, cần kiểm tra). Khi thêm trạng thái R6 thì tách thêm nhóm: chờ duyệt, đang làm, đang chờ, mở lại, quá hạn SLA.

---

## 4. Bảng ưu tiên = ảnh hưởng × độ gấp

_(đang dùng trong `priority-policy.yaml`)_

| | U1 có cách tạm | U2 chờ được | U3 bị chặn | U4 rất gấp |
| --- | --- | --- | --- | --- |
| **I1** một máy | LOW | LOW | MEDIUM | HIGH |
| **I2** vài máy | LOW | MEDIUM | HIGH | HIGH |
| **I3** cả phòng | MEDIUM | HIGH | HIGH | CRITICAL |
| **I4** nhiều phòng | HIGH | HIGH | CRITICAL | CRITICAL |

---

## 5. Dữ liệu cần lưu

- Kết quả AI mỗi ticket (kèm phiên bản) và mọi lần con người sửa (loại, ưu tiên, ET).
- Mốc thời gian: tạo, duyệt, bắt đầu, từng lần Đang chờ (lý do), báo xong, đóng.
- Thời gian làm thực tế (tổng Đang làm) → dữ liệu để backend tính trung vị ET (R2).
- Nguồn ET đã dùng (`estimateBasis`) và `effortSize` của AI nếu có, để đo độ sát giữa ET và thời gian thực tế.
- Lý do đề xuất người và các lần giao lại.

---

## 6. Cấu hình (admin hệ thống chỉnh được)

**Admin hệ thống = `superadmin`** (tài khoản có quyền `platform-admin`). Chỉ người này mở được màn hình cấu hình.

**Một bộ cấu hình dùng chung cho mọi workspace.** Workspace `lab-a`, `lab-b` trong dự án chỉ là ví dụ; đơn vị triển khai tự khai báo workspace của mình. Sẽ có tài liệu hướng dẫn admin cấu hình hệ thống (workspace, tổ, thành viên, các giá trị dưới đây).

Các con số dưới đây là **giá trị mặc định**. Admin chỉnh trong màn hình cấu hình, có hiệu lực cho ticket mới; mỗi lần đổi đều ghi nhật ký.

| Nhóm | Cấu hình | Mặc định | Luật |
| --- | --- | --- | --- |
| **Tổ** | Loại lỗi → tổ | bảng R1 | R1 |
| **Sự cố chung** | Số ticket / khoảng thời gian | 3 ticket / 30 phút | R10 |
| **ET** | Bảng ET mặc định theo loại × ưu tiên | bảng R2 | R2 |
| | Số ticket tối thiểu để dùng trung vị | 20 | R2 |
| | Khoảng thời gian lấy mẫu | 6 tháng gần nhất | R2 |
| | Ngưỡng bỏ giá trị bất thường | < 2 phút hoặc > 8 giờ | R2 |
| **Chọn người** | Hệ số tính tải của ticket Đang chờ | 0,5 | R2 |
| | Ngưỡng "coi như bằng nhau" | 15 phút | R2 |
| | Tối đa ticket Đang làm mỗi người | 1 | R6 |
| | Phần còn lại tối thiểu của việc Đang làm | 10 phút | R2 |
| **Ca trực** | Giờ ca Sáng / Chiều | 7:00–11:30 / 13:00–17:00 | R3 |
| **Hạn xử lý** | Bảng "phải bắt đầu / phải xong" theo ưu tiên | bảng R5 | R5 |
| | Mức chờ để tự nâng 1 bậc ưu tiên | 50% hạn bắt đầu | R5 |
| | Chờ linh kiện / bên ngoài có tính vào hạn không | Có | R5 |
| **CRITICAL** | Chưa ai duyệt sau bao lâu thì tự vào hàng đợi | 5 phút | R7 |
| **Cảnh báo** | Cảnh báo khi còn lại | 20% thời gian | R8 |
| | Nhắc ticket Đang chờ lâu | 3 ngày | R8 |
| **Người dùng** | Tự đóng nếu không xác nhận sau | 3 ngày | R11 |
| | Cảnh báo nếu ticket Mở lại chưa được nhận sau | 15 phút | R11 |

---

## 7. Chạy trên Open-Core

Trạng thái ticket do **Workflow Engine (Flowable) của Tầng 1** quản lý qua file BPMN của app, **không** lưu bằng cột `status` riêng.

### 7.1 Trạng thái ↔ BPMN `service-desk`

| Trạng thái (R6) | Trong BPMN | Hiện có? |
| --- | --- | --- |
| Chờ duyệt | Bước `review` của coordinator, trước `handle` | Thêm |
| Đã giao – chưa bắt đầu | Bước `handle` đã giao người (API `assign` có sẵn) | Có |
| Đang làm / Đang chờ | Trạng thái con của bước `handle` (API mới, mục 7.2) | Thêm |
| Chờ xác nhận | Bước `confirm` | Có |
| Mở lại | Kết quả `rework` quay về `handle` | Có |
| Đã đóng | Kết thúc quy trình | Có |
| Tự đóng, cảnh báo, CRITICAL tự vào hàng đợi | Timer của BPMN (Flowable có sẵn) | Thêm |
| Lịch sử, người làm, thời gian | Timeline của Core | Có |

### 7.2 API chung cần thêm vào Core

Chỉ thêm năng lực **dùng chung cho mọi quy trình**, không có chữ "ticket". FE gọi qua HPDI (mục 8), không gọi thẳng Core.

| API | Dùng cho |
| --- | --- |
| Đặt **ưu tiên** và **hạn** cho một việc (`priority`, `dueDate` có sẵn trong Flowable); inbox trả về hai trường này | Hàng đợi, SLA (R4, R5) |
| **Bắt đầu / Tạm dừng (kèm lý do) / Tiếp tục** một việc, có ghi lịch sử | Đang làm, Đang chờ (R6) |

### 7.3 Luật nào thuộc tầng nào

Mỗi luật thuộc **đúng một tầng**: tầng nào **chạy** luật đó thì luật thuộc tầng đó. Tầng 1 là nền cho tất cả (Keycloak, Flowable, API chung ở 7.2).

**Cách xét:** thay Service Desk bằng app "đặt phòng". Luật vẫn dùng nguyên → **Tầng 2**. Phải viết lại → **Tầng 3**.

| Luật | Tầng | Vì sao |
| --- | --- | --- |
| R1 Chọn tổ | **3** | Loại lỗi → tổ chỉ có ở hỗ trợ CNTT |
| R2 Chọn người, tính ET | **2** | Đặt phòng cũng chọn người duyệt ít việc nhất và cần thời gian duyệt ước tính. Service Desk chỉ đưa vào tổ, bảng ET mặc định và `effortSize` |
| R3 Ai đang trực | **2** | Người duyệt phòng cũng có ca trực |
| R4 Duyệt, hàng đợi | **2** | Yêu cầu đặt phòng cũng cần duyệt và xếp hàng |
| R5 Hạn xử lý | **2** | Đặt phòng cũng có hạn duyệt. Con số hạn nằm ở cấu hình (mục 6) |
| R6 Trạng thái | **2** | Đang làm / Đang chờ dùng cho mọi việc. Service Desk chỉ vẽ BPMN của mình |
| R7 CRITICAL | **2** | Báo ngay, tự vào hàng đợi áp dụng cho mọi việc khẩn cấp |
| R8 Cảnh báo | **2** | Nhắc hạn áp dụng cho mọi việc |
| R9 Người sửa AI | **2** | Ghi nhận người sửa gợi ý AI dùng cho mọi app có AI (khối I) |
| R10 Trùng ticket | **3** | Trùng phòng, trùng thiết bị chỉ có ở hỗ trợ CNTT |
| R0 Gửi ticket | **3** | Trường và luật nhập là của ticket |
| R11 Người dùng xác nhận | **3** | Bước xác nhận, Mở lại là quy trình riêng của Service Desk |
| R12 Thống kê | **3** | Nhóm trạng thái theo BPMN của Service Desk |
| Mục 2 – AI trả về | **3** | Loại lỗi, ảnh hưởng, độ gấp là profile riêng của Service Desk |
| Mục 4 – Bảng ưu tiên | **3** | Bảng ảnh hưởng × độ gấp của Service Desk |
| Mục 6 – Cấu hình | **2** | Màn hình cấu hình dùng chung cho mọi app |

**Tóm tắt**

| Tầng | Gồm |
| --- | --- |
| **Tầng 2 – H-P-D-I** | R2, R3, R4, R5, R6, R7, R8, R9, mục 6 |
| **Tầng 3 – Service Desk** | R0, R1, R10, R11, R12, mục 2, mục 4 |

Luật Tầng 2 **nhận đầu vào** từ Tầng 3 (tổ, ET, mức ưu tiên) nhưng **không biết** đó là ticket.

---

## 8. Hợp đồng API và phân việc

Hợp đồng đích (chưa cài đặt) để BE và FE làm song song. Cài xong endpoint nào thì chuyển sang file đang chạy trong cùng PR (quy ước ở `contracts/README.md`).

| File | Tầng | Gồm |
| --- | --- | --- |
| `contracts/hpdi/openapi-target.yaml` | 2 | Hàng đợi, Bắt đầu / Tạm dừng / Tiếp tục / Trả việc, đề xuất người, xin sửa ET, ca trực, cảnh báo, cấu hình chung |
| `contracts/openapi/servicedesk-target.yaml` | 3 | Ticket mở rộng (trạng thái, phân loại, `assessment`, ET, SLA), duyệt, sửa phân loại, thống kê mở rộng, cấu hình riêng |

### 8.1 Luật ↔ API

| Luật | API |
| --- | --- |
| R0 Gửi ticket | `POST /tickets` (đã có, không đổi) |
| Mục 2 AI | Backend gọi nền, FE đọc `assessment` trong `GET /tickets/{id}`; hướng dẫn: `POST /tickets/{id}/assistance` (đã có) |
| R2 Đề xuất người | `GET /tasks/{id}/assignment-suggestion` |
| R2 Sửa ET | `POST /tasks/{id}/estimate-requests`, `GET /estimate-requests`, `POST /estimate-requests/{id}/decision` |
| R3 Ca trực | `GET /duty`, `PUT /duty/{subject}` |
| R4 Hàng chờ duyệt | `GET /tickets?state=AWAITING_REVIEW&sort=priority` |
| R4 Duyệt | `POST /tickets/{id}/review` (loại lỗi, ưu tiên, ET, người xử lý trong một lần gọi) |
| R4 Hàng đợi kỹ thuật viên | `GET /queue` |
| R5 SLA | Trường `sla` trong ticket và hàng đợi |
| R6 Trạng thái | `POST /tasks/{id}/start`, `pause`, `resume`, `release`; Báo xong dùng `complete` (đã có) |
| R7, R8 Cảnh báo | `GET /alerts?since=` (FE gọi định kỳ), `POST /alerts/{id}/read` |
| R9 Sửa phân loại | `PATCH /tickets/{id}/classification`; lịch sử sửa ở `corrections` |
| R11 Xác nhận | `complete` với `accepted` / `rework` (đã có); tự đóng do timer BPMN |
| R12 Thống kê | `GET /tickets/statistics` mở rộng |
| Mục 6 Cấu hình | `GET/PUT /api/hpdi/v1/settings/work`, `GET/PUT /settings` của Service Desk |
| R10 Trùng | Chưa có, chờ chốt |

### 8.2 Ai làm gì

| Nhóm | Việc |
| --- | --- |
| **BE** | Bước `review` + timer trong BPMN; API Core (7.2); các endpoint ở hai file đích; gọi AI nền và lưu `assessment`; tính ET, tải, SLA, cảnh báo |
| **AI** | Giữ ổn định triage / AI07; merge AI07; giai đoạn sau: `effortSize`, đánh giá lại sau câu trả lời bổ sung, (nếu chọn) trích phòng / thiết bị |
| **FE** | Làm theo hợp đồng đích với dữ liệu giả, nối API thật khi BE merge |

### 8.3 Thứ tự đề xuất

| Bước | BE | FE làm song song |
| --- | --- | --- |
| 1 | R6 (Bắt đầu / Tạm dừng / Tiếp tục) + hàng đợi | Form gửi ticket (R0), xác nhận (R11), component trạng thái / ưu tiên / SLA / ET, hiển thị `assessment` |
| 2 | R4 duyệt + gọi AI nền | Hàng đợi kỹ thuật viên, các nút R6 |
| 3 | R2 đề xuất người + ET, R3 bật/tắt trực | Hàng chờ duyệt + màn duyệt, nút trực, xin sửa ET |
| 4 | R5 SLA, R7, R8 cảnh báo, R11 tự đóng | Cảnh báo (polling + toast) |
| 5 | Mục 6 cấu hình, R12 thống kê mở rộng | Màn cấu hình, thống kê |
