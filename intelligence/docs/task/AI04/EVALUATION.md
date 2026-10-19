# AI-04 evaluation operations and handover

Implementation status: ready for review on `feature/ai04-evaluation-foundation`.
Local evidence is Windows / CPython 3.12.7 with `intelligence/requirements.lock`.
Remote Linux/Windows CI and initial baseline policy approval are still required
before declaring the phase closed or starting AI-05. No remote run or human
approval is implied by local PASS reports.

See [architecture and ownership](../../architecture/AI04_EVALUATION_ARCHITECTURE.md).
Core, HTTP contracts, application profiles, original ticket/corpus/gold bytes and
the TF-IDF algorithm are unchanged.

## Delivered work packages

| Package | Implementation |
| --- | --- |
| AI04-00 | Evaluation lifecycle, ownership and strict dependency rules |
| AI04-01 | Validated schema 1.0, canonical evidence and report round-trip |
| AI04-02 | Versioned dataset, exact bytes, path and association validation |
| AI04-03 | Hit@1, Hit@3, MRR and macro Recall@K, artifact deduplication |
| AI04-04 | Read-only baseline, dataset/metric compatibility and deltas |
| AI04-05 | Executable quality thresholds and zero-failure invariants |
| AI04-06 | Retrieval orchestration and stable CLI evidence |
| AI04-07 | Candidate protocol and TF-IDF adapter, degraded candidate injection |
| AI04-08 | Exact deterministic triage conformance with safe diagnostics |
| AI04-09 | Nine security/provenance/contract invariants backed by required tests |
| AI04-10 | Deterministic fingerprints, repeated-run and equivalence checks |
| AI04-11 | Source and isolated wheel gates, safe artifacts, cross-OS comparison |
| AI04-12 | App import/literal mutations, bundled fixture/profile rejection |

## Measured baseline and review decision

The inherited AI-03 regression test already records these measurements:

| Metric | TF-IDF on 12 authored cases | Initial hard gate |
| --- | ---: | --- |
| Hit@1 | 7/12 = 0.5833333333333334 | >= baseline |
| Hit@3 | 11/12 = 0.9166666666666666 | >= baseline |
| MRR | 0.7388888888888889 | Recorded |
| Recall@3 | 0.875 | Recorded |
| Conformance pass rate | 12/12 = 1 | Must equal 1 |
| Invariant failure counts | 0 | Must equal 0 |

The old CI suggested targets, 10/12 and 1.0, were already false and never
affected process exit. The initial baseline explicitly records that discrepancy
and remains `PROPOSED`. Reviewers must approve adopting the measured AI-03
candidate as the regression floor; this change does not meet the old targets.
Raising thresholds remains possible. Explicit `--gates` cannot relax a supplied
baseline. MRR and Recall@3 have no invented hard threshold.

Fixtures are authored engineering regression/conformance evidence, not a
production benchmark. Rights remain `PENDING_OWNER_APPROVAL`. Local `kb-*`
identifiers are never Core metadata IDs or runtime citations.

## Reproduce on Windows

Run from the repository root with Python 3.12. Use a dedicated environment so
the numerical runtime matches the lockfile; a global Anaconda install may differ.

```powershell
python -m venv .local/ai04-venv
$py = '.local/ai04-venv/Scripts/python.exe'
& $py -m pip install -r intelligence/requirements.lock
& $py -m pip check
$env:HDPI_PROFILE_ROOT = (Resolve-Path 'intelligence/src/apps/servicedesk/intelligence/profiles').Path
& $py scripts/intelligence/validate-foundation.py
& $py scripts/check-tier-boundaries.py
& $py scripts/test-tier-boundaries.py
& $py -m pytest intelligence/tests -q --junitxml=.local/ai04/source-tests.xml
```

Stop if any command returns a nonzero exit code. The application supplies all
dataset paths, conformance pins and required negative-test identities explicitly:

```powershell
$evalArgs = @(
  '--dataset', 'fixtures/intelligence/service-desk',
  '--baseline', 'fixtures/intelligence/service-desk/evaluation/baseline-v0.1.json',
  '--checks', 'fixtures/intelligence/service-desk/evaluation/security-checks.json',
  '--conformance-config', 'fixtures/intelligence/service-desk/evaluation/conformance-config.json'
)
& $py scripts/intelligence/run-evaluation.py @evalArgs --junit .local/ai04/source-tests.xml --output .local/ai04/source
& $py -m build --no-isolation --outdir .local/ai04-dist intelligence
& $py scripts/intelligence/install-built-wheel.py --dist .local/ai04-dist
& $py -I scripts/intelligence/verify-installed-package.py
& $py -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q --junitxml=.local/ai04/wheel-tests.xml
& $py -I scripts/intelligence/run-evaluation.py --installed @evalArgs --junit .local/ai04/wheel-tests.xml --output .local/ai04/wheel
& $py -I scripts/intelligence/verify-evaluation-equivalence.py .local/ai04/source .local/ai04/wheel
```

Build output must contain exactly one wheel for the declared package version;
use a fresh output directory when changing versions. `--installed` verifies that
the runtime was imported from `site-packages`. `python -I` excludes source paths.
The application profile stays external to the wheel. On Linux use the same
commands with the virtual environment's `bin/python` and exported profile path;
CI is the executable Linux/Windows reference.

Retrieval alone can run from the installed wheel:

```powershell
& $py -I -m hdpi.evaluation.retrieval --dataset fixtures/intelligence/service-desk --baseline fixtures/intelligence/service-desk/evaluation/baseline-v0.1.json --output .local/ai04/retrieval-report.json
```

Retrieval CLI exit codes are 0 for PASS, 1 for measured quality FAIL and 2 for
invalid input. Every other evaluation track also returns nonzero on failed
invariants, invalid input or execution error. Failed measured runs retain their
report; validation/execution errors must never be treated as successful evidence.

## Evidence and compatibility

Each source/wheel output directory contains `retrieval-report.json`,
`conformance-report.json`, `security-report.json` and `evaluation-summary.json`.
Schema 1.0 reports contain dataset identity, all component digests, versioned
system configuration, evaluator/metric versions, case count, metrics, gate
violations, result and deterministic fingerprint. The summary hashes each report.
Fingerprints exclude paths, timestamps, execution durations and Python patch
versions. Complete report equality also checks rankings and gate decisions.

`retrieval.evaluate` retains AI-03 top-level metric aliases for existing library
callers. Use `stable_report(evaluate(...))` when serializing its schema 1.0 report.
CLI output already uses the stable schema. `--fixtures-root` remains an alias
for `--dataset`; `suggestedGates` is a compatibility diagnostic only.

Security evidence maps application-owned required JUnit test identities to nine
invariants. Missing, duplicated, skipped or failing required tests fail the gate;
unrelated execution failures also invalidate the proof. Report dimensions
`SECURITY`, `PROVENANCE` and `CONTRACT` are independent from relevance quality.
These probes exercise runtime validators and private HTTP transport; Core access
is simulated in unit tests. Real Core retrieval and the inherited
`AI04TicketTriagePythonE2E.java` remain separate integration/E2E evidence.

CI uploads only safe report JSON for seven days. Raw JUnit assertion details,
tokens, private content and logs are not uploaded by the evaluation artifact step.
The cross-platform job compares the entire installed-wheel reports from the same
workflow run. A missing artifact, failure or mismatch fails CI.

## Dataset, baseline and version changes

`dataset.yaml` pins knowledge, tickets, ticket manifest and relevance labels.
Knowledge manifest entries pin corpus bytes. The digest includes every pinned
component and source file. Paths must remain inside the declared dataset/corpus,
including after symlink resolution. Dataset schema 1.0 currently supports authored
component version 0.1.0. New data versions require explicit loader support and
review; changing bytes at the same version cannot silently reuse a baseline.

Baselines pin dataset identity/digest, component hashes, case count, metric
definition and the original system/config versions. Different candidate engines
can compare on the same dataset and metric definition. Dataset/metric mismatch
is rejected. Config differences are recorded with candidate and baseline systems.
No evaluation command writes or accepts a new baseline automatically.

For any dataset, normalization, chunking, query strategy, metric or baseline
change, provide the reason, impact, old/new evidence and reviewer approval in
the PR. Bump affected versions and preserve the old baseline for audit. A proposed
regression acceptance must explain the loss; lowering numbers to turn CI green
is not an acceptable baseline update.

## Validation and remaining closure work

Local verification on 2026-10-19:

| Check | Result |
| --- | --- |
| Full source suite | 368 passed |
| Full installed-wheel suite (`python -I`) | 368 passed |
| Source/wheel retrieval, conformance and security reports | PASS, identical |
| Foundation and immutable profiles | PASS, including 145 negative cases |
| Strict boundaries and mutation suite | PASS, 34 tests; zero debt |
| Dependency consistency and built-wheel verification | PASS |

Local validation covers mathematical metrics; dataset checksum, size, path and
association corruptions; incompatible baselines; nonzero CLI exits; empty-ranking
candidates; changed triage decisions; disabled workspace authorization; skipped
negative proofs; wheel contamination; boundary mutations; repeated runs; and
source/wheel equality. The package is built from its source distribution.

Before phase close, reviewers must approve architecture and the initial gate
policy, and the remote workflow must pass on Ubuntu and Windows with equivalent
artifacts. Existing real integration jobs remain required. No BM25, embeddings,
RAG, LLMs or production-quality claim is introduced by AI-04.
