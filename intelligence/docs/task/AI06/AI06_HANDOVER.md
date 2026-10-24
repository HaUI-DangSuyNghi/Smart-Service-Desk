# AI06 foundation handover

Continuation: [AI06 implementation status](AI06_COMPLETION_STATUS.md) covers the
AI06-03 DEV tooling and AI06-04 synthetic integration on the AI06-02 branch.
The original foundation evidence below remains historical.

Base: `origin/develop@828fbcf2273e3ed54e5d67e7a2594f1190f319cc`.
Branch: `feature/ai06-01-grounded-generation-foundation`.

Delivered: AI06-00 internal contracts and AI06-01 deterministic assistance.
The complete AI06 phase and production promotion remain open.

## Review units

1. `6651faf`: architecture, immutable knowledge/provider/claim contracts and
   contract tests.
2. `ae49b39`: deterministic provider, application policy, assistance service,
   refusal/degraded behavior, exact quotation grounding and safe traces.
3. Final verification commit: retrieval composition tests, repeatable synthetic
   evidence, source/wheel/OS CI comparison and this handover.

See [entry readiness](AI06_ENTRY_READINESS.md),
[foundation behavior](AI06_GENERATION_FOUNDATION.md) and
[architecture decision](../../architecture/grounded-generation-runtime.md).

## Local validation

Verified on 2026-10-21 with CPython 3.12.7 and the existing
`intelligence/requirements.lock`, installed into a dedicated local environment.
No dependency declaration or lockfile changed.

| Check | Result |
| --- | --- |
| Windows full source base suite | 685 passed, 5 real Dense tests deselected |
| Additional source retrieval-to-assistance tests | 5 passed |
| Complete AI06 source suite | 66 passed |
| Windows full installed-wheel base suite, isolated imports | 690 passed, 5 real Dense tests deselected |
| Existing retrieval/conformance/security evaluation gates | PASS for source and wheel; identical reports |
| Generation foundation source/wheel evidence | Identical on Windows and Linux |
| Generation foundation Windows/Linux evidence | Identical for source and installed wheel |
| Foundation/profile/schema validator | PASS, including 145 negative cases |
| Strict tier boundary scanner | PASS |
| Boundary mutation tests | 34 passed |
| Locked environment dependency consistency | PASS |
| Installed-wheel triage/retrieval/citation checks | PASS |
| Generic generation wheel contents | PASS; wheel size 87,956 bytes |
| Workflow YAML and diff whitespace | PASS |

The first full source run preceded the five retrieval composition tests; those
were subsequently executed separately and in the complete 66-test AI06 suite.
The full wheel run includes all 690 base tests. Dense model tests were explicitly
deselected, with no skipped base tests. No Dense model or real LLM was provisioned.

Linux validation used the existing `python:3.12.7-slim` image with networking
disabled. It covered the five-case generation evidence runner against source and
the installed wheel. The full Linux regression suite was not rerun locally.
The updated GitHub Actions matrix performs full source/wheel tests on both OSes
and compares safe generation evidence. Remote CI subsequently ran on `5af8a6a`
and failed; see the PR35 remediation record below. The earlier local results in
this section predate the strict semantic migration and do not qualify that HEAD.

Safe local artifacts are under `.local/ai06/` and `.local/ai06-linux/`:

- JUnit summaries for source, generation and installed-wheel tests;
- source/wheel generation evidence containing runtime identity, bundle digest,
  case IDs, reason/status codes, counts and citation aliases;
- inherited retrieval/conformance/security evaluation reports.

Evidence contains no raw question, document excerpt, prompt, completion,
credentials, access-scope hash or runtime source UUIDs. It is authored engineering
evidence and makes no reviewed benchmark or production quality claim.

## Remaining work

- AI06-02: governed real provider, pinned model/configuration, privacy/license
  review and actual model prompt-security tests.
- AI06-03: reviewed answerability, refusal, conflict and adversarial evaluation;
  paraphrase entailment must not bypass the exact quotation gate.
- AI06-04: application question/policy adapter, ticket authorization, final Core
  freshness verification, public response contract and user interface.

The current generic service trusts caller-verified citations and cannot establish
Core authorization or freshness. The synthetic Core composition tests prove
mechanics, not real Core acceptance. The deterministic provider quotes evidence;
it does not infer relevance, detect malicious content or resolve conflicting
sources. Human review defaults to enabled. There is no public endpoint, action,
workflow mutation, retrieval promotion or production generation activation.

## PR35 CI remediation, 2026-10-22

The reviewed `5af8a6a` failed
[Intelligence runtime](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39797716342),
[Tier boundaries](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39797716356)
and [integrated HEAD](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39797716281).
This remediation is committed locally; GitHub Actions must run again after push.
No green remote result on the new HEAD is claimed.

Confirmed fixes:

- Move the stale conformance test import to the Service Desk adapter and finish
  active v0.3 profile pins in contract, integration and mutation tests, including
  the inherited AI04 Java/Python E2E harness assertion.
- Keep the historical v0.1 authoring validator on normalization v0.1. Preserve
  v0.1/v0.2 profile bytes; repair only the draft PR's new active v0.3 resources.
- Supply Wi-Fi variants in Service Desk conflict rules. TICKET-010 retains its
  authored ACCOUNT root-cause result without restoring domain aliases in HDPI.
- Update the actual Service Desk Java caller to v0.3. The real Java/Python
  harness exposed a 502 because `TicketTriageService` still requested the now
  non-executable v0.2 profile; mocked transport tests had not exposed this.
- Correct Room Request v0.3's policy reference by supplying v0.3 rules beside its
  manifest. Previously it reused root v0.2 rules whose relative policy reference
  failed with `ProfileInvalid: Review policy reference mismatch`. Keep the root
  v0.2 profile non-executable; align both Compose startup and wire assertions.
- Add installed-wheel profile preflight, fixed startup error codes and bounded
  allowlisted CI diagnostics. Raw exception text and credentials stay private;
  runtime teardown tolerates an already exited process.
- Restore `evaluation/conformance-config.json` byte-for-byte from `828fbcf`.
  Its historical SHA-256 is
  `1c2617862f30cc7e2f3a7a6c2d2178444973da011c88f95522242816407aa2ad`.
  This was the specific nested `legacyEvidence` pin causing the AI05 checksum
  failure. Use a separate `conformance-config-v0.3.json` for active evaluation;
  no data pin, gold label, rights status or review status was rebased.
- Keep frozen corpus/section reports unchanged. Record separate normalization
  v0.2 reference candidates with exact ranking/fingerprint comparisons and an
  [explicit metric and provenance review note](../../../../fixtures/intelligence/service-desk/evaluation/corpus-experiments/NORMALIZATION_V02_CANDIDATE.md).
  The candidates remain proposed engineering evidence, with human review pending.

Local verification uses CPython 3.12.7, the existing base/Dense locks and an
isolated installed generic wheel. Linux runs in `python:3.12.7-slim`; the Dense
model is explicitly provisioned against its immutable revision, byte sizes and
SHA-256, then exercised offline. Safe outputs are under `.local/pr35/`.

| Check | Result |
| --- | --- |
| Windows source / installed wheel base suite | 699 passed each; 5 Dense tests run separately |
| Windows source / installed wheel real Dense suite | 5 passed each; no skipped model checks |
| Linux source base / real Dense suite | 699 + 5 passed |
| Linux installed wheel real Dense suite | 5 passed |
| Linux installed wheel base suite | 699 passed; 5 Dense tests run separately |
| Both OSes source/wheel retrieval, knowledge, section and generation evidence | Exact match |
| Both OSes source/wheel Dense/Hybrid and provisional matrix evidence | Exact match |
| Windows/Linux source and wheel retrieval, knowledge, section and generation evidence | Exact match |
| Windows/Linux wheel Dense/Hybrid and provisional matrix evidence | Exact match |
| Real three-tier Compose reuse | All 5 services healthy; Java/private Python triage, workflow, authorization and exact knowledge provenance passed |
| Room Request / HPDI HTTP wire contracts | 6 / 4 real JSON responses passed |
| Service Desk real Java/private Python success harnesses and bridge regression | 8 passed, no failures/errors/skips, fresh PostgreSQL database |
| Foundation, immutable history and tier boundary gates | 145 foundation negative cases; 39 boundary mutation tests; exact historical evidence bytes preserved |
| Dependency consistency, workflow syntax and generic wheel ownership | PASS |

Independent architecture/AIE/QA review and CI on the pushed remediation HEAD
remain required before merge. No Draft/Ready transition, merge or deployment is
performed. Trace-sink exception handling remains a separate AI06 hardening
recommendation; the assistance service is unchanged by these CI repairs.

## Strict semantic boundary hardening

AI06-01 closes a pre-existing Tier-2 semantic leak before later model work.
Generic `hdpi.normalization` no longer hard-codes Service Desk Wi-Fi aliases;
the `wi fi` variant is explicitly owned by the Service Desk rule profile and
the broader vocabulary remains in the Service Desk lexicon/projection.

Each Intelligence app now declares machine-readable semantic ownership through
`boundary-semantics.json`. The source gate discovers those contracts and rejects
app-owned literals in generic Python, regex strings and packaged generic prompt/
config resources. Generic generation also rejects default business-prompt
constants; prompt/policy text must enter through Tier-3-owned configuration such
as `GenerationPolicy`.

The strict debt baseline remains empty. See
`docs/architecture/intelligence-semantic-boundary.md`.
