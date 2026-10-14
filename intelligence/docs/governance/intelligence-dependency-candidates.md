# AI dependency / data / model inventory — N1 draft

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
