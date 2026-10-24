# AI06 API-first completion status

Date: 2026-10-24. Branch: `feature/ai06-02-governed-real-model-provider`.
Existing PR: [#37](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/37).
FE #38/#39 from `develop@8137a4a` was already merged in `0316cd3`.

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
| Full Python source | 915 passed, 6 explicit optional-model skips; `final-source-tests.xml` |
| Full installed generic + external cloud wheels | 915 passed, 6 skips; `final-wheel-tests.xml` |
| Cloud source / installed wheel | 54 / 54 passed, including private HTTP v0.2 with fake vendors |
| Linux installed cloud wheel in offline-staged container | 54 passed; no vendor network/API credentials |
| Final untouched FE unit tests / typecheck | 28 passed; typecheck fails on the existing upload resolver |
| Linux Java/Core/PostgreSQL/private Python + AI02 | 232 passed, zero failures/errors/skips; `linux-core-python-02/summary.json` |
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

Per the user's latest scope, **frontend is unchanged from session baseline
`913ec5d`**. Temporary upload-resolver fixes were reverted in `25a9a33`; banner
and Playwright configuration edits were removed. Existing FE build has the known
upload resolver `File` / `File | null` type error and is left to the FE team.
Earlier 31-unit/11-browser results included temporary edits and are not final FE
acceptance. Existing auth/panel code remains in place.

Final fetch found `develop@c61cdf9` (FE #40). A read-only `git merge-tree`
simulation reports one conflict in `frontend/src/apps/servicedesk/index.ts`.
No merge or frontend resolution was applied, preserving the user's FE exclusion.
The FE owner must integrate that route registry before PR #37 can merge. This is
separate from the already-merged #38/#39 baseline and local AI06 backend tests.

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
5. Restore hosted CI execution and obtain independent PR reviews. No checks were
   disabled/downgraded. FE build remains an explicit user-scoped exception.

Cloudflare, OpenRouter/Cerebras, distributed budgets and optional local SLM/fine
tuning are deferred. No historical AI05 pins, sign-offs, Core migration or
official evaluation was changed. The 52 authored DEV controls and eight unsafe
exact-selection findings remain provisional and visible.

See [ADR-006](../../decisions/ADR-006-api-first-multi-provider.md),
[processing policy](../../governance/ai06-cloud-processing-policy.md),
[operator runbook](AI06_API_FIRST_OPERATIONS.md) and
[evaluation design](AI06_03_EVALUATION_DESIGN.md).
