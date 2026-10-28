# Persisted background ticket assessment

## API and UI

- `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets` creates the ticket,
  Flowable process, audit event, assessment row and QUEUED history in one transaction.
  Its response contains `assessment.status=PENDING`; it never waits for Python.
- `GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}` reads stored
  assessment only. Existing ticket visibility applies: workspace membership and
  requester/agent/coordinator roles; requesters cannot read another creator's ticket.
- Poll GET every 2 seconds while PENDING, back off on transport errors and stop on
  READY/DEGRADED, navigation, or loss of authorization. Polling does not enqueue work.
  No push notification or manual retry endpoint exists.
- Show PENDING as "Suggestions pending" without blocking manual review. A retry can
  still be PENDING with `errorCode` set. Show DEGRADED as "Suggestions unavailable;
  continue manual review". Never render internal exceptions or upstream responses.
- `assessment=null` is valid for legacy tickets without an assessment row.

## Schema and provenance

The live schema is `contracts/openapi/servicedesk.yaml`, `StoredAssessment` and
`StoredAssessmentResult`. GET response examples cover all three lifecycle states.

`assessment` has `status`, storage `version=1`, nullable `result`, nullable
`errorCode`. READY has a result; PENDING and DEGRADED never do. Metadata:
`contractVersion=0.1.0`, `profileVersion=service-desk@0.3.0`, `engine=rules`,
`rulesVersion=0.3.0`, `modelVersion=null`. Storage version is not model version.

Supported suggestions: category (NETWORK/SOFTWARE/HARDWARE/ACCOUNT/OTHER),
priority (LOW/MEDIUM/HIGH/CRITICAL), nullable impact (I1-I4), nullable urgency
(U1-U4), reasonCode, exact input `evidence[{field,text}]`, rendered
`explanation{classificationReason,priorityReason}`, `needsReview`, `nextQuestions`.
Evidence is from the persisted title/description, not retrieved documents.

`INSUFFICIENT_INFORMATION` is the rules contract's abstention-equivalent: OTHER,
MEDIUM, null impact/urgency, needsReview=true. Do not treat that fallback priority
as a reliable recommendation. `nextQuestions` are fixed application follow-up
prompts, not model output. ROOT_CAUSE_CONFLICT also requires review.

No confidence, probability, literal ABSTAIN state, risk score, category hierarchy,
model inference, retrieved citations, suggested assignee or autonomous approval
is supported by this persisted feature. Do not conflate it with the separate,
disabled-by-default `/assistant/assessment` or AI07 `/assistant` request APIs.

## Human decision

Display AI suggestions separately from `classification` (the coordinator decision).
Offer an explicit "Use suggestion" action to populate category/priority/impact/urgency
in the review form; do not overwrite edits when a poll completes. Require the
existing human review/assignee/reason controls. Submit the existing
`POST .../tickets/{id}/review` contract. The coordinator may override any suggestion.
The stored AI result never changes the workflow, priority, assignee or review row.

Safe error codes include AUTHORIZATION_REVOKED, INTELLIGENCE_INTEGRATION_FAILED,
INTELLIGENCE_CONTRACT_FAILED, INTELLIGENCE_UNAVAILABLE, INTELLIGENCE_TIMEOUT,
PROCESSING_FAILED and RETRY_EXHAUSTED. Use a generic localized message for unknown
codes. Authorization/contract/configuration failures are permanent; availability,
timeout and unexpected processing failures have at most three attempts with
5-second then 30-second delays. Terminal jobs require an operational investigation,
not browser retries. No feature-specific API is exposed to requeue a job.

## Deployment and identity

Run `scripts/setup.ps1` to generate BACKGROUND_CLIENT_SECRET alongside the existing
secrets. Never commit `.env`, generated realm files, access tokens or acceptance
compose files. Compose passes DX_BACKGROUND_TOKEN_URL, DX_BACKGROUND_CLIENT_SECRET
and SERVICE_DESK_BACKGROUND_WORKSPACES. Other deployments must supply the same
properties; the default worker allowlist is empty, so it claims nothing.

The imported realm provisions confidential client `dx-assessment-worker`, audience
dx-core, signed workspace groups and a service account with only intelligence:process
in lab-a's assessment-worker group. No lab-b membership, realm administration,
request:create or ticket review privileges are granted. Adding a workspace requires
both its Keycloak group grant/membership and the worker allowlist. Existing imported
realms are NOT updated by restart: apply the client/group/account additions through
the existing realm administration process; do not reset shared realms.

Every attempt fetches a fresh client-credentials token. The Backend's configured
JwtDecoder checks signature, issuer, audience and timestamps; JwtServiceIdentity
also checks azp, service-account username and expiry. No requester bearer is stored.
The persisted creator subject is checked separately for current request:create.
Service and requester membership/permission mappings are freshly loaded before
inference and before result commit, bypassing the directory's ordinary 60-second
cache. Directory failure fails closed; there is no offline/stale-grant fallback.
As with any external directory check, revocation after the final check is not an
atomic transaction with the application's database.

Rotate the client secret through Keycloak and the deployment secret store, then
restart instances to reload configuration. No refresh token or cached worker JWT
is used. A rejected credential/token is permanent AUTHORIZATION_REVOKED, not a
retryable Intelligence outage. Keycloak transport/server outages are retryable.
Use HTTPS outside the explicitly private local development network.

Backend-to-Python uses the independent INTELLIGENCE_TRIAGE_TOKEN over the private
network, never the requester JWT or Keycloak client secret. Python 401/403 maps to
INTELLIGENCE_INTEGRATION_FAILED and does not retry. No ticket text or bearer is
logged by the worker. Operational logs deliberately omit exception messages.

## Durable worker

V10 adds servicedesk_assessment and servicedesk_assessment_history. The assessment
row is the transactional outbox (ticket primary key prevents duplicate enqueue).
Claims use PostgreSQL FOR UPDATE SKIP LOCKED and a 30-second lease. Inference runs
outside the transaction. Completion is fenced by current lease token and unexpired
lease; READY audit and result commit atomically. A crashed worker may cause another
inference after lease expiry (at-least-once), but not duplicate/stale completion.
READY/DEGRADED never get claimed. GET never triggers recovery or inference.

## Real acceptance runner

`scripts/acceptance/background-assessment.py` creates a random Compose project,
random credentials and project-scoped PostgreSQL/file volumes under `.local/`.
It starts real Keycloak, Java with embedded Flowable, Python and Gateway; source
Python is mounted read-only, and the Java jar is rebuilt unless `--jar` is supplied.
Image overrides allow reuse of locally verified runtime/build images; their names
and the jar hash are recorded. No mocked AI transport or identity provider is used.
PostgreSQL and its volume remain the same through actual JVM restarts and SIGKILL.
The runner stops/removes only its containers and network and retains its volumes
and private evidence for inspection. Delete only volumes belonging to that random
project after inspection. Do not use shared database/volume cleanup commands.

Required checks include PENDING/READY restart, claimed-worker loss and actual lease
expiry, two JVMs, unavailable/paused Python, rejected Python/Keycloak credentials,
insufficient evidence, coordinator override and live group-permission revocation.
The run summary records PASS only after every assertion; failed runs remain ERROR.
See BACKGROUND_ASSESSMENT_ACCEPTANCE.md for actual results, not inferred readiness.
