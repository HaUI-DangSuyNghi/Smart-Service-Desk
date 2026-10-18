# Strict platform configuration and verification

The assembled product starts at `vn.haui.dx.assembly.DxOsApplication` and is packaged
as `dx-platform`. Compose's application service is `platform`. Core remains a
technical module; `/api/core/v1`, existing business routes, ports and JWT audience
`dx-core` retain their public behavior.

## Runtime

From the repository root, prepare local secrets with `scripts/setup.ps1`. Supply
`INTELLIGENCE_TRIAGE_TOKEN` through the deployment environment. For local Compose:

```powershell
$env:INTELLIGENCE_TRIAGE_BASE_URL = 'http://intelligence:8000'
docker compose -f compose.yaml -f compose.triage.yaml --profile intelligence up -d --build --wait
```

`compose.triage.yaml` mounts the application profile root read-only at `/profiles`.
The default is `apps/servicedesk/intelligence/profiles`. To run the second-domain
architecture proof, configure `INTELLIGENCE_PROFILE_ROOT` to
`./apps/roomrequest/intelligence/profiles` and `INTELLIGENCE_REQUIRED_PROFILES` to
`room-request@0.2.0`. The isolated `second-domain` Maven profile excludes Service
Desk source/resources/migrations; its runtime uses only the Room Request profile.

For an installed Python wheel, set `HDPI_PROFILE_ROOT` explicitly or construct
`ProfileRegistry(root=...)`. There is no built-in app profile. Optional
`HDPI_REQUIRED_PROFILES` lists comma-separated exact `ID@version` startup pins.
The Python import package is `hdpi`; distribution metadata remains
`smart-service-intelligence==0.3.0`. Generic wheels contain contracts and code only.

The private endpoint `/internal/intelligence/v1/triage` receives:

```json
{
  "request": {
    "contractVersion": "0.1.0",
    "requestId": "server-generated-correlation",
    "profile": "configured-profile-id",
    "context": {"workspaceId": "verified-workspace"},
    "input": {"title": "persisted title", "description": "persisted description"}
  },
  "trustedContext": {"workspaceId": "verified-workspace", "subject": "verified-actor"},
  "profileVersion": "1.0"
}
```

HPDI derives the actor/workspace from verified identity before transport. The
private bearer credential identifies the authorized adapter. A public ticket
request cannot supply this context; Service Desk triage still rejects bodies.
Python independently checks envelope fields, workspace correlation, immutable
profile selection and output contract. Existing deadlines, size/concurrency caps,
no-redirect behavior and safe failures remain enforced. Operational HTTP failures
retain 502/503/504 mapping; engine DEGRADED responses retain manual review.

## Verification

The Tier boundaries workflow runs source mutation tests, compiled dependency
checks, isolated Core/second-domain tests and real second-domain Python reuse.
`scripts/test-file.ps1` runs database regression using a freshly provisioned
PostgreSQL instance and fails on skipped tests. Generic runtime tests run both
from source and against the installed wheel.

Use `scripts/acceptance/tier-reuse.py` against an isolated gateway/identity stack
configured with the Room Request profile. It exercises trusted actors, exact
knowledge provenance, app persistence, real Java/Python triage, shared inbox,
completion and negative authorization. Validate its captured responses against
`contracts/apps/roomrequest/openapi.yaml` and `contracts/hpdi/openapi.yaml` with
`scripts/check-core-contract.py`.

Service Desk acceptance and `scripts/intelligence/run-real-triage-e2e.py` retain
the real Python success, wrong credential, unavailable service, deadline and
malformed-response scenarios. Use a clean disposable checkout and isolated
databases. Public contract validators use `scripts/acceptance/requirements.txt`;
keep those tools in a separate environment from the pinned Intelligence runtime
when their dependency versions differ.
