# AI06 assistance engineering operations

The default deployment has no assistance route. Current opt-in mode is an
explicit deterministic reference over synthetic-only Core sources. Production
and official model quality are BLOCKED; see the integration ADR and status.

## Local reference composition

1. Create synthetic text/Markdown files and metadata through authorized Core
   APIs. Record actual metadata IDs, version, checksum and usage rights exactly
   `synthetic-test-only`. Never copy draft AI05 documents into this catalog.
2. Generate a separate random `INTELLIGENCE_ASSISTANCE_TOKEN` of at least 32
   bearer characters. Keep it out of the frontend, logs and version control.
3. Compute SHA256 of the checked-in application file
   `intelligence/src/apps/servicedesk/intelligence/assistance-reference.json` and
   assign `HDPI_ASSISTANCE_CONFIG_SHA256`. Set
   `HDPI_ASSISTANCE_REFERENCE_ENABLED=1` explicitly.
4. Set `INTELLIGENCE_ASSISTANCE_BASE_URL=http://intelligence-assistance:8001`,
   `SERVICE_DESK_ASSISTANCE_ENABLED=true`, and
   `SERVICE_DESK_ASSISTANCE_MODE=synthetic-reference` for this local deployment.
5. Set `SERVICE_DESK_ASSISTANCE_SOURCE_PINS` to a JSON object keyed by workspace,
   with arrays of `{metadataId, version, checksum, usageRights}` from step 1.
   Catalogs are bounded to ten workspaces and ten sources per workspace.
6. Start the explicitly selected reference profile:

```text
docker compose -f compose.yaml -f compose.generation.yaml --profile assistance-reference up -d --build --wait
```

This starts local demo infrastructure and requires the existing setup secrets.
The sidecar has no host port and an internal network. Its read-only mounts are
application-owned assets, excluded from the generic wheel. Startup checks the
configuration digest; a missing token/pin or unsupported mode fails closed.
Never change a digest to disguise unreviewed prompt/model changes.

For direct Python development, set the same separate credential, explicit
`HDPI_PROFILE_ROOT`, `HDPI_ASSISTANCE_CONFIG` and its digest, then run
`python -m hdpi.assistance_http`. The default bind is loopback and port 8001.
This executable intentionally offers only explicit reference composition.

## Frontend and HTTP behavior

Run the existing frontend at localhost:5173. It uses Authorization Code + PKCE
with the existing `dx-web` client and audience mapper from FE #38/#39. Tokens stay
in the team's existing memory-backed session and travel through its shared API client. Authority/client
overrides are `VITE_OIDC_AUTHORITY` and `VITE_OIDC_CLIENT_ID`. No backend service
credential belongs in a Vite variable. The existing menu and ticket-list scaffold
are unchanged. The assistance deep link for an existing authorized ticket is
`/servicedesk/workspaces/:workspace/tickets/:ticketId`.

POST the public endpoint with only `{"question":"..."}`. Maximum question
length is 2000 characters and body length 16 KiB. Unknown fields, duplicate keys,
trailing JSON and invalid control characters are rejected. `SUCCESS` always
needs human review. `INSUFFICIENT_EVIDENCE` has no answer/citations. Private
timeout/unavailability becomes HTTP 504/503; malformed output or identity drift
becomes 502. Changed provenance/rights becomes 409; denied/removed resources
follow Core visibility errors. No generated answer is returned on these errors.

Transport has a 1-second connect and 15-second total deadline, a 256 KiB request
and 128 KiB response budget, four Java slots and sixteen private server slots.
At most ten source documents / 32 KiB combined UTF-8 content and three selected
quotations are allowed. There are no proxies, redirects, retries or cloud/model
fallbacks. These engineering bounds are not production capacity/SLO evidence.

## Rollback and incident checks

Set `SERVICE_DESK_ASSISTANCE_ENABLED=false` and recreate the backend so its
conditional route is removed. Stop `intelligence-assistance` separately. Keep
Core data, AI02 triage and workflow services. Do not remove volumes or restore a
previously revoked catalog/model. Re-run ticket/AI02 acceptance and Core health.

Incident records should include commit, configuration/model/corpus digests and
fixed failure category, without question, source content, credentials or UUIDs.
Keep local Java diagnostics private. CI uploads only content-free summaries;
browser traces, screenshots and videos are disabled for these contract controls.

## Verification

```text
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python -m pytest intelligence/tests/generation -q
python scripts/intelligence/run-generation-evaluation.py --output .local/ai06/source.json
python scripts/intelligence/verify-generation-evidence.py .local/ai06/source.json
python scripts/acceptance/servicedesk-assistance.py --output .local/ai06-acceptance-new
```

The acceptance script requires Docker, pinned Python dependencies and Java 17+
with Maven. It creates its own random disposable PostgreSQL container, uses real
Core/file APIs and two actual private Python HTTP servers, then removes only
that container. It never touches an existing database or Compose project.
Use a new output directory. On Windows, Maven can be selected with `--maven`.
Full Core file tests need Linux POSIX permissions/symlink support; a narrower
Windows `--tests TicketAssistancePythonE2E,TicketTriagePythonE2E` run proves only
the eight named integration cases and does not replace the full Linux suite.

From `frontend`, run `npm ci`, `npm run build`, `npm run lint`, `npm test` and
`npm run test:e2e`. Provision the pinned Playwright Chromium if absent. Browser
controls stub the auth boundary and public API, so they must not be
reported as full Keycloak/APISIX/Core/Java/Python/model acceptance.
