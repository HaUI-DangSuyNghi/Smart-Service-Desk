# AI07 v1.5 — diagnostics and classification-only experiments

Implementation base: `d383cb249f46459856a1560019a677c6cce33ce8` on
`feature/ai07-ticket-intelligence`. This implements the first research iteration
of the local v1.5 plan. Rule-primary ticket behavior remains authoritative.
All experiment policies are synthetic DEV only. Quality is NOT_EVALUATED and
production release is NO_GO.

## Changes and gates

| Plan item | Implementation | Remaining acceptance |
| --- | --- | --- |
| D0 | CI source/wheel G1 conformance, second-domain controls, safe artifacts and Compose base/GPU configuration checks | Hosted CI and human review have not been certified locally |
| D1 | Bounded error taxonomy through runtime, bridge, worker and v0.2 receipts; deterministic per-case diagnostics, slice summaries and unique-case Wilson intervals | Historical unknown reasons stay unknown; fresh native results are separate receipts |
| D2 | Separate pinned Gemma G1 adapter and app policy; strict two-field decoder returning `DecisionObservation` | No classification quality claim without reviewed labels |
| D4 | Frozen T0, clarified-unknown T1, three fixed T2 order permutations and paired stability CLI | T1 business interpretation requires owner review; optional hierarchical T3 is deferred |
| D5 preparation | Annotation rubric v0.2 with independent review, rights and family-split instructions | Genuine data-owner and reviewer receipts remain required |
| D3, D6–D11 | Existing gates retained | G2 depends on useful G1 evidence; training, paired gold evaluation, shadow integration and release require their explicit prerequisites |

G0's adapter and policy are unchanged. G1 never accepts quotes, explanations,
confidence, routing, priority, risk, tools or caller authority. It independently
validates the native envelope, bounded strict JSON including duplicate-key
rejection, allowed class and PROPOSE/ABSTAIN state pair. The serving schema is
deliberately simple for the pinned runtime; cross-field checks always run on the
host. Accepted G1 votes have `UNVERIFIED_BY_MODEL`, `NOT_AVAILABLE` scores,
no spans and `operational_authority=false`. There is no fallback to G0.

Integration routing uses an allowlisted adapter version from the hashed runtime
configuration. Business definitions live in Service Desk policy files. The
generic wheel contains the neutral decoder, not policies, experiments, adapters
or weights. Room availability controls exercise independent generic reuse.

Rejections carry allowlisted reason codes, elapsed native time, load state and
bounded token/stop counters when actually observed. Prompts, completions and
exception messages are never serialized into diagnostics. The report distinguishes
all-attempt wall/native latency from validated native latency. Missing historical
token data remains null with an observed-attempt count of zero.

Engineering receipts use `local-model-engineering@0.2.0`. Every failed measured
or warmup attempt requires a bounded reason. The verifier still accepts frozen
v0.1 receipts, recomputes denominators and rule snapshots, and returns NO_GO.
The analyzer accepts only known synthetic DEV IDs and copies an explicit output
allowlist; arbitrary input report fields are not forwarded. Warmups remain a
separate, complete load-schedule denominator. Existing receipts are never overwritten.

T2 modifies only class/criteria order and experiment identity; criteria text,
instructions and abstain semantics match T0. Stability comparison binds policy
hashes, model/tokenizer/API digests, case IDs, repeat indices and input hashes.
It reports changes, not correctness or calibration. T1 preserves OTHER's existing
facilities scope and asks for classification from symptoms without requiring
root-cause certainty. It does not change the authoritative business ontology.

## Historical replay

The local `.local/ai07/v14/local-dev.json` receipt contains 288 measured attempts
and 40 warmups, with 48 distinct DEV cases and three repeats per candidate.

| Candidate | Case-level finding |
| --- | --- |
| Tev T0 | Exactly 24 cases abstained in all three repeats; zero cases changed state/class across repeats |
| Gemma G0 | 27 cases had at least one abstention; 23 abstained in every repeat; four cases changed state/class |
| Gemma G0 rejections | All 71 historical rejected attempts lack a diagnostic reason and remain `MISSING_LEGACY_DIAGNOSTIC` |

This supports investigating stable task framing for Tev. It does not establish
the cause of Gemma's rejections or either model's accuracy. Scenario and
scenarioFamily are analysis dimensions, never gold labels. Intervals use unique
cases, not the 144 repeated attempts as independent tickets. No HOLDOUT inference
or annotation was performed.

## Fresh native DEV study

The frozen seven-candidate study completed on 2026-10-25 with 48 unique cases,
three repeats per candidate, 1,008 measured attempts and 38 separately recorded
warmups. The complete ledger verifier returned `COMPLETE_WITH_FAILURES`: 87
measured failures, all with bounded diagnostic reasons. There were no measured
transport/timeouts/unavailable states. Every candidate was stable across its
three repeats in this particular run.

| Candidate | PROPOSE | ABSTAIN | INVALID | All-attempt wall p50 / p95 ms |
| --- | ---: | ---: | ---: | ---: |
| Gemma G0 | 0 | 69 | 75 | 1,695 / 4,269 |
| Gemma G1 | 48 | 84 | 12 | 1,014 / 1,170 |
| Tev T0 | 72 | 72 | 0 | 791 / 916 |
| Tev T1 | 90 | 54 | 0 | 791 / 878 |
| Tev T2 rotate | 78 | 66 | 0 | 795 / 928 |
| Tev T2 reverse | 87 | 57 | 0 | 797 / 974 |
| Tev T2 interleave | 48 | 96 | 0 | 786 / 893 |

G0 reasons were 36 `INVALID_STATE_PAIR`, 36 `INVALID_QUOTE_MISSING` and three
`TRUNCATED`. Missing quote includes absent or non-matching literal text; it is
not a semantic judgment. No repeated-quote rejection was observed in this run.
These fresh diagnostics must not be retroactively assigned to the 71 historical
v1.4 failures.

G1 had 132/144 valid outputs (91.67%), with 16 distinct proposing cases, 28
abstaining cases and four rejected cases. Its 12 failures were all invalid state
pairs and remained rejected. Classification-only formulation improved output
conformance and latency on this cohort, but still missed the plan's provisional
95% conformance research target. This is not evidence of classification accuracy
or grounding; G2 and promotion remain deferred pending useful reviewed evidence.

T1 changed six of 48 cases relative to T0 and reduced stable abstention from
24 to 18 cases. T2 changed nine cases for rotate, seven for reverse and ten for
interleave. Tev therefore shows observable option-order sensitivity even though
each fixed policy repeated consistently. More proposals or fewer abstentions
cannot establish a better policy without reviewed labels and business approval.

The host GPU sampler recorded 3,760 samples with zero sampler errors and a
1,275 MiB peak **global** used memory. This is not a process-attributed VRAM peak.
Native token/stop counters, cold/load indicators and complete native timings are
retained in local receipts when observed. All-attempt wall latency includes
Docker exec/process startup; no persistent-transport performance claim is made.

Ledger SHA-256:
`9782d2d6fd453f81a650b19b0faf7e2eeb71e73778feba2c79ba9b6d5fbc0919`.
Runtime configuration SHA-256:
`f274842302584000132b921d05ef34c40c2ea7103aecee88a2e8654dc45625f6`.
Local evidence is under `.local/ai07/v15`: `local-dev.json`, `diagnostics.json`,
`choice-stability.json`, `release-validation.json` and `improvement-evidence.json`.
The receipt identifies the uncommitted implementation through its bridge revision
and records the base Git HEAD honestly; it is not a hosted-CI attestation.

Live isolation probes verified anonymous denial, blocked external connectivity
and read-only model mounts. `--require-production` returned exit 2 with NO_GO.
The experiment containers and their private network were removed after the run;
the model volume and receipts were retained. Reviewed gold, rights, licensing,
independent HOLDOUT, hosted CI and independent release reviews remain blockers.

## Operator commands

Use the repository's pinned Python environment. Choose a fresh directory under
`.local/ai07`; the example names below must not be reused after a successful run.

```powershell
python scripts/apps/servicedesk/ai07/analyze-local-errors.py --report .local/ai07/v14/local-dev.json --output .local/ai07/v15-new/historical-diagnostics.json

python scripts/apps/servicedesk/ai07/build-local-bridge.py --output .local/ai07/v15-new/bridge.json
python scripts/apps/servicedesk/ai07/configure-local-runtime.py --provision .local/ai07/v14/provision.json --bridge .local/ai07/v15-new/bridge.json --directory .local/ai07/v15-new/runtime --enable-synthetic-lab --gemma-lane G1 --tev-study all

docker compose -p ai07-v15-new --env-file .local/ai07/v15-new/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local config --quiet
docker compose -p ai07-v15-new --env-file .local/ai07/v15-new/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local up -d --wait --wait-timeout 180

python scripts/apps/servicedesk/ai07/evaluate-local-models.py --split DEV --candidate all --container ai07-v15-new-ai07-classification-bridge-1 --allow-draft-synthetic --warmups 2 --repeats 3 --output .local/ai07/v15-new/local-dev.json
python scripts/apps/servicedesk/ai07/validate-local-release.py --reports .local/ai07/v15-new/local-dev.json --runtime-config .local/ai07/v15-new/runtime/runtime.json
python scripts/apps/servicedesk/ai07/analyze-local-errors.py --report .local/ai07/v15-new/local-dev.json --output .local/ai07/v15-new/diagnostics.json
python scripts/apps/servicedesk/ai07/evaluate-choice-stability.py --reports .local/ai07/v15-new/local-dev.json --output .local/ai07/v15-new/choice-stability.json

docker compose -p ai07-v15-new --env-file .local/ai07/v15-new/runtime/compose.env -f infra/compose/compose.ai07-local-models.yaml -f infra/compose/compose.ai07-local-gpu.yaml --profile ai07-local down
```

`--tev-study all` adds T1 and all three T2 policies alongside T0. `--gemma-lane
G1` adds G1 alongside G0. The default options retain only T0/G0. `--candidate`
must name a candidate bound into that exact runtime; `all` uses every binding.
The seven-candidate, three-repeat study has 1,008 measured attempts, not 1,008
independent tickets. Do not run another inference/provisioning process against
the same volume during the experiment.

Use `--require-production` only to test the refusal gate: it must exit 2.
No local test result, policy file or generated worksheet can grant release
approval. Hosted CI, reviewed labels, data rights, independent HOLDOUT, license
and security/platform sign-offs remain separate requirements.
