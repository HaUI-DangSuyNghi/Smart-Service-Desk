# AI06 API-first completion status

Date: 2026-10-25. Branch: `feature/ai06-02-governed-real-model-provider`.
Existing PR: [#37](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/37).
FE #38/#39 from `develop@8137a4a` was already merged in `0316cd3`.
The follow-up conflict resolution integrates FE #40 at `develop@c61cdf9`.

Decision: **API-first engineering implemented; CLOUD DEV READY NOT_VERIFIED;
official release BLOCKED**. No Gemini/Groq keys or genuine cloud approvals were
provided. Mock responses never count as real API or quality evidence.

## Delivered

- Separate optional cloud wheel: native Gemini/Groq structured SELECT/REFUSE,
  strict envelope/alias/observed-model validation and unchanged exact quotation
  grounding. Generic hdpi has no vendor/secret dependency.
- Trusted Java selects one route before inference and binds catalog/route/approval
  digests, attempt UUID and all runtime fields through additive private v0.2
  schemas. Reference v0.1 is unchanged. No retry or same-request failover.
- Synthetic-only actor/resource/content/question/source allowlists, external
  expiring owner files and isolation attestation. Source rights alone cannot
  authorize cloud processing. Templates remain PENDING.
- Conservative single-instance DEV quota reservation, fixed error states,
  cooldown and next-request vendor selection. Credential/invalid output disables
  the route. No account cycling, paid escalation or template fallback.
- Fixed vendor HTTPS origins, bounded DNS/TLS/body/deadline/concurrency, explicit
  CONNECT relay and internal sidecar network. Credentials remain server-side.
- Core snapshots and final selected-source bytes/version/ACL/rights checks,
  exact Java runtime comparison and final ticket authorization remain intact.
- Content-free metrics and an opt-in real API smoke runner, separate from fake
  transport controls and full authorized cloud acceptance.
- `.env.example` uses the user's unprefixed names (`GEMINI_API_KEY`,
  `GROQ_API_KEY`, `PROVIDER_MODE`, etc.). Compose overlays are grouped under
  `infra/compose/`, with scripts/CI/docs updated.
- ADR-006, processing policy, templates, startup/rollback/incident runbook and
  source/separate-wheel CI wiring. Local model stays dormant; GPU/weights/tokenizer
  are not prerequisites for cloud DEV or approved-cloud production readiness.

## Verification and limits

Ignored artifacts: `.local/ai06-api-first/`. Share only sanitized summaries,
never private logs. Regenerate HEAD-bound receipts after the final commit.
Engineering tests do not grant release rights.

| Check | Observed local result |
| --- | --- |
| Full Python source at `8e500aa` (implementation unchanged by FE merge) | 915 passed, 6 explicit optional-model skips; `final-source-tests.xml` |
| Full installed generic + external cloud wheels at `8e500aa` | 915 passed, 6 skips; `final-wheel-tests.xml` |
| Cloud source / installed wheel | 54 / 54 passed, including private HTTP v0.2 with fake vendors |
| Linux installed cloud wheel in offline-staged container | 54 passed; no vendor network/API credentials |
| FE #40 integration build/typecheck / lint / unit / browser | PASS / PASS / 56 passed / 11 passed (2 browser workers) |
| Merged Linux Java/Core/PostgreSQL/private Python + AI02 | 243 passed, zero failures/errors/skips; `.local/ai06-pr37-merge/linux-core-python/summary.json` |
| Tier source / Java bytecode boundaries | PASS; scanner and Maven ArchUnit |
| Boundary mutation proofs | 39 passed |
| Foundation contracts | PASS; 8 schemas and 145 negative cases |
| Compose relocated overlays | Merged configuration validation PASS |
| Real Gemini/Groq success and legitimate model REFUSE | BLOCKED: keys, exact approved model/catalog and owner receipt missing |
| Full cloud Core/Keycloak/APISIX/browser flow | NOT_VERIFIED in an approved isolated environment |
| Hosted final-head CI | No PASS claimed; previous head jobs failed/cancelled without executed steps |

Six skips cover five explicitly unprovisioned Dense-model cases and one optional
local generation-model case. They are not cloud failures or real model evidence.
The first Linux acceptance wrapper rejected a non-allowlisted alias; the corrected
private alias run passed all 232 tests without weakening host validation. Docker
package-registry downloads timed out; an offline dependency staging build is
tracked separately from the normal network build. The derived staging build
passed using the same pinned dependencies supplied offline; it is not a normal
network build or a full approved deployment.

FE #40 from `develop@c61cdf9` supplies the current frontend baseline. Its upload
typing fix and all other FE functionality are kept.
The sole manual FE resolution is in `frontend/src/apps/servicedesk/index.ts`:
use the team's plain lazy imports and retain both ticket-list and AI06 ticket-detail
routes. No custom upload, page layout, permission or process UI edits were added.

The merged FE build/typecheck, lint, 56 unit tests and 11 browser contract controls
passed. Browser controls still stub authentication/public assistance and do not
prove the full cloud/SSO path. Merge acceptance artifacts are kept separately in
`.local/ai06-pr37-merge/`; earlier receipts remain historical and HEAD-bound
generation receipts must be regenerated after the merge commit.

## Remaining owner/environment gates

1. Obtain Gemini/Groq keys; verify exact models, strict JSON support, current
   account quotas, free entitlement and provider spending caps.
2. Data Owner/Security review hashes, actors, recipients, rights/privacy/terms/
   retention and isolation; issue protected external approval with expiry.
   Do not turn committed fixtures into approvals.
3. Run real success and model REFUSE for each vendor, then full authorized cloud
   acceptance. Direct Python smoke alone cannot establish CLOUD DEV READY.
4. Finish independent reviewed DEV labels, thresholds, adjudication, signed
   HOLDOUT/attempt ledger, revocation/TOCTOU review and owner release GO.
5. Verify hosted CI on the merged HEAD and obtain independent PR reviews. No
   checks were disabled/downgraded. Local merged FE build/typecheck now passes.

Cloudflare, OpenRouter/Cerebras, distributed budgets and optional local SLM/fine
tuning are deferred. No historical AI05 pins, sign-offs, Core migration or
official evaluation was changed. The 52 authored DEV controls and eight unsafe
exact-selection findings remain provisional and visible.

See [ADR-006](../../decisions/ADR-006-api-first-multi-provider.md),
[processing policy](../../governance/ai06-cloud-processing-policy.md),
[operator runbook](AI06_API_FIRST_OPERATIONS.md) and
[evaluation design](AI06_03_EVALUATION_DESIGN.md).

## Synthetic DEV v0.2 dataset expansion (2026-10-25)

The application-owned extension contains 400 cases across 30 categories and six
fixed axes: the 52 immutable v0.1 controls plus 348 new cases. Twelve invented
families, 36 facts and 60 templates drive deterministic generation with seed
20261024. Dataset SHA-256:
`cec8c8832183234996cad0654d256055cd4e624fbe9a3a0042f6ba176088f78a`.
The historical v0.1 raw SHA remains
`f3ba65dd318b2e06cce0e450faf7f2978916e8a6001c94270de38cb23fcb0c2e`.

New tooling and tests are app-owned. Shape/provenance/graph checks, content risk
linting, deduplication, current-byte replay, release immutability, actual generic
wheel inspection and hostile ownership mutations enforce the expansion boundary.
Generic hdpi and shared contracts are unchanged. No FE or Core source changed
in this expansion. Approved question/source digest inventory capacity increases
to 1,024 in the optional Python integration and Java catalog; actor/resource and
per-request bounds remain unchanged.

Observed offline reference result: **400 cases, 0 technical failures, 156
provisional behavior mismatches, 64 unsafe exact selections**. Source and
installed-wheel reports are byte-equivalent. The first 52 preserve 13 categories and eight unsafe
selections. Safety decision is **FAIL**, all 400 human reviews remain **PENDING**,
and provisional behavior mismatches remain visible. This is engineering evidence,
not a gold benchmark or proof of safe real-model behavior. All content rights and
official/HOLDOUT gates remain pending.

The stable cloud smoke selects 30 cases without using model scores. Optional
cloud orchestration requires exact external dataset/provider/model/scope approval,
existing route approval, keys and explicit budgets. Offline fake controls cover
quota/timeout, revocation, denied egress and partial denominators. No real Gemini
or Groq request was executed; both remain **NOT_VERIFIED**. The added CI workflow
requires ownership, Windows/Linux source/wheel checks and cross-OS replay.
Hosted execution on the eventual pushed HEAD is **NOT_VERIFIED**; local results
must not be represented as a hosted CI pass.

Local backend regression: **245 tests passed**, including two new inventory-bound
controls and actual private Python HTTP integration with disposable PostgreSQL.
Full Python regression passed in both source and installed-wheel modes:
**1,019 passed, 6 optional model tests skipped** in each mode. After isolating the
app fixture name from historical generation fixtures, the focused source suite
passed **104 tests** and the installed suite with historical quality controls
passed **111 tests**. Existing boundary scanner and 39 hostile boundary controls
passed, as did the actual generic-wheel ownership check and double regeneration.
The same **111 focused tests passed on Linux** with the fresh installed generic
and optional integration wheels; regeneration matched the committed canonical
bytes. Linux source/wheel reports and Windows reports are independently replayed
before accepting the final HEAD-bound receipts.
The receipt identifies a dirty working tree at the pre-commit revision; it is
test-run evidence, not a clean final-HEAD release attestation. Final dataset
receipts must be regenerated after commits under `.local/ai06-synthetic-v02/`.

See [dataset design](AI06_03_SYNTHETIC_V02_DATASET_DESIGN.md),
[handover and commands](AI06_03_SYNTHETIC_V02_HANDOVER.md) and
[ADR-007](../../decisions/ADR-007-app-owned-synthetic-dataset.md).
