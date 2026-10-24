# AI06 continuation status

Date: 2026-10-24. Branch: `feature/ai06-02-governed-real-model-provider`.
Foundation: AI06-02 at `c3b9f43`; FE #38/#39 at `origin/develop@8137a4a` integrated
by local merge `0316cd3`. No rebase/force push, Core migration or AI05 promotion.

Decision: **engineering continuation delivered; official AI06 closure BLOCKED**.
Do not equate reference integration or CI engineering PASS with a release GO.

## Delivered scope

- AI06-03: 52 AI-authored synthetic DEV controls, 13 categories, generic metrics,
  current-byte/source-revision evidence verification and source/wheel/OS CI
  wiring. Every human review/rights decision remains PENDING. Official mode
  writes a blocked receipt and exits 2 before case/model execution.
- AI06-04: public HPDI assistance capability, bounded dedicated Java/Python HTTP
  transport, request-local active TF-IDF retrieval and unchanged exact-quotation
  generation. Application-selected source pins are resolved through HPDI Data
  and rechecked against Core after inference; ticket visibility is read again.
- Service Desk endpoint accepts only a bounded question. Default OFF; activation
  supports only explicitly pinned `synthetic-reference` / `synthetic-test-only`
  inputs. Unavailable, timeout, malformed, changed and denied states suppress
  answers and preserve manual workflow authority.
- An app-owned assistance panel/deep link uses the auth, shared API client,
  workspace hooks and page shell from FE #39. AI06 adds no replacement auth,
  layout or ticket-management feature. Safe display citations contain title,
  version and local alias; no internal source UUID or URL.
- Optional private reference Compose overlay, rollback/runbook and disposable
  actual Core/PostgreSQL/Java/Python acceptance tooling. Real provider code from
  AI06-02 remains available and unchanged; there is no implicit model fallback.

## Evidence and practical limits

Local artifacts are ignored under `.local/ai06-completion/`. They contain
content-free summaries; private diagnostic logs must not be uploaded.

Before FE #39 integration, the broad source run recorded 859 passed / 6 skipped;
Linux Java recorded 202 tests, zero failures/errors/skips, and the dedicated
actual Core/Python + AI02 HTTP suites recorded 8 passing tests. Windows full Core
file tests cannot prove POSIX permissions/symlinks; their initial three errors
are retained in `core-python-01/`, and the Linux run covers those tests. The
Windows PowerShell wrapper also surfaced a native JVM stderr warning as exit 1
despite Maven success and 202 passing JUnit results; the XML assertions are the
test evidence. These records are not substituted for current-head verification.

Post-integration checks:

| Check | Observed result |
| --- | --- |
| Installed wheel and isolation smoke | PASS; external application assets, packaged shared schemas |
| Full current source Python suite | 861 passed, 6 explicit optional-model skips; `final-source-tests.xml` |
| Full installed-wheel Python suite | 861 passed, 6 explicit optional-model skips; `full-wheel-tests.xml` |
| Linux Java + actual PostgreSQL regression | 212 passed, zero errors/failures/skips; Maven verify and wrapper exit 0 |
| Windows scoped Core/Java/Python + AI02 regression | 120 passed, zero errors/failures/skips; `post-fe39-core-python/summary.json` |
| Source/wheel 52-case DEV summary verification | PASS; identities and current execution agree; official decision BLOCKED |
| FE lint and unit tests on the existing #39 shell | PASS; 28 unit tests |
| Chromium assistance contract controls | 11 passed; stub authentication and public API |
| FE production build/typecheck | BLOCKED by existing #39 `UploadKnowledgeDialog.tsx` resolver input/output types (`File` vs `File \| null`); left unchanged to keep AI06 scope |

Six Python skips comprise five optional Dense-model cases and the explicitly
unprovisioned real generation model. They are not treated as verified model
behavior. Full current source suite and final-HEAD identity receipts are recorded
in `.local/ai06-completion/`; HEAD-bound reports must be regenerated after any
commit, even when outcomes are unchanged.

Full Keycloak/APISIX/browser/Java/Python/**real model** acceptance is NOT_VERIFIED.
The browser controls stub authentication and public HTTP, and cannot replace it.

Known reference result: eight malicious exact selections remain visible as
content-safety failures. Technical quotation integrity does not imply safe,
relevant or reviewed guidance. No invented semantic accuracy or model SLO.

## Remaining external and production gates

| Gate | Current state / owner |
| --- | --- |
| Exact real weights/tokenizer/quantization/serving image and receipt pins | NOT_PROVISIONED; Platform/Security |
| Actual tokenizer/chat-format adapter and serving-engine wrapper | Requires approved model/deployment; Platform/AIE |
| Real-model adversarial, quality and hardware/resource measurements | NOT_RUN; AIE/QA |
| Genuine reviewed cases, adjudication, rights and thresholds | PENDING; PO/Data Owner/Security |
| Signed HOLDOUT lock and owner-managed attempt ledger | NOT_AUTHORIZED; independent reviewer |
| Production revocation propagation and residual TOCTOU review | PENDING; Core/Security |
| Full gateway/Keycloak/real-model acceptance and owner GO | NOT_VERIFIED; Application/Platform/PO |
| Hosted current-head CI and independent PR reviews | Required before merge/release; no hosted PASS claimed locally |
| G9 security/licensing/AI05 ownership obligations | Remain BLOCKED; authorized owners |

The historic GitHub billing incident is documented in the AI06-02 handover.
Its current remote remediation state has not been re-audited in this local run.
No CI check is disabled, downgraded or used to mint human approval.

See [evaluation design](AI06_03_EVALUATION_DESIGN.md),
[integration ADR](AI06_04_INTEGRATION_ADR.md) and
[operations/reproduction](AI06_04_OPERATIONS.md).
