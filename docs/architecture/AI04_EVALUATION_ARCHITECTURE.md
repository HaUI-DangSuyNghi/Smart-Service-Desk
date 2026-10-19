# AI-04 evaluation architecture

Status: proposed for review. Scope: evaluation foundation, not a new retrieval model.

## Ownership and lifecycle

Application-owned dataset -> validation -> candidate -> metrics -> compatible
baseline comparison -> executable gates -> versioned evidence.

Tier 3 owns corpus bytes, authored queries, relevance labels, triage expectations,
profiles and baseline artifacts under `fixtures/intelligence/<application>`.
Tier 2 (`hdpi.evaluation`) owns generic validation, metrics, candidate protocols,
comparison, gates, reports and reproducibility. Core owns no AI evaluation logic.
The evaluator must not import applications, select policy by application name,
read private Core state or package application fixtures/profiles in its wheel.
Existing strict boundary enforcement remains authoritative; no debt is added.

## Evidence contract

Schema `1.0` requires dataset identity/version/digest, system versions, metric and
evaluator versions, case count, metrics, gates, overall result and fingerprint.
Reports are canonical JSON without timestamps, absolute paths or user content.
Evidence classes are `AUTHORED_RETRIEVAL_REGRESSION`, `AUTHORED_CONFORMANCE`,
`SECURITY_INVARIANT` and `CANDIDATE_COMPARISON`. `benchmarkClaim` is always false.
Local artifact identifiers are never runtime citations. Synthetic fixtures prove
conformance and engineering regression only, not production accuracy.

## Dataset and baseline governance

The manifest pins every component's exact bytes. Its deterministic fingerprint
includes corpus, queries, labels and expectations, independent of checkout path.
Traversal, duplicate identities, unknown relevance, orphan associations and
integrity errors fail before candidate execution. Existing fixture bytes stay
unchanged. Dataset version changes or explicit baseline changes require review.

Baselines pin dataset and metric definitions as compatibility requirements.
Engine/config differences are recorded for comparison, not forbidden: future
BM25 candidates must be comparable to TF-IDF on the same measurement definition.
No CI command accepts or writes a baseline automatically.

The inherited AI-03 test records Hit@1 = 7/12, Hit@3 = 11/12 and
MRR = 0.7388888888888889. The old suggested thresholds (10/12 and 1.0)
already fail and never blocked CI. AI-04 records the existing measured candidate
explicitly; adopting these measurements as initial regression thresholds is a
policy change requiring reviewer approval, not a claim that old targets passed.
MRR and Recall@K are recorded; initial hard retrieval gates cover Hit@1 and Hit@3.

## Gate and execution policy

Quality below policy or baseline, any security/provenance/contract failure,
invalid dataset/baseline, or execution errors produce a nonzero process exit.
Security results are independent of relevance and cannot be averaged away.
Conformance checks every authored scenario with exact deterministic expectations.
Negative security probes use synthetic data and real runtime validators, with
simulated Core authorization explicitly distinguished from integration evidence.
`AI04TicketTriagePythonE2E.java` remains inherited Java/Python integration evidence.

Source and isolated installed-wheel runs must agree on rankings, metrics, gate
decisions and fingerprints. CI compares Linux and Windows evidence. Mutations
must demonstrate quality failure, corrupted data/gold rejection, incompatible
baseline rejection, app-boundary rejection and unsafe authorization rejection.

## Completion criteria

Implementation must include dataset corruption tests, mathematical metric tests,
candidate injection, baseline compatibility, executable failure tests, security
and provenance probes, deterministic evidence, isolated wheel evaluation and CI
artifacts. Review and remote CI evidence remain separate from local test claims.
