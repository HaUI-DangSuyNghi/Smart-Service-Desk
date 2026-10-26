# AI07 v1.4 local-model implementation and measurements

Measured on 2026-10-25. The Docker engineering experiment is implemented.
Classification quality is **NOT_EVALUATED**, the empirical winner is
**NOT_SELECTED**, and production is **NO_GO**. The Java assistant remains
rule-first and default-off. No model was added to its synchronous request path.

This extends commit `857f4275ecd3c087ef2388846c622a5c55674fee` on
`feature/ai07-ticket-intelligence`, originally branched from `origin/develop`
at `f94f0ef102cbc7d5737ca05270b0affab1fde1a6`. Historical
[v1.3 verification](VERIFICATION.md) remains a separate record.

## Delivered behavior

Tev1 uses native `/v1/systemone` and returns a non-authoritative
`DecisionObservation`. `UNDETERMINED` maps to `ABSTAIN` with a null class; no
literal evidence is invented. Gemma uses native `/api/chat` with a Tier-3-owned
JSON schema. Its quotes must occur exactly once across the original input fields
before conversion to Unicode code-point spans. Invalid joint states, transformed
quotes, duplicate keys, unknown classes and extra fields are rejected.

The dedicated Ollama and authenticated bridge share a private loopback namespace
on an internal Docker network. They publish no ports, mount model artifacts
read-only and have no Core data, application uploads, cloud credentials or Docker
socket. The bridge runs as UID 10001. The optional profile is separate from normal
application startup. Provisioning imports the existing host model bytes into a
dedicated external volume and seals them before runtime; it preserves host models.

Startup verifies model bytes once. Requests check a filesystem identity lease
instead of hashing gigabytes again. Only native content-addressed `manifests-v2`
leaf links are accepted under the separate integration contract; the legacy
generic model-manifest link prohibition is unchanged. See
[ADR-010](../../../../docs/architecture/ADR-010-pinned-local-decision-classifiers.md)
and [the reproducible runbook](../../../../infra/compose/README.ai07-local.md).

## Frozen environment

| Item | Observed identity |
| --- | --- |
| GPU | NVIDIA GeForce RTX 3050 4GB Laptop GPU; driver 591.74; 4096 MiB |
| Ollama | 0.40.8; image SHA-256 `242721b8a8dbb71c38fdc0a07ed60ca78363f278d33fdf171a48728abf40743a` |
| Bridge revision | `32475ffa2067b6d36356eaebe144a828d074c20af3576dccd2cede7e027a5661` |
| Runtime configuration digest | `77fe49e7ebf3bf8afb32bf4ffe10004b1f6ad425961e9716dfe3b3dafcf3ce4d` |
| Tev1 | 0.8B Q8_0; derived alias `ai07-tev1-0.8b:ctx2048` |
| Gemma | `gemma3:1b`; Q4_K_M |
| Inference controls | Context 2048 verified from `/api/ps`; concurrency 1; one loaded model; keep-alive 30 s |
| Gemma generation | Temperature 0; seed 0; output budget 256; truncation and shifting disabled |

Tev1's original parameters specify context 8192. Native SystemOne does not accept
generation options, so provisioning creates a new explicit 2048-context manifest
with identical weights and records both identities. No parameters are silently
ignored. The runbook lists the exact GGUF hashes; runtime receipts also bind the
native API/model manifests, tokenizer, adapter, policy and taxonomy identities.

## Actual GPU and DEV results

Both candidates passed an authenticated bridge GPU smoke test. Each reported
context 2048 and full offload in the observed model placement: Tev1
1,223,866,449 bytes and Gemma 877,721,026 bytes for both total size and VRAM size.
The first cold native calls took approximately 68.6 s and 35.0 s respectively.
These observations do not establish a latency guarantee.

The DEV experiment ran from 09:51:33 to 10:00:01 UTC: 48 existing draft synthetic
cases, three repeats per candidate, alternating ABBA-style blocks, and ten
warmups per load. All 288 measured attempts and 40 warmups are retained.
HOLDOUT was not sent to either model. The frozen Rule v0.3 category, priority and
review snapshots are stored separately and recomputed by the verifier.

| Candidate | Attempts | PROPOSE | ABSTAIN | Rejected | All-attempt wall p50 / p95 | Validated native p50 / p95 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| Tev1 | 144 | 72 | 72 | 0 | 775.217 / 890.119 ms | 105.453 / 143.243 ms; n=144 |
| Gemma | 144 | 0 | 73 | 71 | 1705.610 / 4182.330 ms | 753.645 / 951.134 ms; n=73 |

Wall times include worker/bridge overhead and every failed attempt. Native times
cover validated results only; Gemma's native latency statistics cover accepted
abstentions, not successful category proposals. The 250 ms NVIDIA sampler recorded
1,510 samples, zero sampler errors and a peak of 1,269 MiB. This is global GPU
memory on this host, not per-process attribution or a general 4GB capacity proof.

Gemma's 71 rejections remain failures in the denominator. A separate diagnostic
replay of `TICKET-V2-001` produced valid outer JSON but combined `ABSTAIN` with
three quotes, one absent from the exact input. The adapter correctly rejected it.
No raw input/output was persisted for this diagnostic, and it was not substituted
into the benchmark. No automatic quote repair, schema relaxation or prompt tuning
was used to hide those failures.

The 48 draft synthetic rows are not reviewed gold. Schema validity does not imply
semantic correctness. Macro-F1 and calibrated accuracy remain null. The CPU SSD
classifier remains **NOT_TRAINED** pending reviewed five-class TRAIN data and
rights; no LabOps four-class artifact was imported.

## Verification and retained evidence

Private, content-free receipts remain under `.local/ai07/v14/`: `provision.json`,
`bridge-build-r3.json`, `runtime-r3/runtime.json`, `gpu-tev1.json`,
`gpu-gemma3.json`, `local-dev.json` and `gemma-diagnostic.json`.
They are not committed, and the separate token file must never be published.
Failed provisioning/build attempts remain separate; their records were not
overwritten. Runtime configuration and reports contain hashes and diagnostic
codes rather than customer tickets or model prose.

The actual deployment probes verified absent published ports, a shared namespace,
read-only roots/model mounts, disabled container logging, rejected anonymous
bridge access and blocked outbound connection. The release verifier recomputes
the dataset inventory, DEV identities, Rule snapshots, attempt denominators,
placement and latency summaries. It reports **COMPLETE_WITH_FAILURES**, 71 failed
attempts, **NOT_EVALUATED** quality and **NO_GO** production. Its
`--require-production` mode exits 2 for this experiment.

| Local check | Result |
| --- | --- |
| Full Python source regression, Windows | 1,194 passed; 7 skipped; zero failures/errors |
| Native integration/classification contracts, Linux | 106 passed, including the real content-addressed symlink case; zero skips |
| Installed wheel contracts, Windows isolated Python | 220 passed; one POSIX-only test skipped |
| Final ledger hardening, source and installed wheel | 11 passed in each mode; rejects missing warmups, wrong phases and negative native timings |
| Maven default / core-only / second-domain, Linux | 276 / 78 / 14 tests; zero failures/errors/skips |
| Architecture boundaries and mutation proofs | PASS; 39 mutation tests passed |
| Immutable profile and foundation validation | PASS |
| Python lint/format and measured bridge-source identity | PASS |
| Production gate CLI | Correctly denied promotion with exit 2 |

The final ledger hardening was added after the full regression run and verified
with the separate focused suite. The seven Windows source skips include six
existing opt-in model tests and the POSIX symlink case exercised on Linux.
Normal CI discovers the new contract tests through the existing source/installed
wheel suites; it does not download weights or start GPU inference. Hosted CI and
independent security/release acceptance have not run.

## Remaining release gates

Reviewed TRAIN/DEV labels and data rights, independent HOLDOUT evaluation, model
license review, acceptable model conformance, hosted CI, and independent security
and owner release decisions are still required. These engineering measurements
do not approve a model for ticket decisions or approve the separate AI06 release.

The experimental profile was stopped after measurement. The external
`ssd-ai07-models-v14` volume and private receipts were retained. Use the runbook's
`down` command without `--volumes` after later experiments. Rollback requires no
change to the existing rule engine.
