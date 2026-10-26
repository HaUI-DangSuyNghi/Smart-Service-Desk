# AI07 local engineering verification — 2026-10-25

Latest v1.6 verification and measured limits are in the
[closure report](CLOSURE_REPORT.md). Historical checks below retain their
original dates, scopes and counts.

Base: `origin/develop` at `f94f0ef102cbc7d5737ca05270b0affab1fde1a6`.
Branch: `feature/ai07-ticket-intelligence`.
Scope and remaining gates: [implementation handoff](IMPLEMENTATION.md).

| Check | Result |
| --- | --- |
| Full Python source suite, pinned requirements | 1,104 passed; 6 optional model tests skipped; zero failures/errors |
| Installed generic wheel, Python isolated mode | 146 passed; zero failures/errors/skips |
| Maven default `clean verify` | 276 passed; zero failures/errors/skips |
| Maven `core-only clean verify` | 78 passed; zero failures/errors/skips |
| Maven `second-domain` reuse proof | 14 passed; zero failures/errors/skips |
| Strict source boundaries | PASS, no debt/allowlist expansion |
| Boundary hostile mutations | 39 passed |
| Service Desk OpenAPI validation | PASS |
| New Python Ruff checks and format | PASS |
| Immutable inventory / recomputed DEV acceptance | PASS; 35 pinned files, all 48 DEV attempts |
| Model promotion | BLOCKED_PENDING_REVIEW |
| Hosted CI / real Gemma / GPU profile / live gateway acceptance | NOT_RUN by this change |

Python: CPython 3.12.7 on Windows with `intelligence/requirements.lock`, including
scikit-learn 1.6.1, NumPy 2.2.6 and SciPy 1.15.3. Wheel tests use `python -I`
and `scripts/intelligence/installed-pytest.ini`; classifier imports were verified
from the temporary venv's `site-packages`, not `intelligence/src`.

Java acceptance uses the cached `ai06-completion-build:local` image, Maven 3.9.9,
Java 17.0.15, and a fresh isolated PostgreSQL 16.15 database for each profile via
the existing `scripts/test-file.ps1` helper. PostgreSQL image identity:
`postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4`.
Linux is required for the existing POSIX permissions and symlink acceptance tests;
the initial Windows host attempt is not counted as a passing acceptance run.

The second-domain test selection is:
`RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest`.
Counts across Java profiles overlap and must not be presented as unique cases.

New controls exercise default-off activation, strict request parsing, persisted
input, rule-policy consistency, literal evidence, missing-information fallback,
provider degradation, source denial, actor changes, ticket drift, cross-workspace
visibility and absence of ticket/workflow writes. Classifier controls exercise
taxonomy injection, duplicate JSON keys, exact Unicode spans, abstention, rejected
draft labels, family/split leakage, policy/model drift and package exclusion of
application data, integration code and model weights.

The six skipped Python cases require five real Dense model controls and one
independently approved real generation model. Synthetic local-classifier transport
tests are architecture evidence only. No real Gemma artifact, supervised SSD model,
cloud inference, semantic synthesis or production release is certified here.
Macro-F1 remains null; authentic rights, reviewer labels and independent HOLDOUT
are still required. Existing AI05/AI06 evidence and release blockers are unchanged.

Local XML/log receipts are retained under `.local/ai07`: `python-source-final.xml`,
`python-installed.xml`, `surefire-linux-default`, `surefire-linux-core-only`,
`surefire-linux-second-domain`, and `metrics-final.json`. These are local test
receipts, not human approvals or hosted CI attestations.

## v1.5 diagnostics and classification-only iteration — 2026-10-25

Scope: [implementation, historical replay and operator runbook](DEEP_IMPROVEMENT_V1_5.md).
Base remains `d383cb249f46459856a1560019a677c6cce33ce8`; earlier verification
counts above describe their original change and are not overwritten.

| Check | Result |
| --- | --- |
| Full pinned Python source regression | 1,256 passed; 7 optional tests skipped |
| Final AI07 installed-wheel suite in isolated Python | 311 passed; 1 Linux-only symlink test skipped on Windows |
| Final receipt/paired-serving integrity controls in isolated Python | 32 passed, including refusal of context/runtime drift |
| Neutral G1 source/Windows wheel/Linux container wheel | Identical conformance receipts: 3 accepted controls, 7 rejected mutations, independent room-availability taxonomy |
| Strict source boundary gate | PASS; no debt or allowlist changes |
| Hostile boundary mutations | 39 passed |
| Ruff lint and formatting on changed Python paths | PASS |
| Compose base and GPU configuration | PASS; no image pull or native inference is added to hosted CI |
| Historical v1.4 diagnostic replay | All 288 measured attempts and 40 warmups accounted for; 71 unknown historical Gemma reasons remain unknown |
| Fresh native seven-candidate DEV study | All 1,008 measured attempts and 38 warmups accounted for; 87 bounded model-output rejections; COMPLETE_WITH_FAILURES |
| Paired policy comparison | PASS frozen model, serving and input identities; T1 changed 6/48 cases and T2 permutations changed 7–10/48 cases versus T0 |
| Native isolation and production refusal | Anonymous bridge denied, outbound unreachable, models read-only; `--require-production` exited 2, NO_GO |
| G0 Gemma adapter and T0/G0 policy files | Unchanged |
| Java/backend/frontend changes | None; Java acceptance was not rerun for this Python experiment iteration |
| Hosted CI, reviewed gold, independent HOLDOUT and release approvals | Not certified by these local tests; production NO_GO |

The first full-suite run had 1,249 passes, 7 skips and one Windows socket reset
in the existing cloud private-HTTP unauthorized-response test. Both private-HTTP
cases passed in isolation, and the subsequent complete source run passed. Final
telemetry/filter controls and two additional tests were then verified in the
311-case installed AI07 suite; counts across suites overlap and must not be added.

Pinned environment: the existing `.local/ai07/venv`, CPython 3.12.7, pytest 8.3.5,
scikit-learn 1.6.1. The final bridge code revision is
`b6c0b7614f36e2b11e69769984d8cb826d901b822d9cdd716421d29de454fe80`;
the Docker image is
`sha256:e713934166aa52b65e772ec12301a3aa785151553976f937f5c0c243898105a7`.
Linux conformance imports the installed generic wheel from `site-packages` inside
this isolated bridge; it is a bounded contract proof, not a Linux full-suite run
or a second-machine GPU acceptance.

Local receipts are under `.local/ai07/v15`: `source-final.xml`,
`wheel-final-tests.xml`, `cloud-http-recheck.xml`, `boundary-mutations.log`,
`paired-integrity-tests.xml`,
`contracts-source.json`, `contracts-wheel.json`, `contracts-linux-wheel.json`,
`historical-diagnostics-final.json`, `bridge-final.json` and the private
`runtime-final` configuration. Secrets, model files and raw experiment inputs
are not committed. Verified native DEV results and failure buckets are recorded
in the implementation document. `local-dev.json`, `diagnostics.json`,
`choice-stability.json`, `release-validation.json` and `improvement-evidence.json`
remain local. They cannot establish five-class accuracy without authentic labels.
The isolated v1.5 containers/network were stopped and removed; the model volume
and frozen receipts were retained.
