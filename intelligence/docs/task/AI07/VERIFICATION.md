# AI07 local engineering verification — 2026-10-25

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
