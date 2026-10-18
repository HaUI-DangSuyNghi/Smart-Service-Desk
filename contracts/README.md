# Hợp đồng API của Core

Có hai loại hợp đồng, phải luôn khớp nhau:

| Loại | Ở đâu | Ai dùng |
|---|---|---|
| REST (HTTP) | `contracts/openapi/` | Frontend, script `smoke.ps1`, app bên ngoài |
| Java | `backend/.../core/<module>/api/` | Module backend gọi nhau, app Tầng 3 |

## File trong `openapi/`

| File | Nội dung |
|---|---|
| `core.yaml` | **Chỉ endpoint đã chạy thật.** Kèm schema dùng chung: `Problem`, `PageMeta`, tham số `Page`/`Size`. |
| `core-target.yaml` | **Bản đích, chưa cài đặt.** Toàn bộ endpoint theo thiết kế, để FE và BE làm song song. Không gọi thử được. |

Quy trình: cài xong endpoint nào → **chuyển** endpoint đó từ `core-target.yaml` sang `core.yaml` trong cùng PR. Khi `core-target.yaml` hết `paths` thì xóa file.

Kiểm tra cú pháp trước khi mở PR:

```bash
python -m openapi_spec_validator contracts/openapi/core.yaml
python -m openapi_spec_validator contracts/openapi/core-target.yaml
```

## Quy ước REST

| Quy ước | Giá trị |
|---|---|
| Tiền tố | `/api/core/v1` (core), `/api/apps/<app>/v1` (Tầng 3) |
| Workspace | Tài nguyên thuộc workspace nằm dưới `/workspaces/{workspace}/...`. Workspace lấy từ URL, không lấy từ header/body. |
| Xác thực | `Authorization: Bearer <access token>` |
| Định dạng | JSON UTF-8, tên trường `camelCase`; upload dùng `multipart/form-data` |
| Trường lạ trong body | Từ chối 400 |
| Phân trang | `?page=0&size=20`, `size` tối đa 100. Trả `{"items":[...],"page":0,"size":20,"total":123}` |
| Thời gian | ISO-8601 UTC |
| Lỗi | Problem Details (RFC 9457) có thêm `code` và `requestId`, xem schema `Problem` |
| Request ID | Mọi response có header `X-Request-ID`. Client/gateway gửi UUID thì giữ nguyên, không thì server tự sinh. Trùng `requestId` trong body lỗi và trong audit. |
| Phiên bản | Thay đổi phá vỡ tương thích → `v2`. Thêm trường mới không phá vỡ. |

## Mã lỗi

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Body sai định dạng, thiếu trường, có trường lạ |
| 401 | `UNAUTHENTICATED` | Thiếu token, sai chữ ký, hết hạn, sai issuer/audience |
| 403 | `FORBIDDEN` | Không thuộc workspace hoặc thiếu vai trò |
| 404 | `NOT_FOUND` | Không tồn tại, **hoặc** người gọi không được biết nó tồn tại |
| 409 | `CONFLICT` | Trùng business key, task đã bị nhận, sai bước, gửi lặp |
| 413 | `PAYLOAD_TOO_LARGE` | File vượt 5 MiB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Loại file không cho phép |

Phía Java: ném `CoreException` với `ErrorCode` tương ứng, `Errors` tự chuyển thành body lỗi.

## Đổi hợp đồng

- Đổi interface trong `*.api` hoặc đổi request/response đã có → **báo cả nhóm** trước, vì ảnh hưởng mọi nơi đang gọi.
- Sửa phần cài đặt (`internal`) không cần báo.


Three-tier ownership: `openapi/core.yaml` is T1; `hpdi/openapi.yaml`, capability
READMEs and `intelligence/v1` are T2; `openapi/servicedesk.yaml` (stable historical
path) and `apps/roomrequest/openapi.yaml` are T3. Service Desk shared schema refs
resolve to H-P-D-I. See `apps/servicedesk/README.md` for path compatibility.
