# Production candidate gates

This is not a production approval. Do not deploy over an existing development or
production database. Image/Keycloak upgrades and database role changes require
an isolated staging restore and a reviewed cutover plan, not in-place guessing.

## Required order

1. Build release candidates, record source SHA and immutable image digests, and
   run **Production security gate**. No HIGH/CRITICAL/EOSL exceptions are supplied.
   The integration workflow remains report-only for development; it is not the
   production gate. The strict workflow includes the frontend runtime image.
2. Resolve any remaining vendor findings or obtain an explicit owner decision;
   this change neither suppresses vulnerabilities nor grants risk acceptance.
3. Validate the production configuration and externally managed TLS proxy.
4. Only after those gates pass: run real browser OIDC/PKCE and business workflow
   E2E on staging, an agreed load/SLO test, backup/restore and rollback rehearsal.
   None of those results may be inferred from a configuration self-test.

## Production configuration

Use all three Compose files in this order:
`compose.yaml`, `infra/compose/compose.triage.yaml`,
`infra/compose/compose.production.yaml`. Use an explicit `production-...` project,
new volumes and a dedicated secret store or an access-restricted external env file.
Do not put that file, realm credentials or TLS keys in Git. The overlay never builds
runtime images: provide registry references with `@sha256:` for postgres, platform, gateway,
frontend and intelligence. Build the frontend with the exact public HTTPS identity
URL and `dx-web` client before scanning/publishing its digest.

Required deployment settings:

- `PRODUCTION_APP_URL`, `PRODUCTION_IDENTITY_URL`: separate public HTTPS origins,
  without paths or trailing slashes.
- `PRODUCTION_POSTGRES_IMAGE`, `PRODUCTION_PLATFORM_IMAGE`, `PRODUCTION_GATEWAY_IMAGE`,
  `PRODUCTION_FRONTEND_IMAGE`, `PRODUCTION_INTELLIGENCE_IMAGE`: reviewed digests.
- Use `infra/compose/compose.release-build.yaml` only when building/scanning release
  candidates. It rebuilds the same gosu release with a patched Go toolchain and
  installs the vendor zlib fix. Publish that PostgreSQL image, not the development
  upstream image, as `PRODUCTION_POSTGRES_IMAGE`. Deployment never builds it.
- `PRODUCTION_DATABASE_ADMIN_PASSWORD`, `PRODUCTION_APP_DATABASE_PASSWORD`,
  `PRODUCTION_IDENTITY_DATABASE_PASSWORD`, `PRODUCTION_IDENTITY_ADMIN_PASSWORD`,
  `DIRECTORY_CLIENT_SECRET`, `BACKGROUND_CLIENT_SECRET`: six distinct random secrets,
  at least 32 characters each. Existing Compose-required development variables must
  also be supplied for interpolation; they are not used as production DB credentials.
- `PRODUCTION_INTELLIGENCE_TOKEN`: a seventh distinct random secret of at least
  32 characters, shared only by the platform and internal intelligence service.
- `PRODUCTION_REALM_FILE`: external realm `dx-lab`, `sslRequired=external`, no demo
  or human users, password grants disabled, exact `dx-web` callback URLs, exact web
  origin and S256 PKCE. Retain reviewed service-account permissions and match the
  directory/assessment client secrets. Assign the built-in `kc-admin-api` scope to
  `dx-directory` as an optional client scope. Provision human users separately with MFA
  according to the operator's policy. Never reuse the demo realm import.
- `PRODUCTION_TRUSTED_PROXY_ADDRESSES`: exact trusted TLS proxy IPs/CIDRs.
  Never use a catch-all. The proxy must overwrite forwarded headers, route the app
  origin to loopback port 5173 and the identity origin to loopback port 8081,
  enforce HTTPS and certificate verification, and deny public direct access to
  internal ports. Certificate issuance/renewal and DNS are operator-owned gates.

Validate without deploying or printing resolved credentials:

```powershell
python scripts/check-production-config.py --self-test
python scripts/check-production-config.py --env-file <external-env-file> --project production-staging
```

New database volumes initialize separate non-superuser `core` and `identity`
roles. Existing volumes are deliberately not migrated by the init script. An
operator must migrate/restore roles and ownership in staging before cutover.
Uploads remain disabled until an approved content-scanning path exists; do not
turn on `DX_ALLOW_UNSCANNED_UPLOAD` to bypass that restriction. The production
Spring profile also removes localhost WebSocket origins.

## Pending operational evidence

### Local candidate audit, November 9, 2026

These are uncommitted local candidates, not the previously green PR artifacts.
Trivy completed all six runtime inventories; the strict release gate still fails:

| Runtime | HIGH | CRITICAL | Gate |
| --- | ---: | ---: | --- |
| Platform | 0 | 2 | FAIL |
| Hardened PostgreSQL | 64 | 1 | FAIL |
| Keycloak | 6 | 0 | FAIL |
| Intelligence | 47 | 0 | FAIL |
| Gateway | 0 | 0 | PASS |
| Frontend | 0 | 0 | PASS |

The platform findings are CVE-2026-47884 and CVE-2026-47890 in Spring WebMVC
6.2.19. The scanner lists 7.0.9 as fixed. Neither finding alone justifies forcing
Spring 7 into Boot 3. No CVE exclusion or release exception has been introduced.
The PostgreSQL gosu findings are resolved; its remaining findings are OS
packages, including critical CVE-2026-6653 in libxml2. The PostgreSQL and
intelligence reports list no fixed versions for their remaining HIGH/CRITICAL
findings. Keycloak still includes OS findings and Guava CVE-2026-102554
(33.6.0-jre; scanner lists 33.7.2 as fixed), requiring a validated vendor upgrade
or a separately tested derivative. A missing fixed version is not risk acceptance.

Local package inventories and exact image IDs are under `.local/`:
`scan-production-backend-patched-20261109`,
`scan-production-postgres-patched-20261109`,
`scan-production-runtime-patched-20261109`,
`scan-production-gateway-patched-20261109`, and Keycloak at index 1 in
`scan-production-vendors-20261109`. Frontend production dependency scanning also
passes; it does not cover development dependencies or certify browser behavior.

Backend `verify` passes (967 tests reported, 345 environment-gated skips), and
frontend typechecking and 219 unit tests pass. The isolated Linux Python source
suite also passes (1,542 passed, six skipped), with no external network access.
Production preflight self-tests, resolved synthetic configuration, and all 43
tier-boundary regression tests pass. These do not exercise the
production database, identity server, browser, backup or deployment rollback.
No real-service E2E, load, recovery or rollback rehearsal is approved by these
results; those remain pending the release gates and an operator-owned staging
environment with TLS, secrets, SLO and recovery targets.

### Stability-first Spring assessment

The upstream advisories GHSA-pc63-qcmh-9cmg and GHSA-j9f9-w8pj-32f8 were
checked on November 9, 2026, along with Maven Central metadata. The former
requires XSLT view rendering; the latter concerns SSE with view fragments.
No patched public 6.2 release is listed after 6.2.19 at this check.

The application currently uses REST controllers and does not depend on XSLT,
server-rendered MVC views, ModelAndView or FragmentsRendering. Knowledge chat
does use SSE, but sends JSON through StreamingResponseBody rather than rendering
view fragments. RestOnlyMvcTest enforces these application-code constraints,
including negative fixtures. KnowledgeChatControllerTest verifies that CR/LF,
blank lines and forged SSE field text remain inside one JSON event rather than
creating additional frames. Existing permission and security-context tests remain.

Keep Boot 3 / Spring 6 / Flowable 7 for this stabilization pass. These checks
reduce the risk of accidentally enabling the affected features; they are not a
vendor patch or proof covering arbitrary third-party configuration. The image
findings and strict production gate remain unchanged. Reassess when adding
server-side views or changing dependencies, and use a supported patched line
or a separately tested migration before claiming the dependency issues fixed.

### Keycloak compatibility fix

Real-service regression found that the upgraded identity server rejects directory
tokens without its Admin API scope/audience, causing administrator checks and
settings requests to return 503. Both directory token callers now explicitly
request `scope=kc-admin-api`, and the realm template assigns that optional scope
to `dx-directory`. Real gateway smoke also found that group role mappings are
filtered without `view-clients`; the directory service account now includes this
read-only realm-management role. No write roles are granted and fail-closed
behavior is unchanged. Tests cover unscoped rejection, scoped permission reads, denied
writes, account disabling and live role revocation using the same end-user JWT.

For an existing realm, importing the JSON again does not update client settings.
Before enabling the new application, use the Keycloak Admin Console:
Clients -> dx-directory -> Client scopes -> Add client scope -> kc-admin-api ->
Optional. Also assign realm-management `view-clients` to the dx-directory service
account alongside `view-users`, `query-users` and `query-groups`. Retain the
existing default scopes. Do not disable
Keycloak audience checking, add manage-users, delete database volumes or run
`start.ps1 -Reset` just to apply this change. Fresh realms use the updated
template; the production preflight rejects a realm file missing this assignment.

After this fix, `test-fresh-admin.ps1 -Profile full` passes all 1,031 backend tests
with zero failures, errors or skips against disposable real PostgreSQL and
Keycloak instances. Evidence: `.local/fresh-admin-d319922890de/summary.json` and
the corresponding JUnit reports. The suite includes authorization, live role
revocation, database/workflow behavior and settings HTTP/JVM restart checks.
The production database initialization was also checked separately: both runtime
roles are non-superusers without CREATEDB/CREATEROLE and can manage their own DB.
These results do not replace browser E2E, load or operational recovery rehearsal.

Record the source SHA, deployed image digests, target environment, test identity
scope, browser/PKCE results, p95/error-rate SLO and agreed concurrency, recovery
point/time targets, restore checksums and rollback outcome. Do not roll an older
application onto a newer schema without a verified compatibility plan. Quiesce
writes before paired database/blob backups; preserve both current and rollback
copies. A passing local development recovery test is not a production cutover.

### Isolated local staging, November 9, 2026

Project `staging-stability-20261109` uses dedicated secrets, realm and named
volumes, loopback ports only, and no existing development/production data.
It is a local HTTP development-profile rehearsal, not the production overlay
or externally hosted HTTPS staging. The frontend was rebuilt with the real
loopback identity URL; do not reuse an image built with example identity URLs.
Resolved configuration, credentials and backups stay in ignored `.local/`.

Evidence under `.local/staging-stability-20261109/`:

- `smoke-final.log`: gateway/direct authentication, workspace isolation,
  file/metadata linking, audit and two approval workflows pass.
- `browser.json`: real Chromium login with S256, reload/SSO, logout and no
  persisted tokens pass for requester, agent and administrator; no API mocks.
- `browser-business.json`: repeats those sessions after recovery and verifies
  requester ticket creation and detail rendering through the real browser UI.
- `servicedesk.json`: real ticket create/read/comment/progress and foreign
  workspace denial pass through the gateway.
- `load.json`: 100 authenticated reads with concurrency 5, zero errors,
  p95 2110 ms while the full backend suite also ran. This is a bounded smoke,
  not an agreed production capacity/SLO result.
- `restore.json`: stopped application/identity writers; backed up both databases
  and uploaded files; restored core/Keycloak into an isolated temporary PostgreSQL
  instance and matched normalized data-dump SHA256. Uploaded-file checksums were
  recorded, not a full file-volume restore or application-on-restored-data test.

After starting this isolated stack and waiting for all health checks, repeat:

```powershell
./scripts/smoke.ps1 -ComposeProject staging-stability-20261109 -EnvFile .local/staging-stability-20261109/staging.env
node scripts/acceptance/staging-browser.cjs .local/staging-stability-20261109/staging.env .local/staging-stability-20261109/browser-repeated.json
```

Both checks write synthetic test data and must not target production. The browser
check also creates a ticket through the real UI. A verified previous release and
schema compatibility plan are still required for rollback rehearsal. Strict
security gates, HTTPS staging and production load/recovery targets remain pending.

After adding the read-only client visibility regression, the full backend suite
passes 1,032 tests with zero failures, errors or skips. Evidence:
`.local/fresh-admin-6fe24a577fff/summary.json`. The new real-Keycloak test reads
agent/coordinator/requester permissions and confirms group-role and client writes
remain forbidden. These are uncommitted worktree results, not GitHub CI approval.
