# Private Java/Python capability adapter

Applications call `hpdi.intelligence.api.IntelligenceCapability`. HPDI derives
actor/workspace through HumanContext, generates correlation, validates result
identity and literal evidence, and records content-free transactional audit data.
Service Desk retains its existing SUCCESS/DEGRADED policy. Authentication,
contract and cross-workspace failures fail closed. Room Request reports 503 for
an unavailable capability rather than inventing domain advice.

The private endpoint is `/internal/intelligence/v1/triage`. Java sends the
versioned request, profile version and independently trusted actor/workspace.
The runtime retains bounded bodies, concurrency and absolute deadlines.
Authorized search/citations retain auth-before-scoring and Core re-resolution;
this adapter adds no public knowledge search endpoint.

Set a private `INTELLIGENCE_TRIAGE_TOKEN` of at least 32 random characters and
`INTELLIGENCE_TRIAGE_BASE_URL=http://intelligence:8000`, then run:

```powershell
docker compose -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence up -d --build --wait
```

`INTELLIGENCE_PROFILE_ROOT` selects the external host profile root. The default
is `./intelligence/src/apps/servicedesk/intelligence/profiles`; Room Request uses
`./intelligence/src/apps/roomrequest/intelligence/profiles` with
`INTELLIGENCE_REQUIRED_PROFILES=room-request@0.2.0`. Compose mounts it read-only
at `/profiles`. No Python host port is published. Never send this credential
to a browser.

For an installed wheel, set `HDPI_PROFILE_ROOT` to an absolute application root,
optionally pin `HDPI_REQUIRED_PROFILES`, then run `python -I -m hdpi.http_service`.
The default listener is loopback. The generic distribution remains
`smart-service-intelligence`; package discovery includes only `hdpi*` and excludes
`src/apps`. The image build context also excludes application profiles.

`scripts/test-tier-reuse.ps1` starts a fresh isolated Compose project, exercises
Room Request through Keycloak, HPDI, Core and real Python, validates the Room and
HPDI wire contracts, then stops that stack. Service Desk regression and its triage
fault scenarios remain in their dedicated acceptance workflows. See
[operations](../../../docs/architecture/STRICT_BOUNDARY_OPERATIONS.md).
