# AI dependency / data / model inventory — N1 draft

## Trạng thái hiện tại — 16/10/2026

Danh mục N1 bên dưới là lịch sử trước runtime. AI-02 chỉ chọn Python, YAML parser,
JSON Schema validator và công cụ test/build; version, nguồn release/license và
dependency closure phải được ghi trước khi thêm dependency vào project.
FastAPI/Pydantic/Uvicorn, LLM, vector DB và retrieval chưa được chọn cho AI-02.
Phê duyệt source license, quyền dữ liệu và nghiệp vụ vẫn thuộc chủ sở hữu.
Core đã có File HTTP và checksum server; mô tả N3 bên dưới thuộc kế hoạch N1 cũ.

## Lựa chọn kỹ thuật AI-02

Đã kiểm tra nguồn release và license ngày 16/10/2026 trước khi thêm dependency.
Đây là lựa chọn kỹ thuật cho library local theo yêu cầu triển khai; không thay
thế quyết định source license của repository, approval nghiệp vụ hoặc release.
Các version là baseline tương thích để tái lập, không tuyên bố mới nhất/an toàn.

| Thành phần | Pin | Vai trò | Nguồn license đã đối chiếu |
| --- | --- | --- | --- |
| CPython | 3.12.7, Unicode 15.0.0 | Runtime; chỉ hỗ trợ 3.12 trong AI-02 | [PSF và notices, tag v3.12.7](https://github.com/python/cpython/blob/v3.12.7/LICENSE) |
| PyYAML | 6.0.2 | Runtime parser với SafeLoader, từ chối duplicate key | [MIT, tag 6.0.2](https://github.com/yaml/pyyaml/blob/6.0.2/LICENSE) |
| jsonschema | 4.23.0 | Runtime Draft 2020-12 validator | [MIT, tag v4.23.0](https://github.com/python-jsonschema/jsonschema/blob/v4.23.0/COPYING) |
| pytest | 8.3.5 | Dev/test | [MIT, tag 8.3.5](https://github.com/pytest-dev/pytest/blob/8.3.5/LICENSE) |
| setuptools | 75.1.0 | Build backend | [MIT, tag v75.1.0](https://github.com/pypa/setuptools/blob/v75.1.0/LICENSE) |
| wheel | 0.44.0 | Build wheel | [MIT, tag 0.44.0](https://github.com/pypa/wheel/blob/0.44.0/LICENSE.txt) |
| build | 1.2.2.post1 | Dev/build frontend | [MIT, tag 1.2.2.post1](https://github.com/pypa/build/blob/1.2.2.post1/LICENSE) |

`requirements.lock` khóa cả runtime, build, test và dependency bắc cầu; marker
Windows chỉ bật colorama. Bằng chứng resolution/license metadata được ghi riêng
trong báo cáo AI-02 sau khi cài môi trường sạch. Kiểm tra top-level license không
phải audit đầy đủ transitive hoặc xác nhận đủ điều kiện phân phối. Không thêm
network inference, framework HTTP, model, DB client hoặc bộ số học cho AI-02.

## Danh mục lịch sử N1

Status: PROPOSED. No Intelligence dependency or model has been adopted/pinned.
Repository/source license and knowledge usage rights still need owner decisions.
This inventory does not declare a license verified or grant redistribution rights.

Status vocabulary: `PROPOSED` = candidate awaiting review; `ADOPTED` = approved
project choice; `INSTALLED` = present in an environment (does not imply adoption);
`TRANSITIVE` = confirmed in the resolved dependency tree; `REJECTED` = explicitly
declined. All runtime entries below remain `PROPOSED`; there are no adopted,
installed project dependencies or confirmed transitive dependencies in this package.

## Dependency candidates

| Candidate | Future role | Adoption / version | License verification |
| --- | --- | --- | --- |
| Python 3.x | Runtime language | PROPOSED; no project version pin | UNVERIFIED for selected release |
| FastAPI | Optional HTTP adapter | PROPOSED; not installed as project dependency | UNVERIFIED |
| Pydantic | Adapter request/response validation | PROPOSED; not adopted | UNVERIFIED |
| Uvicorn | Optional ASGI host | PROPOSED; not adopted | UNVERIFIED |
| scikit-learn | TF-IDF/cosine candidate | PROPOSED; not adopted | UNVERIFIED |
| NumPy | Potential transitive numeric dependency | PROPOSED; no confirmed TRANSITIVE tree yet | UNVERIFIED |

Host Python, PyYAML and jsonschema are used only as existing local authoring/check
tools for the package validator. Their presence does not mean they are Intelligence
runtime dependencies (`INSTALLED` on the host, not `ADOPTED` by this project).
No pip install, requirements file, service or lockfile is
added in N1. Versions used during validation are recorded in the package report.
Before adoption, pin actual versions, inspect official release/source licenses and
the transitive tree, record notices and test compatibility; do not rely on expected
license names in the master draft as verification.

## Data / configuration inventory

| Artifact | Owner / state | Version and provenance |
| --- | --- | --- |
| Taxonomy/routing | Intelligence Service Desk profile, proposed | `taxonomyVersion=0.1.0`, `profileVersion=service-desk@0.1.0`; team-authored |
| Priority policy | Service Desk / Process Owner; PROPOSED; Intelligence implements | `priority_policy_version=priority-policy@0.1.0`, pinned by `service-desk@0.1.0`; business approval pending |
| Conflict/rule catalogue | Intelligence profile, PROPOSED; no executor | `rulesVersion=0.1.0`; team-authored |
| 12 tickets with expected suggestions | Intelligence authoring/evaluation fixtures | `datasetVersion=0.1.0`; synthetic, manually annotated, rights pending |
| 5 knowledge Markdown documents | Team-authored source assets; canonical ownership belongs to Core after future ingest | Artifact versions `0.1.0`; exact-byte SHA-256 in local manifest; rights pending |
| Core binding | Core exclusively | No workspace/metadata UUID binding yet; no metadata created for this package |
| Retrieval gold associations | Intelligence authoring fixtures, PROPOSED | 12 MANUAL_EXPECTED associations; NOT BENCHMARK / NOT ENGINE OUTPUT; rights pending |
| Text normalization policy | Intelligence draft, PROPOSED | `text-normalization@0.1.0`; behavior documented, not implemented |
| TF-IDF/retrieval configuration | Future Intelligence engine | No engine/index configuration implemented; needs reviewed runtime versions |
| Index/cache | Future Intelligence derived state | Not created; must be rebuildable, never authoritative |
| Evaluation report | Future Intelligence evaluation | Not generated; N1 consistency checks are not model accuracy |

## Model inventory

No trained classifier, embedding model, LLM, model weights, prompts, vector store
or external provider is selected/downloaded. Model candidates and license status
are not fabricated. Adoption later must record identifier, release/version, origin,
weights checksum, usage restrictions and evaluation evidence. Rules + TF-IDF are
planned first approaches, not proof that any engine currently exists.

## Data rights and release

All 12 tickets are synthetic; all 5 knowledge documents are authored in this repo
from the supplied package, without copying external content. Rights remain
`PENDING_OWNER_APPROVAL`, `usageRights=null`; being self-authored does not authorize
the assistant to choose a license. Internal fixtures are not a public/release
corpus. Do not ingest/publish before rights are decided.

Local [knowledge manifest](../../../fixtures/intelligence/service-desk/knowledge/manifest.json) records source,
version, exact bytes/checksum and null Core bindings. Once approved, prepare Core
metadata requests with the decided usageRights and artifact digest; Core generates
the UUID/owner/time and authorizes the workspace. N3 upload verifies received bytes.
Do not send `PENDING_OWNER_APPROVAL` as a pretend license to Core.

Future development/holdout splits must separate source scenarios/templates, not
slightly edited duplicates. These 12 examples are contract/annotation fixtures,
not a representative production set or uncontaminated holdout benchmark.
