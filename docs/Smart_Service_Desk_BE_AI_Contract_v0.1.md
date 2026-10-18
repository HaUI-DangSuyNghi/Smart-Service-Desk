# SMART SERVICE DESK — BE ↔ INTELLIGENCE TECHNICAL DESIGN

**Design revision:** `BE-AI-TECH-0.1`  
**Status:** **G0-A TECHNICAL APPROVAL RECORDED; G0-B BUSINESS APPROVAL PENDING**  
**Scope:** Triage-first integration; authorized retrieval is phase 2.  
**Reference baseline (verified during review):** `develop` after PR #18 (`e39356cc`); AI-03 PR #19 under review.  
**Authoritative existing schemas:** `contracts/intelligence/v1/{triage-request,triage-result,search-request,search-result}.schema.json` — logical contract `0.1.0`.  
**AI profile:** ID `service-desk`, resolved/pinned to `service-desk@0.2.0` on the server. The existing profile YAML was marked PROPOSED at review time; the service-desk/process owner must approve business policy.

> G0-A decisions 1-5 are approved according to the user's explicit confirmation of Backend + AI team agreement in the IMPLEMENT BE-01 instruction. This records that attestation, not independently collected signatures; no individual names or approval dates are inferred. G0-B business priority profile approval remains PENDING: no Process Owner approval evidence was supplied. Technical implementation may proceed; business/release approval is not implied. Retrieval remains a separately gated proposal.
>
> Approved triage transport: private `POST /internal/intelligence/v1/triage`, `Authorization: Bearer <internal-service-token>` from environment/secrets, no user JWT forwarding or public gateway route. Connect deadline 1 second; overall HTTP deadline 4 seconds including response body. Request cap 32 KiB; response cap 256 KiB. Public API accepts no body; any bytes, including `{}`, return 400. Return the unchanged logical response envelope. HTTP mapping: 200 validated SUCCESS/DEGRADED; 400 invalid public input; 401/403/404 existing user/Ticket policy; 502 invalid upstream contract or integration authentication/configuration; 503 unavailable (including upstream 429/5xx and connection failure); 504 deadline. No retries or state mutation.

## 1. Responsibilities and 3-tier placement

| Component | Responsibility | Logical layer | Owner |
| --- | --- | --- | --- |
| Keycloak, JWT/WorkspaceAccess, Metadata, File, Workflow, Audit | Shared authoritative infrastructure and policies; independent of tickets/AI | Tier 1 Open-Core | Backend |
| Shared H/P/D/I UI: workspace, generic inbox/history, reusable data presentation, generic recommendation display | Reusable user capabilities; no hard-coded Ticket domain | Tier 2 H-P-D-I | Frontend |
| Python rules engine, profile-driven triage, retrieval/index/ranking/citation capability | Reusable intelligence capability; domain profile is configuration | Tier 2 Intelligence capability | AI/Data |
| `TicketService`, Service Desk BPMN, Ticket statistics, Ticket triage orchestration, Ticket-specific UI | Service Desk business rules, ticket authorization and composition | Tier 3 Application | Backend/Frontend |
| Authorized corpus integration / bridge | Policy-enforcing integration boundary; keep application-specific eligibility outside generic Core | Between App, Core and I | Backend + AI/Data |

No extra Core microservice, separate Tier-2 website or generic platform rewrite is required for this design.

## 2. Phase 1 - Triage: G0-A technical contract approved

### 2.1 Sequence

1. Browser sends `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/triage` with the user JWT and no body. Any body bytes, including `{}`, are rejected with 400.
2. Java validates the bearer token via existing security and calls the existing `TicketService.get(workspace,id)` (or equivalent authorized application read). Requesters see own tickets; agent/coordinator see the workspace's tickets. Hide inaccessible ticket IDs under the existing 404 policy.
3. Java reads `title` and `description` from the persisted Ticket and creates a new correlation `requestId`. Choose profile ID `service-desk` and exact runtime version `service-desk@0.2.0` using trusted server configuration.
4. Java calls **proposed** internal Python endpoint `POST /internal/intelligence/v1/triage` using a service-only credential and the *unchanged* logical triage request body below. Do not forward the browser JWT. This endpoint must never be exposed through the public APISIX route.
5. Python authenticates the calling service **before** constructing `TrustedContext` from its authenticated middleware/configuration and the validated request's workspace correlation. JSON `context` is not itself an authentication credential. Execute the existing `TriageService` against the pinned profile, without duplicating RulesEngine.
6. Python returns the existing logical triage response, validated by `triage-result.schema.json` and correlation/evidence invariants.
7. Java validates the response, including `contractVersion`, `requestId`, `profile`, `workspaceId`, `runtime.profileVersion`, status/evidence shape. It sends a UI-safe response. No automatic workflow mutation, ticket priority update or recommendation persistence in phase 1.
8. The user may choose actions manually through existing workflow APIs. Ticket/manual flow must not depend on Intelligence availability.

### 2.2 Existing logical Python request — unchanged schema

```json
{
  "contractVersion": "0.1.0",
  "requestId": "2c6402a1-8be0-4a89-b638-6c4af8816dd5",
  "profile": "service-desk",
  "context": {"workspaceId": "lab-a"},
  "input": {
    "title": "Không đăng nhập được hệ thống",
    "description": "Không thể đăng nhập bằng tài khoản được cấp."
  }
}
```

The fields above are *illustrative*. They must be populated server-side after Java authorization. `profile` is the **ID** (`service-desk`) and is not the full version string. The Python adapter resolves `profile_version='0.2.0'` via trusted configuration; Java rejects responses whose `runtime.profileVersion` is not `service-desk@0.2.0`.

### 2.3 Existing logical Python response — unchanged schema

When `status=SUCCESS`, the current AI-02 schema requires:

- Correlation fields: `contractVersion`, `requestId`, `profile`, `workspaceId`.
- `result`: `category`, `priority`, `suggestedGroup` (profile-defined strings, not grants).
- `evidence`: list of `{type:"INPUT",field:"title"|"description",text:<literal substring from original input>}`.
- `explanation`: `matchedRules`, `impact`, `urgency`, `reasonCode`, `text`.
- `runtime`: `engine`, `profileVersion`, and `rulesVersion` (or model version for future engines).
- `needsReview` and `error:null`.

When `status=DEGRADED`, current AI-02 requires `result:null`, `evidence:[]`, `explanation:null`, `runtime:null`, `needsReview:true`, and `error.code=INTELLIGENCE_UNAVAILABLE`.

**Do not invent a `confidence` field** (rule engine has no calibrated confidence). Do not put retrieval citations into triage evidence: the current triage schema only permits literal `INPUT` evidence.

### 2.4 Approved triage transport and failure policy (G0-A)

- **Auth:** single dedicated secret for Backend → Python internal service, configured outside source/Git and compared safely; private Compose network. It is a demo baseline, not a production mTLS claim. For cross-host/network traffic, add TLS. Never forward a user JWT or mount a broadly privileged Keycloak credential in Python. Review whether the demo threat model warrants stricter service identity.
- **Availability:** 1 second connect, 4 seconds overall HTTP deadline including response body; no retries. Request limit 32 KiB, response limit 256 KiB. No request content, evidence, tokens or credentials in logs. Reject redirects.
- **User 401/403/404:** existing Java auth/Ticket policy, before any Python call.
- **Python `SUCCESS`:** return a validated recommendation for display; never write workflow state.
- **Python logical `DEGRADED`:** return a *clearly marked* unavailable recommendation to UI, with no synthetic result. Manual processing remains usable.
- **Transport:** timeout returns 504; upstream 429/5xx and connection failure return 503. FE shows AI unavailable and keeps manual workflow usable; never fabricate a prediction.
- **Python 401/403 (internal credential failure):** security/configuration failure; fail closed, alert on non-sensitive status metadata; **do not** translate to `DEGRADED SUCCESS`.
- **Bad/mismatched Python response/profile/evidence:** return 502 upstream contract failure. Internal Python 401/403 and invalid integration configuration return 502 integration failure, never user 401 or logical DEGRADED.
- **Database transactions:** finish authorized Ticket read before network I/O. No open DB transaction across remote call.
- **Persistence:** recommendation is transient in v0.1. If business later chooses to accept/override suggestions, define a separate reviewed command and audit/persistence contract.

## 3. Phase 2 — Knowledge Retrieval: ownership and direction only

### 3.1 Sequence (proposed)

Browser search → Java authenticates & determines workspace/application context → trusted corpus eligibility/catalog policy + Core metadata/file authority → backend grants a narrowly scoped execution → Python uses `AuthorizedCorpusSource` port via an agreed transport adapter → index is selected/built for the **exact authorized scope before scoring** → retrieval/ranking → Core-backed revalidation & citation verification → Java validates scope/correlations before presenting results.

**Retain AI-03 code:** `SearchService`, chunking, TF-IDF, index registry, citation verification, evaluation, and the current `CoreAuthorizedCorpusAdapter` (as baseline/test until a replacement is proven). No Core database access by Python.

### 3.2 Boundary proposal — **not yet approved**

Backend owns corpus selection and usage-rights eligibility; Core remains identity/file/metadata authorization authority. The previous agent review suggests a Backend-owned facade with 3 **proposed** operations:

- `GET /internal/intelligence/v1/corpus/manifest` — authorized catalog/scope manifest.
- `GET /internal/intelligence/v1/corpus/documents/{metadataId}/content` — bytes only for an authorized source in the execution.
- `POST /internal/intelligence/v1/corpus/revalidate` — check source identity/version/checksum and current access before citing.

The Backend may issue a short-lived request/service-bound opaque grant distinct from user JWT. The grant is **not** the scope fingerprint. All grant operations must revalidate identity/scope/source authorization as designed; no arbitrary document IDs, subject or roles accepted as authority. Do not expose these endpoints on the public Gateway.

**Review gate:** Backend and AI must approve transport, trusted-context construction, source catalog revision, grant TTL/deadline, error mapping, snapshot access/retention and invalidation. Do not build duplicate Python/Core authorization adapters in parallel.

### 3.3 Existing AI-03 logical Search wire schema

`contractVersion=0.1.0`; `requestId`, `profile`, `context.workspaceId`, `query`, `topK` (1–10). Results include `workspaceId`, Core `metadataId` UUID, `sourceVersion`, `sourceChecksum`, `chunkId`, `score`, and `excerpt`; score is a ranking value, **not confidence**. A response must match request context and carry exact profile/corpus/index versions. Unauthorized access is hard failure, not a fallback to broad search. No global index then post-filter.

**Data rights:** `usageRights` is metadata, not an automatically approved license/policy. Use rights-cleared synthetic/corpus-approved material only; do not ingest private ticket attachments by default. Core's current workspace-based read access is **not** per-document requester ACL. Document what the demo actually enforces.

**Revocation limitations:** Core checks signed JWT group claims; removal in Keycloak does not instantly invalidate an already-issued token. Backend and AI must disclose/review this limit. An opaque grant's short TTL alone does not fix it.

## 4. Responsibility matrix and branch plan

| Workstream | Backend | AI/Data | Frontend |
| --- | --- | --- | --- |
| Triage contract | Own Application endpoint, Ticket authorization, approve internal HTTP envelope | Own Python HTTP wrapper, exact logical schema/runtime, approve auth/error policy | Display `SUCCESS`, `DEGRADED`, failed requests |
| Triage implementation | `feat/servicedesk-triage-bridge` after sign-off | `feat/ai-triage-http-runtime` after sign-off | Add Ticket AI suggestion panel after endpoint available |
| Knowledge retrieval contract | Own data eligibility/authorization and corpus facade | Own `AuthorizedCorpusSource` transport and search endpoint; retain AI-03 | UI knowledge/citation source cards |
| Corpus implementation | `feat/core-intelligence-corpus-facade` *after* separate sign-off | `feat/ai-authorized-retrieval-http` *after* separate sign-off | No direct Python or internal corpus calls |
| PoF/release | Verify LICENSE/source/Java/Docker; app security and reproducible stack | Corpus rights, Python dependencies/tests | Browser E2E, screenshots, UX/demo |

## 5. Acceptance gates

**G0-A Technical:** APPROVED per user-attested BE + AI agreement recorded above for revision `BE-AI-TECH-0.1`. Decisions 1-5 are the approved triage scope.

**G0-B Business:** PENDING explicit Service Desk/Process Owner approval of priority profile; technical sign-off does not approve business priority/SLA policy.

**G1 Triage application:** Existing Ticket visibility applied before Python invocation. Test missing user token, own/foreign ticket, cross-workspace, agent/coordinator and missing Ticket. Responses validated against real AI-02 logical schemas; request ID/version/evidence must match.

**G2 Real E2E:** Java → actual Python HTTP service (not fake) → validated recommendation; separate tests for real timeout, auth mismatch and manual fallback. CI/worktree evidence linked. Avoid claiming browser E2E if no browser was used.

**G3 Retrieval:** Only after PR #19 review and corpus-contract approval. Verify unauthorized source has zero scoring, scope/index invalidation, stale citation rejection, data provenance and rights. Use real Core/Keycloak/file endpoints where possible.

**G4 Full demo & PoF:** Browser login → workspace → ticket creation → candidate inbox → claim/handle → AI suggestion → requester confirm → dashboard. Public repository only after source/data rights and secret review; root LICENSE, release/tag, clean checkout source build and stable demo instructions.

## 6. Decision record: triage approved; retrieval pending

1. **Triage route:** APPROVED `POST /internal/intelligence/v1/triage` inside private service.
2. **Service auth:** distinct internal token with no end-user JWT forwarding for triage; exact provisioning/deployment and error behavior approved by Backend+AI.
3. **Profile:** `profile="service-desk"`, resolved version `0.2.0`, response `runtime.profileVersion="service-desk@0.2.0"`; business policy still explicitly approved by owner.
4. **Java API:** `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/triage`; no arbitrary text/subject/role in request.
5. **Manual fallback:** operational outages affect only AI suggestion; do not mutate workflow or silently fabricate AI output.
6. **Retrieval ownership:** Backend-managed corpus eligibility/facade (tentative B); Python maintains AI-03 protocol/algorithms. Must separately approve facade contract, token/grant, topology and index lifecycle before implementing phase 2.

G0-A decisions 1-5 are approved for independent Backend and AI triage PRs. G0-B and phase-2 retrieval remain separately pending. Do not block Frontend H/P/D development on retrieval.
