# AI06-02 implementation handover

Date: 2026-10-23. Scope: governed local evidence-selection engineering.
Release decision: **NOT_AUTHORIZED**. Real-model validation: **BLOCKED**.
Architecture, Privacy, Security, Data Owner and Platform sign-offs remain pending.

## Baseline and branch

- Branch: `feature/ai06-02-governed-real-model-provider`.
- Fetched base: `origin/develop@d12c3498ccf46e1ea14e9bab5d09924f7b0228b4`, PR #36.
- Existing worktree was clean when the branch was created.
- The existing deterministic provider and exact-quotation validator are unchanged.
- AI05 current-identities gates, retrieval runtime, qrels and HOLDOUT are unchanged.

## Delivered engineering

`PinnedLocalSelectionProvider` implements the existing provider protocol with
explicit, disabled-by-default construction. It verifies local model inventory,
receipt references, tokenizer identity and sidecar identity before use. Its
runtime identity binds revision plus the canonical full manifest digest.

Selection decoding accepts only exact SELECT/REFUSE fields and unique supplied
aliases. Trusted mapping produces exact excerpt claims in evidence order. JSON
duplicates, deep nesting, invalid UTF-8, floats, free prose, tool fields,
identity drift and incomplete/filtered output fail closed. The unchanged
grounding gate still controls service success.

The concrete loopback HTTP client disables proxies, redirects, retries,
streaming and ambient credentials. It enforces request/response bytes and
connect/read/overall deadlines, including the Linux EOF-on-watchdog-close case.
Provider guards add exact offline token-count, context, output, rate and
concurrency bounds. Malformed HTTP is a contract failure; genuine disconnects
are unavailability. Trace sink exceptions cannot replace results or safety
errors and are not logged.

The 40-case fake pack reports all categories and failures without raw content.
Eight scripted malicious-source selections are explicitly counted as unsafe
quotations, not reported as proof of content safety. Both matching stale reports
and type-altered counters fail current-identity verification. The real-mode
command writes a blocked readiness receipt and exits 2 without opening a socket.

CI adds source/wheel and Windows/Linux selector evidence checks to the existing
workflow. It retains seven-day content-free JSON artifacts. Real weights,
application policy and synthetic fixtures remain outside the generic wheel.

## Verification record

Local artifacts are ignored under `.local/ai06-02/`; no prompts, raw model
completions, weights or private material are committed.

| Check | Recorded outcome |
| --- | --- |
| Baseline generation suite | 66 passed |
| Windows full installed-wheel suite | 820 passed, 6 skipped; `.local/ai06-02/wheel-tests.xml` |
| Windows final generation source | 183 passed, 1 explicit real-model skip |
| Windows final generation wheel | 183 passed, 1 explicit real-model skip; final cross-OS wheel |
| Windows broad source regression | 821 passed, 6 skipped; `.local/ai06-02/final-source-tests.xml` |
| Linux source / wheel contracts | Each 183 passed, 1 explicit real-model skip; `.local/ai06-02/linux-verified-01/` |
| Tier boundary scanner | PASS, no debt exceptions added |
| Boundary mutation proofs | 39 passed |
| Immutable profile / foundation validation | PASS, including 145 negative foundation cases |
| Existing five-case generation oracle | Source/wheel PASS and equivalent |
| Forty-case provider contracts | PASS in fake mode; no socket creation |
| Retrieval, conformance and security evaluation | Source/wheel PASS with identical evidence and fingerprints |
| New Python code formatting | Black check PASS, 15 files; explicit spacing between logical groups |
| Model provisioning and real-model quality | REAL_MODEL_VALIDATION_BLOCKED |
| Production / public integration | NOT_AUTHORIZED |

Six skips in the broad Windows suite comprise five optional Dense-model cases
and one explicitly marked generation-model case. Optional generation validation
is never counted as completed. Setting `HDPI_REQUIRE_GENERATION_MODEL=1` makes
the missing deployment an explicit failure instead of a hidden skip.

Broad regression runs preceded the four additional malformed-HTTP/disconnect
cases. The final 183-case generation matrix includes these cases and the final
Linux EOF timeout fix on both operating systems, for source and installed wheel.
Linux used CPython 3.12.15 and Windows CPython 3.12.7, both within the pinned 3.12
minor line with the locked native runtime dependencies. Generic wheel inspection
confirmed no application policy resources, model weights or synthetic fixtures.

The final report identity fields also record the observed provider, model,
revision/manifest, configuration, envelope, application policy and output schema.
After adding these explicit identities, evidence and opt-in tests were rerun:
10 passed and 1 explicit real-model skip for both Windows source and wheel.
Regenerate reports after the last commit into
`.local/ai06-02/post-commit-20261023/{windows,linux}/`; use the strict verifier
against the final HEAD, including cross-OS comparison. A report from an earlier
commit is deliberately rejected even when all outcomes match.

The first broad source attempt exposed an unread-request-body race in the
loopback test server; its handler now consumes the request before responding.
The initial minimal Linux image also lacks git and subprocess-visible pytest,
so its broad inherited suite is not a claimed PASS. The generation-specific
Linux matrix uses its native runtime libraries and pure Python test tooling,
with the checkout mounted read-only and `network_mode: none`.

## Develop CI incident: account billing, not code

At the pinned base, [Intelligence runtime run 39967606752](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39967606752)
has successful source/wheel jobs on both operating systems, Dense jobs, data
quality and Core integration. The `evaluation-equivalence` and
`dense-equivalence` jobs failed before runner assignment, with no executed
steps. A failed-jobs rerun reproduced the same startup failure (attempt 2).

The repository operator supplied the annotation: recent account payments failed
or the spending limit needs to be increased. **Required remediation:** the
GitHub organization/account billing owner must fix Billing & plans, then rerun
the failed jobs. Code changes cannot resolve the account restriction; no gate
is disabled or weakened to turn this into a false PASS.

[Tier boundaries run 39967606767](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39967606767)
and [Core acceptance run 39967606693](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39967606693)
completed successfully at the base. This branch's hosted CI has not run locally;
the billing blocker must be cleared before hosted verification can be claimed.

## Reproduction commands

Use a fresh local run directory, the pinned Python environment and an installed
wheel in an isolated environment. CI executes these on Ubuntu and Windows.

```text
python -m pytest intelligence/tests/generation -q
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python scripts/intelligence/validate-executable-profiles.py
python scripts/intelligence/ai06-provider-evidence.py --mode fake --output .local/ai06-02/<fresh-run>/source.json
python -m build --no-isolation --outdir .local/ai06-02/<fresh-run>/dist intelligence
python scripts/intelligence/install-built-wheel.py --dist .local/ai06-02/<fresh-run>/dist
python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests/generation -q
python -I scripts/intelligence/ai06-provider-evidence.py --installed --mode fake --output .local/ai06-02/<fresh-run>/wheel.json
python -I scripts/intelligence/verify-ai06-provider-evidence.py --installed .local/ai06-02/<fresh-run>/source.json .local/ai06-02/<fresh-run>/wheel.json
```

The verifier binds the actual git HEAD. A minimal offline container without git
must receive `AI06_SOURCE_REVISION` from a trusted launcher that verified the
checkout on the host. When git is available, a conflicting declaration fails.
Do not compare reports across commits or source changes; regenerate them.

`verify-generation-model.py --manifest <approved.json> --directory <offline-root>
--offline` verifies the enumerated bytes without network access. No approved
model manifest exists yet. JSON schema validation alone does not authenticate
review signatures, verify a running serving image or prove network isolation.

## Remaining gates and ownership

| Gate | State | Owner |
| --- | --- | --- |
| Account billing and hosted CI rerun | BLOCKED | GitHub account / organization billing owner |
| ADR and code review | PENDING | Architect / AIE / QA |
| Exact real revision, artifact and serving-image pins | NOT_PROVISIONED | Platform / Security |
| Authentic license / notice and data rights receipts | PENDING | Legal / Security / Data Owner |
| Privacy, logging, retention and resource/network controls | PENDING | Privacy / Platform |
| Actual tokenizer adapter and serving-engine protocol wrapper | NOT_IMPLEMENTED | Platform / AIE |
| Approved real-run adapter, three repeats and resource measurements | NOT_RUN | AIE / QA |
| AI06-03 relevance/refusal/conflict reviewed labels | DEFERRED | AIE / Data Owner / QA |
| AI06-04 public integration and final Core freshness | DEFERRED | Application / Core / release owner |

Engineering can be reviewed as a dormant generic mechanism. This does not close
AI06-02 real-model validation, AI06-03 benchmarking, AI06-04 production approval
or any outstanding AI05 ownership/HOLDOUT decisions.
