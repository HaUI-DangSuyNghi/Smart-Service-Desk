# AI06 API-first completion status

Date: 2026-10-24. Branch: `feature/ai06-02-governed-real-model-provider`.
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
