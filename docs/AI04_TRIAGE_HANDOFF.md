# AI-04 private triage HTTP adapter

## Baselines and scope

Verified remote heads on 2026-10-18:

| Ref | SHA |
| --- | --- |
| origin/develop (implementation base) | e39356cc44ef50421a9b81199756ec9784fe161a |
| PR #20, feat/servicedesk-triage-bridge | 0050da1f4b840bde53b8b68f6e4dd74da50f960c |
| PR #19, feature/ai03-knowledge-retrieval | c16598dd032b962b79a9100c80c61b79419c4749 |
| refactor/dxos-three-tier-boundary (reference only) | cdca19f627b64acc37f003fbd73d063c9f6d703d |

BE-AI-TECH-0.1 in PR #20 is unchanged. G0-B remains PENDING; the executable
service-desk@0.2.0 business profile remains PROPOSED. This does not approve release.
No Core, Java production code, shared schema, profile, AI-02 engine or AI-03 retrieval
implementation changes. The refactor's custom /v1/triage envelope is not adopted.

## Transport and trust

Only `POST /internal/intelligence/v1/triage`, with `Authorization: Bearer` and the
same `INTELLIGENCE_TRIAGE_TOKEN` injected into Java and Python. No default credential.
Use a cryptographically random secret of at least 32 bytes, never a browser JWT.
Credential comparison uses `hmac.compare_digest`; authenticate before reading body.
No public gateway route, CORS, token endpoint, access/content logging or state writes.

Wire request is exactly shared logical schema 0.1.0, without a trustedContext wrapper.
Wire profile is `service-desk`; Python pins version `0.2.0`, preloads the registry,
constructs adapter-owned TrustedContext, and calls existing TriageService/RulesEngine.
Workspace is an assertion of the authenticated Java service, NOT independent Python
workspace authorization. Java must first authorize the persisted Ticket. Python will
correlate a valid authenticated workspace assertion, not infer browser permissions.

Responses are unchanged shared envelopes, validated again for schema, literal evidence,
correlation and pinned runtime. SUCCESS and library-generated EngineUnavailable DEGRADED
are HTTP 200. Transport/auth/schema/configuration/programming failures are never DEGRADED.

| Python HTTP | Meaning | PR #20 public Java mapping |
| --- | --- | --- |
| 200 | Valid SUCCESS or engine-generated DEGRADED | 200, after Java schema validation |
| 401 / 403 | Invalid service credential / rejected trusted context | 502 integration failure |
| 400 / 404 / 405 / 413 | Invalid JSON/schema/framing/profile/path/method/size | 502 invalid upstream interaction |
| 408 | Body read deadline | 502 if received before Java deadline |
| 503 | Profile/configuration/operational service failure | 503 unavailable |
| 500 | Unexpected failure or invalid engine output | 503 unavailable |
| No response / refused connection | Unavailable / Java 4s deadline | 503 / 504 |

The stdlib server is restricted to a trusted private demo network, not an Internet edge.
It reads at most the declared 32 KiB body, rejects ambiguous lengths/transfer encoding,
requires UTF-8 application/json, rejects duplicate keys/non-finite JSON, and closes every
connection. A socket timeout plus an absolute 3s timer bounds even slow-drip headers/body.
At most 16 active handlers run; excess connections are closed. Timers interrupt socket I/O,
not Python engine computation; the current deterministic RulesEngine is bounded by input
size. A future blocking engine requires a cancellable worker/process before deployment.
Responses are capped at 256 KiB. Exceptions and raw inputs never enter HTTP error payloads.
Use TLS for cross-host traffic; a private Docker network alone is not cross-host encryption.

## Run from clean source (Python 3.12)

```sh
python3.12 -m venv .local/ai04-venv
.local/ai04-venv/bin/pip install -r intelligence/requirements.lock
.local/ai04-venv/bin/pip install --no-build-isolation --no-deps ./intelligence
# Inject INTELLIGENCE_TRIAGE_TOKEN through your secret mechanism first.
.local/ai04-venv/bin/python -m hdpi.http_service --host 127.0.0.1 --port 8000
```

Packaged schemas/profiles are loaded from the installed wheel, never a nonexistent apps/
directory. Missing credential or invalid profile configuration fails startup without details.
Windows uses the corresponding virtual environment Scripts executables.

## Optional Docker Compose

First integrate PR #20 through normal review: this Python PR alone does not install the
Java bridge. Retain existing setup-generated PostgreSQL/Keycloak settings. Set the secret
and `INTELLIGENCE_TRIAGE_BASE_URL=http://intelligence:8000` in deployment configuration.

```sh
docker compose -f compose.yaml -f compose.triage.yaml --profile intelligence up -d --build
```

Python has no host port and no public APISIX route. The overlay injects the same secret
into both containers; avoid publishing `docker compose config` output containing secrets.
The Python-specific Docker ignore whitelist excludes local environments and secrets even
when combined with PR #20's backend-specific root ignore file. The image builds/installs
the existing package, runs non-root, read-only, without Linux capabilities, with resource caps.
The socket healthcheck indicates listener readiness, not independent credential validation.
AI is optional: omit the profile and leave Java's base URL empty to disable it; no hard
depends_on relationship blocks Core/Ticket/Workflow. Base compose.yaml is unchanged.

## Repeatable real Java-Python E2E

Provision a NEW disposable PostgreSQL 16 database `file_storage`, user `workflow`.
Never use an existing user's, production, or other worktree's database. Run the real
Python container/service with an ephemeral token, then set all four environment variables:
`FILE_TEST_JDBC_URL`, `FILE_TEST_DB_PASSWORD`, `INTELLIGENCE_TRIAGE_BASE_URL`,
`INTELLIGENCE_TRIAGE_TOKEN`. The base URL must be reachable from the Maven host.

```sh
git worktree add --detach .local/be01 0050da1f4b840bde53b8b68f6e4dd74da50f960c
python scripts/intelligence/run-real-triage-e2e.py --java-root .local/be01
```

The harness refuses a dirty or different Java revision. It temporarily copies only a new
test into that disposable checkout and removes that test afterwards. Production Java and
the original TicketTriagePythonE2E are unmodified. The success run includes the original
`mvn -B -f backend/pom.xml -Dtest=TicketTriagePythonE2E test` case plus an extended case.
Wrong-token invokes the real Python service. Unavailable uses a reserved non-listening
loopback socket. Timeout and malformed cases use a loopback-only fault proxy AFTER one
verified real Python SUCCESS; they are labeled fault-injected, not natural Python faults.
Every extended case checks requester/cross-workspace denial, unchanged Ticket/workflow/
audit row counts, unchanged Ticket data, and successful manual task claim/completion.

Workflow `intelligence-triage-e2e.yml` builds the clean image and uses disposable PostgreSQL.
It records exact Python/Java heads and scenario results in `ai04-real-triage-summary`.
Only non-secret summary JSON is uploaded; local logs are not delivery artifacts.
MockMvc JWT fixtures exercise Java authorization, not actual Keycloak login or a browser.
Do not claim FULL KEYCLOAK/GATEWAY/BROWSER E2E from these results.

## AI-03 compatibility

The current runtime package path is `hdpi` (renamed during BND-00 recovery).
PR #19 originally retained `smart_service_intelligence`, TriageService, resources,
registry and rules APIs. That bridge change added one module without renames, dependency changes
or edits to AI-03-owned retrieval/corpus/citation files. Requirements and pyproject remain
untouched; no Retrieval HTTP or Authorized Corpus Facade is introduced. A disposable
combined checkout must still run the entire AI-03 suite before integrating both PRs.

## Evidence status

Local targeted Python HTTP tests: 57 PASS on CPython 3.12.3 (WSL).
Full Python regression, installed-wheel validation, real E2E and combined AI-03 regression:
record exact run results in the PR after verification. Local Docker daemon unavailable;
do not reinterpret local unit tests as Docker or real Java-Python PASS.
