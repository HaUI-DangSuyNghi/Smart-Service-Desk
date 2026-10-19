# Hợp đồng API theo tầng

Có hai loại hợp đồng, phải luôn khớp nhau:

| Loại | Ở đâu | Ai dùng |
|---|---|---|
| REST (HTTP) | `contracts/openapi/` | Frontend, script `smoke.ps1`, app bên ngoài |
| Java Core | `backend/.../core/<module>/api/` | HPDI implementation, Core integration |
| Java HPDI | `backend/.../hpdi/<capability>/api/` | App Tầng 3 |

HPDI REST nằm tại [hpdi/openapi.yaml](hpdi/openapi.yaml). Service Desk giữ
[openapi/servicedesk.yaml](openapi/servicedesk.yaml); Room Request sở hữu
[apps/roomrequest/openapi.yaml](apps/roomrequest/openapi.yaml). App gọi HPDI public
API và sở hữu REST projection; không import Core DTO hoặc adapter nội bộ.

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
| Tiền tố | `/api/core/v1` (T1), `/api/hpdi/v1` (T2), `/api/apps/<app>/v1` (T3) |
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

Phía Core dùng `CoreException`; phía app dùng `hpdi.common.api.CapabilityException`. Error advice của từng tầng giữ cùng hợp đồng Problem Details.

## Đổi hợp đồng

- Đổi interface trong `*.api` hoặc đổi request/response đã có → **báo cả nhóm** trước, vì ảnh hưởng mọi nơi đang gọi.
- Sửa phần cài đặt (`internal`) không cần báo.
