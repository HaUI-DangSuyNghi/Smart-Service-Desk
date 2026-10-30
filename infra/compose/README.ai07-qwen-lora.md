# Runtime AI07 Qwen LoRA

Provider tùy chọn này mở rộng ADR-011 và contract phân loại hiện có. Tev1/Gemma,
API assess cũ, rule guard và processing gate Java giữ nguyên vai trò. Image chỉ chứa
code/thư viện. Base, adapter và tokenizer riêng được mount read-only, không vào Git,
image hoặc wheel HDPI dùng chung.

## Chuẩn bị artifact

Dùng Python 3.12 và bộ công cụ build đã khóa của repo. Chạy từ thư mục gốc. Mỗi lần
thử dùng một đường dẫn `.local/ai07` mới; công cụ từ chối ghi đè bằng chứng cũ.

```powershell
python scripts/apps/servicedesk/ai07/prepare-qwen-base.py --revision 23c69c53358a07516b5827588b3fdb12ae78fd65 --directory .local/ai07/qwen-base-new
python scripts/apps/servicedesk/ai07/build-qwen-image.py --output .local/ai07/qwen-new/image.json
```

Đọc `imageId` trong receipt build. Dùng ID `sha256:...` đã inspect, không dùng tag có
thể thay đổi. Đóng băng bundle mới cho từng image và môi trường thư viện:

```powershell
python scripts/apps/servicedesk/ai07/verify-qwen-artifact.py --source .local/ai07_qwen35_silver_lora_results --stage .local/ai07/model-registry/qwen-new --base-directory .local/ai07/qwen-base-new --base-revision 23c69c53358a07516b5827588b3fdb12ae78fd65 --runtime-identity <image-id-da-inspect> --dependencies-lock intelligence/requirements-qwen-linux.lock --output .local/ai07/qwen-new/artifact.json
```

`--source` cũng nhận ZIP gốc. Công cụ kiểm đủ 7 file adapter đã pin, rank/alpha/base,
192 tensor safetensors và offsets, base receipt/revision, tokenizer/template, lock thư
viện và digest code thực thi. Không chạy Python từ checkpoint, không load pickle.
Stage thất bại không có identity hợp lệ; giữ riêng và dùng đường dẫn mới để thử lại.

Identity model là SHA-256 của `ai07-qwen-serving-v1\0` nối với JSON manifest canonical
(`sort_keys=True`, ASCII, separators gọn). Mỗi file được hash theo byte thật;
`filesDigest` hash map tên-file → SHA canonical. Digest prompt hash lần lượt prefix
`ai07-qwen-policy-v1\0`, byte JSON prompt, NUL, byte JSON mapping, NUL và byte source
`qwen_policy.py`. Digest rule guard độc lập. SHA adapter không phải SHA model bundle.
Runtime kiểm phiên bản thư viện và source trước khi load. ID image do operator inspect;
container không tự chứng thực được Docker ID của chính nó.

Artifact Kaggle không ghi revision base lúc train. Snapshot tải về là **base phục vụ
được dựng lại**, không được nhận là revision train gốc. Prompt giữ đúng spacing/order/
Unicode của Kaggle: greedy, seed 42, tối đa 4 token mới, không truncate âm thầm.
Transformers/PEFT FP16 có thể khác Unsloth; phải đo parity, không suy đoán là giống.

## Replay riêng

Xem `replay-qwen-checkpoint.py --help`. Công cụ yêu cầu bản reference và predictions
Kaggle đã pin, đối chiếu đúng 185 DEV với importer LabOps v4 hiện có. Label, ID và
family không đi vào context model. Từng attempt được flush vào journal mới; lỗi vẫn
nằm trong mẫu số. Báo cáo chỉ là độ đồng thuận machine-silver, không phải Gold accuracy.
GPU receipt ghi phần cứng, cold load/warmup, latency warm và VRAM allocated/reserved.
Chữ ký độc lập và quyền xử lý dữ liệu vẫn là điều kiện bên ngoài.

Provider có giới hạn inference 8 giây trong deadline HTTP Java 10 giây (GPU laptop rảnh hạ xung, lần đầu có thể quá 3 giây). Replay
offline dùng bound nghiên cứu 120 giây để đo case chậm. Model chỉ ready sau load/warmup.
Vượt bound phục vụ sẽ dừng worker và hạ readiness; cần restart. Không retry, tự chuyển
CPU/cloud hoặc tự gán OTHER khi lỗi.

## Bộ Docker cho thành viên

```powershell
python scripts/apps/servicedesk/ai07/package-qwen-team.py --bundle .local/ai07/model-registry/qwen-new --model-digest <model-digest-da-kiem> --image-receipt .local/ai07/qwen-new/image.json --directory .local/ai07/qwen-new/team-package
```

Bộ xuất gồm model bundle đã kiểm, policy ứng dụng tối thiểu, Compose, các script
`Start-Qwen.ps1`, `Test-Qwen.ps1`, `Stop-Qwen.ps1`, image TAR nén `qwen-image.tar.gz`
và toàn văn LICENSE/NOTICE của base/thư viện trong đúng image. Chia sẻ qua kênh được chủ sở hữu
cho phép, kèm checksum `package.json` riêng. Script không publish/upload. Bộ test chỉ
gọi HTTP loopback qua `docker exec`, không publish cổng host, và tạo secret mới tại
máy nhận. README trong bộ test có lệnh chạy.
Trên Linux, đặt cùng các biến `AI07_QWEN_*` rồi chạy Compose; UID 10001 cần quyền đọc
các file mount, còn secret chỉ cấp quyền cho tài khoản dịch vụ chuyên dụng.

## Nối backend đã được phép xử lý

Compose mặc định không publish cổng, dùng mạng internal. Gắn backend được ủy quyền vào
mạng đó rồi cấu hình bằng các setting sẵn có:

| Setting | Giá trị |
| --- | --- |
| `INTELLIGENCE_CLASSIFICATION_BASE_URL` | `http://ai07-qwen:8003` trong mạng riêng |
| `INTELLIGENCE_CLASSIFICATION_TOKEN` | Secret riêng của provider |
| `SERVICE_DESK_CLASSIFIER_PROVIDER` | `qwen35-08b-lora-labops-silver-r1` |
| `SERVICE_DESK_CLASSIFIER_MODEL_DIGEST` | SHA bundle phục vụ đã kiểm |
| `SERVICE_DESK_CLASSIFIER_POLICY_DIGEST` | SHA prompt/mapping đã kiểm |
| `SERVICE_DESK_CLASSIFIER_MODE` | SHADOW hoặc ASSISTED sau phê duyệt bên ngoài |
| `SERVICE_DESK_CLASSIFIER_PROCESSING_RECEIPT` | File receipt do issuer bên ngoài cấp |
| `SERVICE_DESK_CLASSIFIER_PROCESSING_VERIFICATION_KEY` | File khóa xác minh của issuer |

Mặc định repo vẫn DISABLED. Grant phải khớp workspace/model/policy/expiry; actor,
snapshot ticket và grant được kiểm lại sau inference. Thu hồi grant sẽ chặn cả yêu cầu
mới lẫn kết quả đang chạy khi tái kiểm tra. Không dùng khóa lab để cấp quyền xử lý
ticket thật. Quyền dataset, cloud processor, Gold và release là các phê duyệt riêng.

Rollback: đặt DISABLED, thu hồi processing grant rồi chạy script Stop hoặc Compose down
mà không xóa model volume. Giữ receipt/digest cũ. Provider không ghi ticket, routing,
assignment hoặc workflow. PROMOTED bị chặn; chưa chọn champion.

Tài liệu gốc: [Transformers Qwen3.5](https://huggingface.co/docs/transformers/model_doc/qwen3_5),
[định dạng checkpoint PEFT](https://huggingface.co/docs/peft/developer_guides/checkpoint).
