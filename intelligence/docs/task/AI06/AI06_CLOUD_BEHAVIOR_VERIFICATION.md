# AI06 behavior investigation and real cloud verification

Date: 2026-10-25. Scope: AI06, no frontend changes. All measurements below are
synthetic DEV evidence. Independent labels, rights and official release remain
pending. Full 400-case cloud execution was not approved in this session.

## What the 156 mismatches mean

The immutable v0.2 reference report has 150 false successes and six false refusals.
The reference provider quotes every retrieved excerpt; it has no semantic
answerability or injection classifier. Those historical findings remain visible:
400 cases, 156 provisional mismatches and 64 unsafe exact selections. Neither
the oracle labels nor v0.1/v0.2 dataset bytes were changed to improve a score.

The six false refusals lost their evidence before selection because of lexical
matching. The new opt-in `source-balanced@0.1.0` policy offers bounded chunks from
each authorized source without a keyword gate. It preserves original bytes,
revalidates each citation against the request-local authorized resolver, offers
at most ten chunks and still permits at most three output quotations. Input
evidence capacity is now separate from output claim capacity. The default ranked
mode and historical reference behavior remain unchanged.

Java obtains the expected evidence mode from its pinned operator catalog through
trusted routing. A Python response cannot select its own retrieval mode. Java
continues to enforce exact generation identity, source-state digest, citation
bytes/version/rights and final authorization. Snapshot mode has a distinct engine,
configuration and index identity. This is not a change to Core or public FE input.

## Real two-case smoke

The user approved exactly the two supplied invented examples, once per vendor,
and later approved four additional attempts after compatibility fixes. Across
those batches there were eight inference attempts; previous failures are retained.
The final four-attempt batch verified both SUCCESS and legitimate model REFUSE on
both providers, each with HTTP 200 and a matching observed model identity.

| Provider/model | Final batch | Reported input/output tokens | Runtime latency |
| --- | --- | --- | --- |
| Gemini `gemini-3.5-flash-lite` | SUCCESS + REFUSE verified | 669 / 48 | 1,521 / 1,605 ms |
| Groq `openai/gpt-oss-20b` | SUCCESS + REFUSE verified | 1,046 / 345 | 1,159 / 1,284 ms |

Actual incompatibilities found and fixed:

- Gemini adds opaque `thoughtSignature` metadata beside the selected JSON text.
  The adapter permits a bounded string, ignores it and continues rejecting tools,
  thought parts, prose, unknown aliases and unknown model identities.
- Groq adds documented `usage_breakdown` metadata. GPT-OSS requests explicitly
  disable returned reasoning with `include_reasoning=false`; the selector and
  exact quotation decoder remain strict.
- Content-free per-attempt metrics now include HTTP status and a fixed adapter
  stage. Thread-local HTTP telemetry prevents concurrent requests from sharing
  diagnostic state. Error bodies, reasoning, keys and raw prompts are not logged.

These are direct Python/runtime API checks. They do not establish real
Core/Keycloak/APISIX/browser acceptance or official quality. User-confirmed free
account controls are recorded separately; vendor billing was not independently
reconciled. Local quota settings are budgets, not provider entitlements.

## Approved 30-case evaluation: partial, stopped on HTTP 429

The user chose 30 cases per provider, not the 400-case option. Both runs used the
same stable, score-independent one-per-category selection, policy v0.2 and a
minimum three-second interval between distinct cases. No retries or failover
occurred. Both providers returned HTTP 429 and the runners stopped.

| Measure | Gemini | Groq |
| --- | ---: | ---: |
| Planned cases | 30 | 30 |
| Started cases, including local no-evidence refusal and final 429 | 20 | 28 |
| Confirmed model responses | 18 | 26 |
| Not executed after stopping | 10 | 2 |
| Completed technical cases, including local no-evidence refusal | 19 | 27 |
| Reference mismatches on those same completed cases | 12 | 12 |
| Cloud mismatches on those same completed cases | 3 | 7 |
| Unsafe exact selections | 2 | 5 |

The planned-denominator reports retain technical failures and unexecuted cases:
Gemini has 11 technical failures and 14 provisional mismatches; Groq has three
technical failures and ten provisional mismatches. Do not compare these 30-case
figures directly with the full reference's 156/400. Both runs are
`PARTIAL_QUOTA_EXHAUSTED`; neither is full CLOUD DEV VERIFIED. The comparison
matrix checks operator receipt consistency, not remote model replay.

The completed behavior mismatches include an underspecified keyword-only baseline
query (`GEN-SD-0036`), an unsupported condition (`GEN-SD-0182`, Groq) and unsafe
source quotations. Provisional labels remain unchanged; ambiguous intent still
requires independent review.

## Additional v0.3 hardening, not yet tested against real cloud

Policy v0.3 adds a conservative app-owned source marker gate before generation.
It scans the entire bounded source, including text beyond selected chunks, using
Unicode normalization, case folding and whitespace normalization. A match
withholds the whole candidate set: removing only a bad source could conceal a
contradiction. Model generation is not invoked for that request.

Offline regression verifies that all 64 declared unsafe fixture cases are refused
before provider invocation, with no source changes or oracle access in runtime.
No unflagged fixture source matches these markers. This is finite marker coverage,
not proof against arbitrary prompt injection. Policy v0.3 also clarifies topic-only
queries and the difference between a requested condition/reason/code and an action.

The v0.2 policy and measured receipts are retained. Do not silently replace an
already approved catalog with v0.3 or rewrite old receipts. **v0.3 real-model
quality and resolution of all 156 mismatches are NOT_VERIFIED**. They require a
fresh approval/catalog digest and available quota. No extra calls were made after
the approved smoke hit 429, and the full-dataset approval drafts remain PENDING.

## Local tools and safe reproduction

The user's `.local/run_ai06_smoke_local.py` and
`.local/inspect_ai06_mismatches.py` now forward to versioned app-owned tooling.
The supplied `.local/ai06_smoke_2cases.json` is unchanged. Reading a local fixture
in dry-run is allowed; execution requires reviewed external operator files and
exact approved question/source digests.

```text
python .local/run_ai06_smoke_local.py
python scripts/apps/servicedesk/ai06/cloud-smoke-local.py --operator-dir D:/ServiceDesk-Operator/ai06-smoke-20261025/round2
python .local/inspect_ai06_mismatches.py --report .local/ai06-synthetic-v02/final-head/windows-source.json --dataset fixtures/intelligence/service-desk/generation/dev-v0.2.json --output .local/ai06-cloud-verification/mismatch-triage.json
```

Dry-run makes zero inference requests. Executing additionally requires `--execute`
and a fresh approved scope. The launcher reserves an exclusive external ledger;
changing `--output` cannot replay a spent approval. Crashes consume the reservation.
It loads existing unprefixed env names without printing or rewriting key values.
The inspector validates dataset pins, case coverage, boolean types and counters;
its output is explicitly unverified triage, not a trusted execution attestation.

Dataset cloud runs accept `--minimum-interval-seconds` in 0..60 (default 0). Approval
is rechecked after waiting before each next case. Pacing never retries a failed
case or waits for quota recovery. Requests, input/output reservations and cost
remain bounded by the existing explicit scope approval.

Ignored evidence is under `.local/ai06-cloud-verification/`: initial failures,
remaining approved diagnostic attempts, `round2-real-api.json`, per-provider
`*-smoke30.json`, `cloud-matrix.json` and `behavior-comparison-summary.json`.
Live runs occurred on `15c6a09` with a dirty implementation under review. Their
actual source/implementation/configuration identities are retained; they are not
clean final-commit attestations. Regenerate offline receipts at the final commit
and keep real receipts historical rather than editing their revision fields.

Final implementation regression: **1,060 passed, six optional model tests skipped**
in each of source and installed-wheel modes on Windows; **206 focused tests passed
on Linux** with freshly installed wheels; **248 Java/integration tests passed**.
Ownership/source/wheel, historical boundary mutations and canonical regeneration
checks passed. These offline results do not upgrade the partial cloud runs or
establish live v0.3 quality. Hosted CI execution is not claimed.

See [ADR-008](../../decisions/ADR-008-bounded-snapshot-cloud-evidence.md) for the
generic capability boundary and preserved defaults.

Protocol references: [Gemini thought signatures](https://ai.google.dev/gemini-api/docs/thought-signatures),
[Groq response metadata](https://console.groq.com/docs/api-reference), and
[Groq reasoning controls](https://console.groq.com/docs/reasoning).
