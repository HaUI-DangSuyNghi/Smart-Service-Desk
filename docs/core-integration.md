# Core integration handoff

Date: 2026-10-15 (Asia/Saigon). Branch: `feat/core-integration`.
Scope: integration of existing Core capabilities, not Core Acceptance or production approval.

## Merge gate and delivery

GitHub REST confirms PR #10 merged into `develop` at 2026-10-15T12:13:47Z.
The merge commit is `73d5b64d72fd3909dc3d8b042961964f886610af`, reachable
on fetched `origin/develop`; local `develop` matches it. This feature starts there.
Workflow (#8), File (#9) and Metadata (#10) are merged capabilities, not pending work.
No direct changes to shared branches, force push or automatic merge are performed.

## Runtime changes

- Spring discovers the existing File and Workflow services and policy adapters.
  Workflow resolves the authenticated user on each call, not once per singleton.
- Registry validation runs at application readiness, after Flowable auto-deploys.
  Lazy injection avoids validating an empty deployment during bean construction.
  Invalid registrations still fail startup rather than silently permitting tasks.
- Controllers delegate to the existing services. Workflow now exposes definitions,
  candidate/assigned inbox, claim, completion and history; task mutation validates
  workspace, instance and task together before applying candidate/assignee checks.
- Existing independent-approval BPMNs become the canonical `processes/` resources;
  redundant copies are removed. Isolated tests use these same runtime definitions.
  A legacy probe is retained inline in its regression test, not deployed in production.
- File HTTP upload, describe and download use the existing private storage service.
  File-backed metadata creation is the HTTP link operation, with the real metadata
  resource-policy adapter and transactional `file.link` audit; no arbitrary link bypass.
- Multipart limits are 5 MiB/file and 10 MiB/request. Downloads are attachments with
  `nosniff`, original media type and byte length. The container volume directory is
  prepared as UID/GID 10001 with mode 0700; Compose points storage at that volume.
  Existing volumes need an explicit ownership/permission review before upgrade;
  do not clear a volume or recursively change arbitrary storage to make startup pass.
- JSON/validation/upload errors use the shared Problem Details contract. Implemented
  routes move into `core.yaml`; `assign` remains explicitly unimplemented in the target.
- Keycloak's existing demo profiles failed fresh password-grant login with
  `resolve_required_actions` / `Account is not fully set up`. Synthetic display names
  now satisfy profile validation; no validator, issuer or audience check is disabled.
  Existing realms are not overwritten: correct demo first/last names via Admin API/UI
  if upgrading one, or use a separately named fresh test project. Never reset shared DBs.

## Compatibility and security review

- Legacy start `title` remains accepted alongside the new `variables` form; duplicate
  title sources are rejected. `id` aliases `processInstanceId`; history retains
  `ended`, `tasks`, task IDs and `actor`, alongside the richer current contract.
- Completion now requires explicit `outcome` and prior claim by an independent actor.
  The former creator-self-completion probe behavior is intentionally not preserved.
- No applied SQL migration is modified. V1-V4 and metadata snake_case response fields
  remain intact. Old process versions remain readable, but mutation returns 409 until
  an explicit instance migration is approved; deployment does not auto-migrate instances.
- File sharing is unchanged: unlinked bytes are workspace-shared; linked reads use
  the registered resource policy. Platform admin has no implicit content access.
  Do not use upload-then-link for owner-private confidential drafts.

## Verification evidence

- `scripts/test-file.ps1`: **66 tests, 0 failures, 0 errors, 0 skipped** on disposable
  PostgreSQL 16.6 databases; script also rejects skipped/error/failure XML reports.
  Includes 61 regressions and 5 full Spring runtime tests with the real security
  filter chain, service beans, migrations, Flowable and audit implementation.
- The actual Spring JWT decoder accepts a signed token and rejects validly signed
  wrong-issuer, wrong-audience, expired tokens and an invalid signature. Test keys and
  a loopback JWKS server exist only in the test source, not production configuration.
- A database-triggered audit failure rolls back engine history, process scope and
  audit together; the test verifies Flowable and JDBC share the transaction manager
  and that the JDBC manager uses the same datasource.
- Fresh named Compose deployment: PostgreSQL, Keycloak, Core and APISIX healthy.
  `scripts/smoke.ps1` exercises real Keycloak tokens through the Gateway, both approval
  definitions, independent claim/complete, candidate/assigned inbox, concurrent
  completion (one 204, one 409), files, metadata linking/search, workspace isolation,
  audit actors and request IDs. Real multipart requests verify 413 and 415.
- `scripts/check-core-contract.py` validates the captured raw HTTP JSON against
  `core.yaml`: **80 responses** pass, including three direct-backend requests proving
  401 without JWT, 200 with a valid JWT and 403 for a foreign workspace. Both OpenAPI
  documents pass `openapi-spec-validator`. Read-only DB checks find no engine/scope
  or audit inconsistency and no workspace/checksum mismatch in file-backed metadata.

Reproduce (test build image must contain Maven dependencies for offline verify):

```powershell
docker build --target build -t dx-core-build:0.1.0 backend
powershell -NoProfile -File scripts/test-file.ps1
powershell -NoProfile -File scripts/setup.ps1
docker compose -p dx-core-integration-final up -d --build --wait
powershell -NoProfile -File scripts/smoke.ps1 -ComposeProject dx-core-integration-final
py -3.11 -m pip install openapi-spec-validator
py -3.11 -m openapi_spec_validator contracts/openapi/core.yaml
py -3.11 -m openapi_spec_validator contracts/openapi/core-target.yaml
py -3.11 scripts/check-core-contract.py .local/core-integration-http.json
Get-Content scripts/check-db.sql -Raw | docker compose -p dx-core-integration-final exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1
```

## Explicit remaining gates

Unscanned HTTP uploads are **disabled by default**. Only local synthetic-data Compose
opts into `DX_ALLOW_UNSCANNED_UPLOAD=true`; it is not malware scanning. Do not enable
this opt-in in production: add an approved fail-closed scanner before enabling upload.
The service's existing type/extension/signature/UTF-8 checks are not antivirus.

Known rollbacks compensate storage; unknown outcomes deliberately retain bytes.
For crash-orphan reconciliation: pause writers, take matching DB/storage snapshots,
compare UUID blobs with `file_object.storage_key`, quarantine unreferenced blobs
privately, investigate unknown commits and delete only after retention/review.
Never delete by age alone or while uploads can commit. Missing referenced bytes
require recovery from backup, not deletion of DB provenance. Automated reconciliation,
backup/restore rehearsal, restart/recovery, vulnerability scanning, supported-version
upgrade and independent team sign-off belong to the next Core Acceptance/release gate.

After a human merges the integration PR, verify that merge before creating
`test/core-acceptance`. Do not label all Core or the competition product complete.
