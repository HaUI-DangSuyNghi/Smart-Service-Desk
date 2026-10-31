# AI D2 — Chạy model và bàn giao Docker offline

Tài liệu dành cho người build và người nhận kit. Mặc định là `DEMO_SYNTHETIC`;
chưa có quyết định production GO. Không đưa `.env`, grant, khóa, ticket, prediction,
metrics hoặc dataset vào Git hay gói bàn giao.

## 1. Những gì đã kiểm chứng

Base serving được tải từ [unsloth/Qwen3.5-0.8B tại revision cố định](https://huggingface.co/unsloth/Qwen3.5-0.8B/tree/23c69c53358a07516b5827588b3fdb12ae78fd65).
License base: Apache-2.0. Quyền phân phối adapter phải được chủ sở hữu xác nhận riêng.
Revision base lúc train adapter chưa được ghi nhận; không suy diễn revision serving
là revision training.

Image tự chứa đã build và chạy inference thật trên RTX 3050 Laptop 4 GB, không
mount weights/code từ host, mạng `none`. Fixture bàn phím hỏng trả HTTP 200,
`HARDWARE`, `score=null`, không có operational authority. Token sai trả 403;
schema sai trả 502 theo protocol hiện tại. Kết quả này chỉ chứng minh kỹ thuật
cho fixture synthetic, không chứng minh chất lượng Gold hoặc quyền xử lý ticket thật.

Pipeline Java/PostgreSQL/Flowable cũng đã chạy với model GPU thật: một inference,
final result được lưu, GET lặp ổn định, human review và feedback pass. Lệnh tái kiểm:

```powershell
python scripts/ai-d2/prove-real-qwen-intake.py `
  --image-receipt .local/ai07/d2/full-image-hf.json `
  --output .local/ai07/d2/real-java-intake-proof-new.json
```

Harness này dùng JWT/grant test doubles cho fixture synthetic; không thay nghiệm
thu quyền xử lý thật hoặc clean-recipient acceptance.

| Danh tính | Giá trị đã kiểm tra tại máy phát triển |
|---|---|
| Base serving revision | `23c69c53358a07516b5827588b3fdb12ae78fd65` |
| Code Docker image ID | `sha256:7ad420520547ab2a345bfdc355db709ef4513ac7480fac8faece1c966e3cce96` |
| Model bundle SHA-256 | `a40d655f95f8ed6b6dfa89262b9bc1c99c014233e8ca7b23fe29c4f4dee9576d` |
| Policy SHA-256 | `ac07759e7e3cfdcddd5e1259fea78564b270aa46e8a680dc9d0df5ce7b661753` |
| Wrapper Docker image ID | `sha256:b88e7935726c0d7b7d5e716a9b2069f5d3ce8e6e70686b5b590a606830fdc812` |
| OCI registry digest | Chưa publish; không dùng image ID thay registry reference |

Receipt riêng tư: `.local/ai07/d2/full-image-hf.json` và
`.local/ai07/d2/full-image-gpu-smoke.json`. Receipt build ghi đúng trạng thái source
dirty tại thời điểm build. Chưa có gói offline được cấp phép phân phối, chưa chạy
nghiệm thu trên máy nhận sạch. Không gọi các bước này là PASS khi chưa thực hiện.

## 2. Tạo và kiểm tra image tự chứa

Tool download/verify hiện có: `scripts/apps/servicedesk/ai07/prepare-qwen-base.py`
và `verify-qwen-serving-artifact.py`; dùng `--help` để lấy đúng tham số.
Chỉ staging `adapter/` đã được allowlist, không copy cả thư mục kết quả training.
Bundle serving phải có receipt và checksum đầy đủ trước khi build.

```powershell
python scripts/apps/servicedesk/ai07/build-full-model-image.py `
  --bundle .local/ai07/d2/registry-hf-23c69c5 `
  --model-digest a40d655f95f8ed6b6dfa89262b9bc1c99c014233e8ca7b23fe29c4f4dee9576d `
  --code-image sha256:7ad420520547ab2a345bfdc355db709ef4513ac7480fac8faece1c966e3cce96 `
  --expected-code-image-id sha256:7ad420520547ab2a345bfdc355db709ef4513ac7480fac8faece1c966e3cce96 `
  --output .local/ai07/d2/full-image-new.json

python scripts/apps/servicedesk/ai07/verify-full-model-image.py --help
python scripts/apps/servicedesk/ai07/smoke-full-model-image.py `
  --image-receipt .local/ai07/d2/full-image-new.json `
  --output .local/ai07/d2/full-image-smoke-new.json
```

Các output dùng tên mới để tránh ghi đè bằng chứng cũ. `runtimeIdentity` trong
manifest vẫn là code Docker image ID. Wrapper phải giữ nguyên prefix layers của
code image, OS/architecture và user `10001:10001`. Wrapper thêm base/adapter/
tokenizer/policy; không thay code runtime. Image model không mở host port.

## 3. Điều kiện bàn giao

Người có thẩm quyền cấp một approval JSON và chữ ký RSA/SHA-256 tách rời. Public
key và SHA-256 của manifest bàn giao phải đến qua kênh độc lập đã tin cậy. Tool
không tạo issuer key, không tự ký approval và không tự phát grant.

Approval dùng schema `ai-d2-distribution-approval@1.0.0`, gồm `approved=true`,
`privateChannel=true`, `channel`, `issuer`, `expiresEpochSecond`, `modelDigest`.
`channel` phải là đúng registry/private-offline channel đã được duyệt. Approval
phân phối model không thay thế quyền xử lý dữ liệu người dùng.

Để publish private registry, chạy preflight trước; `--execute` mới push:

```powershell
python scripts/apps/servicedesk/ai07/publish-private-model-image.py --help
```

Tool lưu mapping code Docker image ID ↔ model digest ↔ policy ↔ wrapper Docker image ID ↔ OCI
registry digest. Không dùng mutable tag làm bằng chứng release.

## 4. Export kit đầy đủ ở máy build

Build backend/frontend từ commit đã kiểm tra; image CPU sidecar từ
`intelligence/Dockerfile`, gateway từ `infra/apisix/Dockerfile`. Không dùng image
backend cũ thiếu migrations V16–V19. Ghi Docker image ID sau build vào plan JSON:

```json
{
  "images": {
    "postgres": "sha256:<64-hex>",
    "keycloak": "sha256:<64-hex>",
    "platform": "sha256:<64-hex>",
    "gateway": "sha256:<64-hex>",
    "frontend": "sha256:<64-hex>",
    "intelligence": "sha256:<64-hex>",
    "ai07-qwen": "sha256:<64-hex>"
  }
}
```

Các chuỗi `<64-hex>` phải thay bằng Docker image ID thật từ `docker image inspect`.
Docker 29 dùng containerd có thể trả OCI index digest trong `.Id`, khác config
digest. Exporter lưu riêng `imageId`, `sourceDescriptor`, `configDigest` đọc từ
archive và registry `ociRefs`. Không đổi `runtimeIdentity` của model bundle đã
pin. Sau import, installer ghi mapping sang ID thật trên Docker Engine máy nhận;
Compose dùng ID đã nạp và kiểm alias chưa đổi. Không giả định image ID của mọi
Docker Engine đều là config digest.
Exporter kiểm tra đủ bảy service, cùng Linux/amd64, tự thêm code image cha để
người nhận kiểm ancestry độc lập. Export vào thư mục mới dưới `.local`:

```powershell
python scripts/apps/servicedesk/ai07/team_release.py export `
  --plan .local/ai-d2-release-images.json `
  --image-receipt .local/ai07/d2/full-image-hf.json `
  --approval D:/Approved/distribution.json `
  --approval-signature D:/Approved/distribution.sig `
  --public-key D:/Trusted/release-issuer.pub.pem `
  --channel PRIVATE_OFFLINE_CHANNEL `
  --output .local/ai-d2-offline-kit
```

Exporter lưu `images.tar`, receipt model, inventory/checksum toàn bộ file, cấu
hình Compose/profile/realm template, script lifecycle, notices/license và inventory
dependency lấy từ model image. Người phát hành cần rà soát notice chưa có file
license trong distribution và nghĩa vụ các image hỗ trợ. Copy theo allowlist;
không export `.env`, `.local`, khóa, grant hoặc dữ liệu ticket.

Kết quả export là **EXPORTED_UNSIGNED**. Issuer bên ngoài ký chính xác bytes của
`release.json`, tạo `release.sig`. Chuyển SHA-256 manifest và public key qua kênh
tin cậy riêng. Không bỏ trust key tự sinh vào kit rồi coi đó là nguồn tin cậy.

## 5. Máy nhận offline

Cài trước Python 3.12+, PowerShell, OpenSSL, Docker Engine Linux/amd64 và Compose
v2; GPU NVIDIA cùng driver/container runtime đủ bộ nhớ cho Qwen. Cần dung lượng
cho image archive, Docker layers và database. Không có bước cài pip/npm hoặc tải
model khi khởi động kit. Cổng localhost 5173/8081/9080 cần trống; tool không tự
dừng stack khác hay xóa volume. Kiểm tra chữ ký bằng bản verifier từ nguồn tin cậy.

```powershell
$kit = 'D:/SSD-Team/ai-d2-offline-kit'
$hash = '<SHA-256 release.json nhận độc lập>'
$pub = 'D:/Trusted/release-issuer.pub.pem'

./scripts/ai-d2/Verify-Release.ps1 -BundleRoot $kit -ExpectedManifestSha256 $hash -PublicKey $pub
./scripts/ai-d2/Install-Team.ps1 -BundleRoot $kit -ExpectedManifestSha256 $hash -PublicKey $pub
./scripts/ai-d2/Start-Team.ps1 -BundleRoot $kit -ExpectedManifestSha256 $hash -PublicKey $pub
./scripts/ai-d2/Test-Team.ps1 -BundleRoot $kit -ExpectedManifestSha256 $hash -PublicKey $pub
./scripts/ai-d2/Export-Diagnostics.ps1 -BundleRoot $kit -ExpectedManifestSha256 $hash -PublicKey $pub
./scripts/ai-d2/Stop-Team.ps1 -BundleRoot $kit
```

Install chỉ dùng `docker load`, kiểm signed config mapping và model integrity; sinh
password, token, client secret, realm riêng trên máy nhận. Không tái sử dụng secrets
máy build. Project có tên `ssd-d2-<random>`; secrets/realm/rights nằm trong `.local`
của kit, DB và files ở volume theo project đó. Lần install thứ hai bị từ chối để
bảo toàn credentials. Start chỉ `up --no-build --pull never`; Stop chỉ dừng container
thuộc project đã cài, không xóa dữ liệu, vẫn dùng được sau khi approval hết hạn.

Mặc định synthetic tắt xử lý AI trên ticket. Model vẫn có thể chạy healthcheck;
manual intake → review → agent → requester confirmation hoạt động độc lập.
`Test-Team` tạo ticket fixture cố định qua Keycloak/gateway/backend thật, kiểm
requester không thể release, human review, execution và đóng cùng process. Report
chỉ chứa tên check/trạng thái, không có token/password/nội dung ticket. Test này
ghi dữ liệu synthetic vào DB kit; không chạy trên stack khách hàng.

## 6. Bật AUTHORIZED_ASSISTED

Chỉ dùng khi issuer độc lập đã cấp quyền xử lý dữ liệu, đúng scope và policy pin.
Kit hiện hỗ trợ workspace `lab-a` theo realm template được kiểm tra; mở rộng
workspace/identity cần review deployment riêng.

Rights directory do issuer cung cấp gồm `classifier.key`, `context.key`,
`classifier.json`, `context/lab-a.OWN_TICKET.json`,
`context/lab-a.WORKSPACE_REVIEW.json`. Hai grant context phải bind cả readiness và
contextual policy; grant classifier phải bind assessment policy. File khóa là
ASCII ít nhất 32 ký tự, không chứa khoảng trắng; dùng Docker secret/file mount,
không đưa giá trị vào Compose environment.

Issuer ký thêm `ai-d2-assisted-activation@1.0.0`: `approved=true`,
`realTicketProcessingApproved=true`, `productionApproved=false`,
`releaseManifestSha256`, `workspace`, `expiresEpochSecond`, `modelDigest`,
`policyDigest`, `contextPolicyDigest`, `readinessPolicyDigest`,
`contextualPolicyDigest`, `assessmentPolicyDigest`, và `rightsFiles` mapping từng
đường dẫn trên sang SHA-256 bytes thật. Activation không chứa secret hoặc ticket.

```powershell
./scripts/ai-d2/Configure-Assisted.ps1 -BundleRoot $kit `
  -ExpectedManifestSha256 $hash -PublicKey $pub `
  -Activation D:/Approved/activation.json `
  -ActivationSignature D:/Approved/activation.sig `
  -RightsDirectory D:/Approved/recipient-rights
```

Tool kiểm chữ ký, checksum, HMAC, scope, digest và expiry rồi copy đúng allowlist
vào `.local/rights`. Start/Test kiểm lại activation. Backend vẫn tự kiểm current
grant và quyền đọc trước/sau xử lý. Không bật auto merge hoặc PROMOTED. Grant
thiếu/thu hồi/hết hạn hoặc model lỗi phải về manual; không tự sửa grant để qua gate.
`Test-Team` ở assisted mode yêu cầu có đề xuất cuối thật đã lưu và GET lặp ổn định.

## 7. Vận hành và giới hạn nghiệm thu

Frontend `/servicedesk/intake-pending` dành cho coordinator. Ticket mới chờ tại
intake; coordinator có thể chuyển duyệt thủ công kể cả model không sẵn sàng. Sau
final assessment, coordinator disposition chuyển intake sang review; worker không
giả danh coordinator. Giao diện GET chỉ đọc kết quả đã lưu, không gọi model khi
poll hoặc reload. Bổ sung sau review phát cảnh báo; không tự đổi assignment.

Canonical human mặc định tắt. Khi policy được phê duyệt và cấu hình riêng, phải
xác minh category đã được người duyệt, cùng phòng/cùng thiết bị/triệu chứng phù hợp.
Incident khu vực chỉ là tín hiệu; không gộp các máy khác nhau. Split đang mở quay
về intake. Split sau khi follower đã đóng cần quy trình nghiệp vụ riêng.

Diagnostics chỉ giữ trạng thái health, mode, manifest digest và tên kiểm tra.
Không export log thô, `.env`, realm có credentials, key, grant hoặc model input.
Khi test lỗi, dùng health/container logs tại máy nhận theo quyền quản trị, không
gửi nguyên log có dữ liệu nhạy cảm. Không `down -v`, reset realm hay prune để sửa lỗi.

Các gate còn cần bằng chứng bên ngoài: quyền phân phối adapter, issuer approval/
release signature thật, license review, Gold/canonical policy approval và nghiệm
thu ở máy nhận sạch không có model cache/network. R1/R3 routing/duty và gợi ý phân
công đã tích hợp từ develop có PR #52/#53, áp dụng cho cả process intake mới.
Các phần SLA/reporting còn lại thuộc backend nghiệp vụ liên quan; bộ AI D2 không
thay thế toàn bộ R0–R12.

## English quick start

Use a separately trusted public key and manifest SHA-256. Run Verify-Release,
Install-Team, Start-Team, Test-Team, Export-Diagnostics and Stop-Team as shown
above. The kit contains immutable Linux/amd64 images; startup never builds or
pulls images. Recipient secrets are generated locally. DEMO_SYNTHETIC is the
default; AUTHORIZED_ASSISTED requires independently signed activation and current
processing grants. Stop preserves volumes. Distribution approval, license review,
Gold evaluation and clean-recipient acceptance remain separate release gates.


## 8. D2-A + E5 (AI05 dense, không thêm LLM nội bộ)

Bản mở rộng này đóng gói **hai bộ trọng số AI**: Qwen3.5-0.8B + LoRA (classifier,
GPU) và `intfloat/multilingual-e5-small` ONNX (embeddings 384 chiều, CPU).
BM25/TF-IDF/rules là thuật toán, **không** phải mô hình weights bổ sung.
Bộ kit gồm bảy service images và **hai** image code/parent hỗ trợ
(`model-code` và `e5-base`), tổng cộng chín image archive entries.
Không tải model khi người nhận Start-Team; cả Qwen và E5 phải pass isolated
`docker run --network none` khi export/install. Chưa tự bật dense/hybrid search
qua Ticket Assistant: retrieval runtime hiện có dùng TF-IDF mặc định;
việc chuyển các route cụ thể sang E5 phải được kiểm thử permission, citation
freshness và retrieval quality trước khi công bố khả năng đó hoạt động end-to-end.

### 8.1 Build E5 trên máy phát hành (có mạng, trước approval)

Image `intelligence` trong release plan bắt buộc là image E5 mới, không phải
CPU sidecar gốc. Download và kiểm đúng upstream revision/size/SHA-256 ngoài Git,
rồi build layer E5 có runtime ONNX được pin. Script tạo receipt private.

```powershell
python scripts/apps/servicedesk/ai07/build-e5-sidecar.py \
  --model-directory .local/models/e5 \
  --download \
  --cpu-base-image <CPU-TRIAGE-IMAGE-ID> \
  --tag ssd-ai-d2-intelligence-e5:local \
  --output .local/ai-d2-e5-image.json
```

Dòng ví dụ trên dùng `\\` nối lệnh dành cho shell POSIX; trên PowerShell
hãy nhập cùng một dòng hoặc thay bằng dấu backtick. Pin `images.intelligence`
trong `.local/ai-d2-release-images.json` bằng image ID ở E5 receipt.
Build dùng Docker buildx named context `e5-model` chứa đúng hai file
`onnx/model.onnx`, `onnx/tokenizer.json`. Không commit file trọng số.
Exporter giờ cần thêm `--e5-receipt .local/ai-d2-e5-image.json`.
Issuer ký release manifest chứa `e5-image.json` và cả hai parent images;
manifest thiếu E5 bị từ chối.

### 8.2 Cloud LLM qua API, opt-in riêng (không offline)

Giữ provider integration hiện có (`intelligence/Dockerfile.cloud`,
`infra/compose/compose.generation.yaml`, `infra/compose/compose.cloud.yaml`).
Cloud LLM (ví dụ Gemini/Groq) **không** nằm trong bộ offline D2-A+E5; mặc định
DISABLED. Chỉ bật trong deployment online riêng khi đã có catalog, provider pins,
cloud-egress policy và quyền gửi dữ liệu ra ngoài. Không copy API key vào Git,
Docker layer, `images.tar`, `release.json` hay diagnostics. Không tự bật cloud
inference với ticket thực nếu chưa có chấp thuận data-processing/egress.

### 8.3 Chưa thể gọi release PASS

Phải kiểm build E5 thực tế, exact ONNX runtime dependency resolution,
license/redistribution E5, GPU recipient, clean-machine offline install,
chữ ký issuer và endpoint retrieval integration. Không suy diễn build proof từ
mã nguồn/receipt template. Các acceptance gate D2 cũ vẫn giữ nguyên.
