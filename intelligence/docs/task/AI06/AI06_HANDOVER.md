# AI06 foundation handover

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
and compares safe generation evidence. Remote CI has not run for these local
commits.

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
