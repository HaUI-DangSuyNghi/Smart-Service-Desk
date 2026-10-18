# INT-01 combined baseline

## Source and merge history

Remote heads verified on 2026-10-18 before integration:

| Source | SHA |
| --- | --- |
| origin/develop | e39356cc44ef50421a9b81199756ec9784fe161a |
| PR #20 Java BE-01 | 0050da1f4b840bde53b8b68f6e4dd74da50f960c |
| PR #21 Python AI-04 | 8bbee5212244cbd68ccfc9bb56cd4dda5366ac15 |
| PR #19 authorized retrieval AI-03 | c16598dd032b962b79a9100c80c61b79419c4749 |

Branch `integration/be-ai-triage-retrieval` starts at that develop head and has three
explicit non-fast-forward merges in order #20, #21, #19. No textual conflicts occurred.
No original PR head or developer worktree was changed. The refactor branch is not merged.
Historical AI-04 runs remain in Git/PR #21; they are not current integration proof.
The exact tested combined SHA is in the integration PR, CI job summary and artifact
`integrated-current-head-evidence/integration-summary.json` (not a self-referential SHA here).

## Semantic integration fixes

- Docker installs the wheel actually built from current pyproject metadata, not historical
  `smart-service-intelligence==0.2.0`. AI-03 version 0.3.0 and its full lock are preserved.
- Both service adapters require at least 32 ASCII bearer characters before optional `=`
  padding. Empty, shorter, whitespace, non-ASCII and header-injecting credentials fail
  before engine/network use. Generate at least 32 random bytes (`openssl rand -hex 32`);
  character validation does not prove entropy and a hand-authored password is not a secret
  generation policy. No default service credential or credential logging is introduced.
- The current-head workflow no longer assembles fixed #19/#20 snapshots. Java, Python,
  installed wheel, Docker images and AI-03 tests use the same checkout and record its SHA.
- The E2E harness refuses mismatched Java/Python revisions. Runtime image OCI revision
  labels and the installed image package version are checked against that checkout.

## Frozen boundaries

BE-AI-TECH-0.1, shared wire schema 0.1.0, both HTTP routes and public 502/503/504 mappings
are unchanged. Package version 0.3.0 is NOT profile version: runtime profile remains
`service-desk@0.2.0`. No trustedContext wire wrapper, public Python route, automatic state
mutation, domain ownership transfer or new application feature is introduced.

## Ordered gate execution

Workflow `.github/workflows/intelligence-triage-e2e.yml` runs on relevant Java, Python,
contract, fixture, infrastructure, deployment and verification changes to the integration
PR; workflow_dispatch also supports an exact branch/SHA. It checks out the actual PR head,
not GitHub's synthetic merge revision. Other existing regression workflows remain enabled.

| Gate | Evidence / scope |
| --- | --- |
| G1 | Full Maven verify on disposable PostgreSQL; fail on any failure/error/skip |
| G2 | Entire AI-02/AI-03/HTTP source and installed-wheel suites, package build/import |
| G3 | No-cache Python image, metadata version, wheel resources, AI-02 + AI-03 runtime |
| G4 | Current Java client -> authenticated Python -> real RulesEngine -> Java validator |
| G5 | Real wrong-token/refused endpoint; timeout/malformed injected after Python SUCCESS |
| G6 | No Ticket/workflow mutation; manual claim/complete after actual Python shutdown |
| G7 | Real Keycloak login/JWT + APISIX + Java/Python for requester/agent/coordinator in two workspaces |
| G8 | AI-03 real authorized Core corpus/provenance/citation and retrieval evaluation |
| G9 | Configuration checks + five image vulnerability inventories; production findings reported separately |
| G10 | Clean checkout, same SHA, current package/image identities, reproducible commands |

The G7 check includes another same-workspace requester, cross-workspace membership and
Ticket isolation, no platform-admin implicit access, no-body validation, no public Python
port/route, unchanged persisted counts and the real manual fallback. Synthetic identities
and knowledge exist only inside that run's disposable stack. Real G7 is HTTP acceptance,
NOT browser UI E2E. Browser UI E2E remains NOT RUN.

A stopped Compose service can refuse a connection (503) OR leave an unreachable container
address until the Java deadline (504). G7 verifies the matching problem code for either
physical outage, not a fabricated DEGRADED result. G5 separately proves deterministic
refused-connection 503 and injected-delay 504. Database and service credentials are generated
per run, not fixed historical passwords; all published test ports bind loopback only.

No continue-on-error or test skip is allowed. Missing or skipped technical evidence fails
the workflow. Image scanning uses the explicitly labeled assessment (`-ReportOnly`) mode:
scanner failure fails CI, but successful scanning with vulnerabilities is a recorded G9 FAIL,
not a technical functional-test failure. CI green never means the production security gate
passed. `integration-summary.json` retains findings, productionGate results and release blockers.

## Reproduction

The canonical complete reproduction is to run **Integrated current HEAD acceptance** on
this branch/SHA; it provisions a clean runner and separate PostgreSQL and application stacks.
For local reproduction use a NEW checkout/worktree and never an existing developer database.
Requires Java 17, Maven, Python 3.12, Docker/Compose >=2.24.4, PowerShell 7 and PostgreSQL client.
Use a unique `INT01_PROJECT=int01-<random>` and unused loopback ports; never reuse an existing
project or volume. Commands below are Bash unless marked PowerShell. Do not print secret envs.

```sh
export INT01_PROJECT="int01-$(openssl rand -hex 6)"
export INT01_SOURCE_SHA="$(git rev-parse HEAD)"
export INTELLIGENCE_TRIAGE_TOKEN="$(openssl rand -hex 32)"
export INTELLIGENCE_TRIAGE_BASE_URL=http://127.0.0.1:18000
export FILE_TEST_DB_PASSWORD="$(openssl rand -hex 32)"
export WORKFLOW_TEST_DB_PASSWORD="$FILE_TEST_DB_PASSWORD"
export FILE_TEST_JDBC_URL=jdbc:postgresql://localhost:15432/file_storage
export WORKFLOW_TEST_JDBC_URL=jdbc:postgresql://localhost:15432/workflow_approval
docker run -d --name "$INT01_PROJECT-regression" -e POSTGRES_USER=workflow -e POSTGRES_DB=file_storage -e POSTGRES_PASSWORD="$FILE_TEST_DB_PASSWORD" -p 127.0.0.1:15432:5432 postgres:16.6-bookworm
# Wait until pg_isready -U workflow -d file_storage succeeds before creating the second DB.
docker exec "$INT01_PROJECT-regression" psql -U workflow -d file_storage -c 'CREATE DATABASE workflow_approval'
mvn -B -f backend/pom.xml verify
python3.12 -m venv .local/int01-venv
.local/int01-venv/bin/pip install -r intelligence/requirements.lock
.local/int01-venv/bin/python -m pytest intelligence/tests -q
.local/int01-venv/bin/python -m build --no-isolation --outdir .local/int01/dist intelligence
.local/int01-venv/bin/python scripts/intelligence/install-built-wheel.py --dist .local/int01/dist
.local/int01-venv/bin/python -I scripts/intelligence/verify-installed-package.py
docker build --no-cache --label "org.opencontainers.image.revision=$INT01_SOURCE_SHA" -f intelligence/Dockerfile -t "$INT01_PROJECT-intelligence:ci" .
docker run -d --name "$INT01_PROJECT-python" -e INTELLIGENCE_TRIAGE_TOKEN -p 127.0.0.1:18000:8000 "$INT01_PROJECT-intelligence:ci"
# Wait for a 401 response from the private endpoint with no service credential.
.local/int01-venv/bin/python scripts/intelligence/run-real-triage-e2e.py --java-root .
pwsh -File scripts/setup.ps1
docker compose -p "$INT01_PROJECT" -f compose.yaml -f compose.triage.yaml -f scripts/acceptance/integration-compose.yaml --profile intelligence up -d --build --wait --wait-timeout 300
.local/int01-venv/bin/python -I scripts/acceptance/triage-gateway.py --project "$INT01_PROJECT" --output .local/int01/gateway-triage.json
.local/int01-venv/bin/python -I scripts/acceptance/intelligence-retrieval.py --output .local/int01/authorized-retrieval.json
```

All exact no-skip XML capture, image verification, scan and summary commands are in the
workflow. In PowerShell, invoke `scripts/scan-core.ps1 -ReportOnly` with an explicit image
list and `-CacheVolume "$env:INT01_PROJECT-trivy-cache"`; never reuse a developer cache volume.
The override supports `INT01_KEYCLOAK_PORT`/`INT01_GATEWAY_PORT`; pass matching loopback URLs
to both acceptance scripts if defaults 18081/19080 are changed. Stop only containers with
your run's project label/name. No reset, existing-volume removal or global Docker prune is used.

## Before merge and release

Recommended history strategy: **B**. Review and merge this integration PR once, then close
#19/#20/#21 as superseded with links and team agreement. Their original commits are already
ancestors through explicit merges. Do NOT additionally squash/merge the original PRs.
No original PR is closed or merged by this task. If the team chooses A instead, do not merge
this aggregate PR as well; port the small integration fixes through a separately reviewed follow-up.

Release remains blocked by G0-B Process Owner approval, the pending formal root LICENSE
grant, fixture/data usage rights clearance and any G9 production security failures or missing
scan evidence. Do not mark the PROPOSED business profile APPROVED or treat synthetic
acceptance rights as permission to redistribute pending-rights repository fixtures.
