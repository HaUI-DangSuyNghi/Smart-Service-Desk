# API-first assistance: isolated synthetic DEV

Default: OFF. Real Gemini/Groq smoke and full cloud acceptance are NOT_VERIFIED.
Use this on the existing AI06-02 branch/PR #37. No GPU or local weights required.

## Configure server-only variables

Copy missing names from `.env.example` into your ignored `.env`; keep your existing
values. Names deliberately have no `AI06_` prefix. Never paste keys into chat,
commit them or put them in a `VITE_` variable.

| Variable | Purpose |
| --- | --- |
| `GEMINI_API_KEY`, `GROQ_API_KEY` | Native process env or Compose server credentials |
| `GEMINI_API_KEY_FILE`, `GROQ_API_KEY_FILE` | Alternative native-process secret files; use exactly one source per vendor |
| `PROVIDER_MODE` | `disabled` by default; explicit `synthetic-cloud-dev` activation |
| `PROVIDER_CATALOG_PATH`, `PROVIDER_CATALOG_SHA256` | Exact operator catalog file and byte SHA-256 |
| `CLOUD_EGRESS_APPROVAL_FILE`, `CLOUD_EGRESS_APPROVAL_SHA256` | External owner-approved allowlist file and byte SHA-256 |
| `CLOUD_ISOLATION_FILE`, `CLOUD_ENVIRONMENT` | External isolation attestation; environment must be `isolated-synthetic` |
| `CLOUD_EGRESS_PROXY` | Native process optional trusted relay URL; Compose sets `http://cloud-egress:8080` |
| `INTELLIGENCE_ASSISTANCE_TOKEN` | Shared Java/private Python service token |
| `INTELLIGENCE_ASSISTANCE_BASE_URL` | Java private service endpoint; Compose cloud: `http://intelligence-cloud:8001` |
| `SERVICE_DESK_ASSISTANCE_ENABLED` | Explicit `true` after approval |
| `SERVICE_DESK_ASSISTANCE_MODE` | `synthetic-cloud-dev`; rollback to `disabled` |
| `SERVICE_DESK_ASSISTANCE_SOURCE_PINS` | Existing workspace JSON pins, with rights `synthetic-public-evaluation` |

Compose reads `.env` automatically and passes credentials only to the Python
sidecar, never platform Java or the relay. Native Python reads exported process
variables; it does not automatically load `.env`. Prefer secret files for native
processes (regular file, <=4096 bytes, no symlink, POSIX mode 600; Windows ACLs).
Compose's documented environment credential mode is server-only but inspectable
by Docker administrators. Do not publish `docker compose config`, container
inspection output or process environment dumps after adding keys.

## Owner preparation and activation

1. Use a new isolated synthetic project/database and test accounts. Never attach
   this overlay to a production/customer database. Provision synthetic Core
   text/Markdown sources, metadata and tickets through the normal authorized APIs.
2. Copy the three templates in `config/assistance/` to an operator-owned directory
   **outside this checkout**, such as `D:/ServiceDesk-Operator/`. Replace exact
   model IDs and approved observed model response IDs. Verify structured-output
   support, account quotas and free-only provider-side spending limits in each
   account. Example limits are illustrative and intentionally not entitlements.
3. Set every approved actor, exact question/source hash, resource/workspace ID and
   immutable content digest. Read the [processing policy](../../governance/ai06-cloud-processing-policy.md).
   The real owner must review recipients/terms/privacy/rights, set expiry and
   issue APPROVED status. Protect these files and parent directories with ACLs.
   Committed PENDING templates and TEST_ONLY fixtures cannot enable real egress.
4. Compute byte hashes after editing, for example
   `(Get-FileHash -Algorithm SHA256 D:/ServiceDesk-Operator/catalog.json).Hash.ToLowerInvariant()`.
   Put hashes and paths in `.env`; configure `CLOUD_ENVIRONMENT=isolated-synthetic`,
   provider/app modes, source pins, service URL/token and keys. Catalog changes
   require a fresh approval digest and restart of both services.
5. Use the opt-in overlay in the isolated project:

   ```powershell
   docker compose -p servicedesk-synthetic -f compose.yaml -f infra/compose/compose.generation.yaml -f infra/compose/compose.cloud.yaml --profile assistance-cloud-dev up -d --build --wait
   ```

   Neither Python nor the relay publishes a host port. Python only has the
   internal assistance network. The relay permits CONNECT to Gemini/Groq :443,
   validates public DNS results and relays encrypted TLS without seeing keys or
   prompts. Only the relay joins the outbound network. Other platform services
   retain their existing networking; this overlay is not a platform-wide firewall.
6. Sign in as an approved synthetic actor through the existing FE/Keycloak flow,
   open the ticket deep link and submit an exact approved question. FE sends no
   provider/model/key. Unapproved questions, resources or source bytes fail before
   egress. Successful answers still require human review and never mutate tickets.

The catalog supports one route per vendor, maximum two vendors, one attempt per
request and a <=12-second provider deadline. Existing private Java transport must
allow the bounded request plus local retrieval time. Quota reservation estimates
`maxInputBytes + maxOutputTokens + 1024` tokens per attempt, with no refunds; this
is intentionally conservative. Configure RPM/TPM to allow the planned smoke count
within the actual account limits. Java and Python ledgers are independent
in-memory single-instance DEV guards; a restart is not a quota reset policy.

## Verify without keys

```powershell
python -m pytest intelligence/tests/cloud_integration -q
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python scripts/intelligence/cloud-api-smoke.py --output .local/ai06-api-first/real-cloud.json
```

The last command deliberately writes BLOCKED and exits 2 without network calls.
CI runs source and separate installed-wheel suites with injected fake transports;
no provider credentials or external quota are needed. Native entrypoint:
`python scripts/intelligence/serve-cloud-assistance.py` after exporting the
approved configuration. `--installed` uses the two installed wheels.

## Real smoke and full acceptance

Only after operator approval, prepare an external UTF-8 JSON case array with
exactly two cases: `{question, documents, expectedStatus}`. Documents follow the
private request schema (`metadataId`, `version`, `checksum`, `content`, `mediaType`).
Use one `SUCCESS` case and one legitimate `INSUFFICIENT_EVIDENCE`/model REFUSE case
that still retrieves evidence, so both actually invoke the model. Approve their
exact question/source hashes. Set enough approved quota headroom for two calls
per vendor; the runner does not sleep/retry to evade limits.

```powershell
python scripts/intelligence/cloud-api-smoke.py --real-api --cases D:/ServiceDesk-Operator/smoke-cases.json --output .local/ai06-api-first/real-cloud.json
```

This records safe outcomes, route/approval/model digests, counters, token usage
when reported and latency, never question/source/output/key bytes. It is a direct
Python smoke and cannot prove Core/SSO/browser acceptance or official quality.
Provider response model IDs are observed claims, not cryptographic revision proof.
`attemptSlotsReserved`/private `providerCalls` count bounded invocation slots;
failed DNS/connect may consume a slot without a billable provider call. Vendor
usage/cost is NOT_VERIFIED unless separately reconciled with the account console.

Then exercise the normal Service Desk endpoint through Java, Core, Python,
Keycloak/APISIX and FE for both vendors in that approved environment. Retain
content-free receipts at the final HEAD, test revocation/tenant/identity negatives,
and distinguish fake transport controls from real API results. Do not mark CLOUD
DEV READY without those results. Official reviewed evaluation/HOLDOUT and owner
release GO remain separate required gates.

## Failures and rollback

429 -> cooldown; timeout/5xx/network -> unavailable; 401/403, redirects, invalid
JSON, observed-model drift -> disabled configuration incident. The current request
fails; only a later request may choose another provider. All providers exhausted
returns unavailable, never a template/local fallback. Public responses contain
safe generic error codes; inspect content-free `provider_attempt` metrics for
vendor/state/code/digests/token counters/latency buckets. No raw provider logging.

For rollback, set `SERVICE_DESK_ASSISTANCE_ENABLED=false`, app mode `disabled`,
`PROVIDER_MODE=disabled`, recreate platform and stop `intelligence-cloud` and
`cloud-egress` in this project. Do not remove volumes or reset Core. To restore
reference mode, use only the reference overlay, approved reference pins/rights and
its original config digest; cloud env never silently changes reference behavior.
AI02 triage stays independent.

For a credential/egress incident: disable cloud first, revoke the affected key,
disable the route and invalidate/reissue the owner files, retain sanitized
evidence and remove any accidental content-bearing diagnostics under the local
incident process. Restart only after approval; do not retry with alternate
accounts/projects to work around quota or revoked rights.
