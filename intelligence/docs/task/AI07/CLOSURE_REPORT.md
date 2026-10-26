# AI07 v1.6 engineering implementation and remaining closure gates

Date: 2026-10-26. Implementation base:
`f17cac0cf8d265c92bf0aadae6c2e08c0a2fc8bd`; local `origin/develop` reference:
`f94f0ef102cbc7d5737ca05270b0affab1fde1a6`.

The model-primary handoff supersedes the earlier rule-primary assumption for the
new category proposal flow. The implementation is locally verified engineering;
formal hosted-CI acceptance, authentic rights/Gold and production approval remain
pending. No model champion is selected. See [ADR-011](../../../../docs/architecture/ADR-011-ai07-model-primary-rule-guarded.md).

## Delivered behavior

`POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/classify`
is additive. It accepts only `contractVersion=model-primary-assessment@0.1.0`.
The model supplies a tentative category; rule guards never rewrite a class on
a keyword. Impact, urgency and priority can remain null while the category is
present. The response always requires review, exposes no calibrated score,
grants no operational authority and performs no ticket/workflow/routing writes.

DISABLED is the default. SHADOW conceals the category and model-derived outcome;
ASSISTED exposes a source-tagged proposal after processing eligibility is verified.
Both inference modes require an external, authenticated, unexpired processing
grant for the exact workspace/model/policy. PROMOTED is refused. The public API
cannot supply its own input text, model, policy, grant, score, priority or route.
Actor, persisted snapshot and grant are rechecked after inference, including
degraded paths. HTTP inference is bounded to one slot, a four-second deadline,
strict JSON and a circuit after repeated failures, with no automatic retry.

The legacy `assistant/assess`, AI02 triage, immutable `service-desk@0.3.0` profile,
AI05 and AI06 contracts remain stable. The old rule category fallback is not used
as a model label or as a fallback category in the new endpoint. Frontend behavior
was not changed.

Core retains authorization and resource authority; HDPI contracts contain no
Service Desk taxonomy. Domain guards and selection/processing policy live in
Tier 3. Neutral room-availability tests exercise the new generic classifier and
HTTP capability. Python and Java guard policy bytes are checked for equality.

## Private dataset integration

The user-provided `intelligence/src/apps/servicedesk/dataset` directory remains
intact and is explicitly ignored by Git. A separately validated private copy is
under `.local/ai07/datasets/labops-v4`. No raw dataset, reviewer sheet, prediction
ledger, trained weight, token or owner verification key is committed or shipped
in the generic wheel.

Dataset: `ai07-labops-classification-eng-release@4.0.0`.
Manifest SHA-256:
`ba705f6f4e805e73f95894a047c874b296e63d2d4efcf0c1dbdf0439cb3f1723`.
All 37 pinned files are verified before row interpretation. The supplied input
was an extracted directory; no unavailable ZIP hash is invented. The importer
also supports bounded ZIP extraction with traversal, duplicate-path, symlink and
compression/size checks. Imports and receipts refuse overwrites.

| Partition | Total | PROPOSE silver | ABSTAIN silver | Families |
| --- | ---: | ---: | ---: | ---: |
| TRAIN | 1,083 | 741 | 342 | 37 |
| DEV | 324 | 185 | 139 | 11 |
| CASCADE reserved | 10 | 7 | 3 | 3 |
| CHALLENGE engineering | 60 | 35 | 25 | 60 |

All 1,477 non-holdout records retain MACHINE_SILVER_UNREVIEWED and pending rights.
TRAIN/DEV families and normalized exact content are disjoint. IDs, split fields,
typed decisions, five-class membership, literal evidence offsets, model-input
projections and false Gold/review flags are validated. The legacy 40-row holdout
remains inventory-only and quarantined. CASCADE is not evaluated. The supplied
lexical audit and family split are not proof of semantic independence or an
unseen final holdout. DEV ACCOUNT still has only one family.

Only title/description reach native models. IDs, source categories, silver
labels, flags, family IDs, rule IDs and existing rule predictions are excluded
from model input. Runtime v0.2 distinguishes ENGINEERING_SILVER from the original
synthetic lab and binds the server to the exact dataset digest. A caller cannot
claim another dataset or use this binding to approve production processing.

## Reproduced research results

All DEV results use the same 324 unique cases. There is one measured attempt per
case and candidate in this v4 study; these are not repeat-stability estimates.
The older 48-case, three-repeat receipts retain their separate identity.

| Candidate | PROPOSE | ABSTAIN | INVALID | Agreement with 185 labelled silver references | Proposals on 139 silver-ABSTAIN cases |
| --- | ---: | ---: | ---: | ---: | ---: |
| CPU word/char TF-IDF + LogReg | 324 | 0 | 0 | 129/185 (69.73%) | 139 |
| Frozen multilingual E5 embeddings + LogReg | 324 | 0 | 0 | 116/185 (62.70%) | 139 |
| Tev T0 | 243 | 81 | 0 | 93/185 (50.27%) | 96 |
| Tev T1 | 255 | 69 | 0 | 93/185 (50.27%) | 104 |
| Gemma G1 v0.2 | 88 | 208 | 28 | 16/185 (8.65%) | 35 |

These are proxy silver agreements, not classification accuracy, Macro-F1 or a
selection decision. More proposals do not establish a better model. Neither CPU
candidate learned an approved abstention/calibration policy; their proposals on
every silver-ABSTAIN case are a warning against operational use.

Both CPU experiments fit only the 741 TRAIN proposals. Their vocabulary/head
never fits DEV or challenge. Word/char features use the established fixed seed
and parameters. The semantic candidate reuses the repository's pinned
`intfloat/multilingual-e5-small` ONNX/tokenizer with a frozen encoder and L2
features. Its inference is offline and verified by byte hashes. Neither path
constructs TrainingApproval, marks rows reviewed, saves classifier weights,
loads pickle/joblib or produces an operational model artifact. Both manifests
are UNREVIEWED_SILVER_POC / NEVER_PROMOTABLE. Official reviewed training remains
unchanged and gated.

The native DEV study has 972 complete attempts. All 28 rejections belong to
Gemma G1 and remain in the denominator. Its 296/324 valid outputs (91.36%) do
not meet the provisional 95% conformance research target. Latency includes
failures: DEV wall p50/p95 was approximately 1,043/1,349 ms for T0, 792/921 ms for
T1 and 948/1,174 ms for G1. CPU lexical prediction p50/p95 was 2.5/4.0 ms;
embedding+head prediction was 48.4/66.1 ms on this machine. These are engineering
measurements, not a production SLO or a cross-machine performance guarantee.

CHALLENGE is explicitly ENGINEERING_CHALLENGE_ONLY. Sixty cases each were run
through CPU lexical, Tev T1 and Gemma G1. T1 yielded 42 PROPOSE / 18 ABSTAIN and
20/35 labelled-silver agreement; G1 yielded 8 PROPOSE / 46 ABSTAIN / 6 INVALID
and 1/35 agreement. This challenge is not sealed Gold or a final holdout.

The persistent observer uses one authenticated Docker worker process per
candidate, instead of spawning for every input. Five DEV inputs per candidate
were measured, keeping all cold/error attempts. Persistent wall p50 was about
413 ms for G1, 139 ms for T0 and 152 ms for T1. Cold/model-load calls reached
about 6.0 s and 4.4 s; the five-case p95 includes them. This tiny profiling cohort
cannot establish a production SLO. In particular, cold readiness is required
before any approved synchronous deployment with the four-second client deadline.
Token/load counters and observed placement are retained in this separate profile;
no process-attributed VRAM peak is claimed.

### Versioned UTF-8 serialization experiment

Inspection found that G1 v0.2 embeds ASCII-escaped JSON in the user-message
content, so the model sees literal Unicode escape sequences. The separately
pinned `gemma-classify-only@0.3.0` lane sends natural Unicode inside that message
and reuses the identical strict decoder, schema and generation controls. The
original G0 and G1 v0.2 implementations/policies remain frozen. Select this
experiment explicitly with `--gemma-lane G1_UTF8`; it is not the default.

On the same 324 DEV cases, G1 UTF-8 returned 134 PROPOSE, 124 ABSTAIN and 66
INVALID, with 39/185 labelled-silver agreement (21.08%) and 59 proposals on
silver-ABSTAIN cases. Its conformance fell to 258/324 (79.63%); wall p50/p95
was approximately 1,011/1,409 ms. Natural input improved proxy agreement while
structured-output reliability worsened. This is not a winning model or a
production improvement claim. The new lane remains a diagnostic experiment;
no invalid state is corrected or accepted. These additional 324 attempts are
separate from the 972-attempt control study and do not increase unique-case N.

A separate actual Tev proposal was passed through the neutral inference contract
and app guard: its category was preserved, priority stayed null, review stayed
mandatory and SHADOW concealed the vote. This private structural probe is not
backend activation or a quality benchmark. Real authorization and no-write
behavior are independently tested against PostgreSQL and the Java HTTP endpoint.

## Review and release gates

The imported 75-case blind review queue is preserved privately. Rubric v0.3
fixes the proposed observed-symptom objective and lists ownership/security and
boundary cases for real adjudication. Its owner status remains DRAFT/PENDING.
The implementation does not fabricate reviewer identities, votes, rights,
adjudication or an unseen holdout.

The reviewed DEV scorer requires a separately authenticated external freeze,
rights and distinct reviewer receipt references, frozen rubric/benchmark plan,
candidate model/policy pins, source/input hashes, expiry and multiple independent
families per class. Its verification key is configured outside Git, not supplied
as an approval boolean. It reports five-class F1 with non-decisions counted as
false negatives, per-class confusion/recall, all-case coverage, wrong acceptance,
selective accuracy and a fixed-seed family-cluster bootstrap. HOLDOUT is refused;
production and champion selection stay NO_GO even after DEV scoring. All signed
fixtures in tests are explicitly owned synthetic controls, not real approvals.

Hosted workflow visibility is enabled for feature-branch pushes. Source/wheel
model-primary controls and content-free artifacts are added; private data,
native inference and new AI07 model downloads are excluded. No exact implementation
SHA was pushed or run on hosted CI by this local commit request. The connector
reported zero workflow runs for the base SHA. Hosted CI is NOT_RUN, not PASS or
an invented platform/billing failure.

Current release state is QUALITY_PENDING / NO_GO, selectedModel=null. Remaining
external prerequisites are authentic rights/licensing, independent Gold review,
representative family coverage, a new sealed holdout, predeclared selection
criteria, hosted CI on the implementation SHA and security/platform/release
acceptance. Local tests and byte hashes cannot substitute for those approvals.

## Reproduction and rollback

### Local verification

| Check | Observed result |
| --- | --- |
| Full pinned Python source suite after HTTP cleanup | 1,327 passed, 7 optional skips |
| Full installed generic wheel, isolated Python | 1,327 passed, 7 optional skips |
| Subsequent AI07 source and isolated-wheel suites, including UTF-8 adapter | 504 passed, 1 POSIX-only skip in each |
| Final report runtime/model/policy tamper controls | 28 passed |
| Full Java/PostgreSQL default regression | 334 passed, no failures/errors/skips |
| Final model-primary guard/HTTP/authorization/PostgreSQL checks | 62 passed, no failures/errors/skips |
| Core-only Java profile | 78 passed, no failures/errors/skips |
| Second-domain Java profile, including neutral classification HTTP | 24 passed, no failures/errors/skips |
| Strict source ownership and hostile mutations | PASS; 39 mutation tests passed |
| Service Desk OpenAPI and workflow YAML | PASS |
| Changed Python Ruff lint/format and Java AOSP formatting | PASS |
| Dataset original and private import | All 37 file hashes and 1,477 non-holdout records validated |
| Native control, challenge and UTF-8 ledgers | Complete denominators, proxy summaries and exact trusted runtime bindings verified |
| Actual model -> neutral contract -> rule guard | PASS private structural probe; no backend activation |
| Hosted CI on the implementation SHA | NOT_RUN by this local commit request |

Counts overlap between suites and must not be added as unique cases. The full
suites preceded the final optional serializer/report-identity controls; those
paths were then tested in the focused suites and native runs. No model weights
are needed for hosted AI07 contract tests. The seven optional skips are the
existing provisioned/approved-model and POSIX-specific controls, not hidden
classification failures.

One earlier installed-wheel pass had a Windows socket reset in the existing
assistance HTTP early-rejection test. The server could close while a rejected
request body was still unread. A bounded 16 KiB / 200 ms cleanup after sending
the error preserves auth/route/method checks and prevents discarding the status
on Windows. Source and installed-wheel rejection tests both passed, followed by
clean full-suite runs. This transport fix does not change AI06 answer contracts,
provider selection, grounding or release status.

Receipts live under `.local/ai07/v16`, including the XML suites, private native
ledgers, source/wheel model-primary controls and persistent profile. The final
UTF-8 bridge revision is
`d0968e25972c61d03ae8ee74ff9361740b31b10773ae2765c564d707e4cb9cf7`
with image
`sha256:70c5b8fe54cb28bedbb88786190c70b7887fbc0a161098af5b63c53f47a46bbd`.
The earlier control and persistent studies retain their own frozen image/runtime
receipts. Artifact identities are not substituted retroactively.

Use a fresh output directory under `.local/ai07`; every existing receipt is
immutable. The original 48-case evaluator is unchanged in meaning.

```powershell
python scripts/apps/servicedesk/ai07/validate-labops-v4.py --dataset-root intelligence/src/apps/servicedesk/dataset --output .local/ai07/closure-new/dataset.json
python scripts/apps/servicedesk/ai07/import-labops-v4.py --source intelligence/src/apps/servicedesk/dataset --destination .local/ai07/datasets/labops-v4-new --output .local/ai07/closure-new/import.json
python scripts/apps/servicedesk/ai07/train-cpu.py --dataset-root .local/ai07/datasets/labops-v4-new --allow-machine-silver-poc --output .local/ai07/closure-new/cpu.json
python scripts/apps/servicedesk/ai07/train-cpu.py --dataset-root .local/ai07/datasets/labops-v4-new --allow-machine-silver-poc --features embedding --embedding-directory <explicitly-provisioned-pinned-model> --output .local/ai07/closure-new/semantic.json
```

Build a new code-only bridge using the existing runbook. Configure it with
`--engineering-dataset-root <private-root> --allow-pending-rights-engineering`,
`--gemma-lane G1` and `--tev-study T1`; then start the existing private Compose
base/GPU overlay. Run `evaluate-labops-v4.py --split DEV` with an explicit private
root, candidate list, container, fresh output directory and pending-rights
engineering opt-in. `--split CHALLENGE` has its own engineering-only scope.
Use `verify-labops-report.py` to recompute every denominator and proxy summary;
native reports require `--runtime-config` pointing to their exact frozen runtime,
including model/policy/serving identity. CPU manifests remain NEVER_PROMOTABLE.
`--require-production` must exit 2. `profile-local-inference.py` documents the
separate persistent transport experiment.

Backend mode/configuration is server-owned (`SERVICE_DESK_CLASSIFIER_*` and
`INTELLIGENCE_CLASSIFICATION_*`). A real activation requires a separately
approved serving deployment and a private processing grant, not a connection to
the lab's Ollama port. The shipped additive inference launcher is an authenticated
engineering reference assembly bound to a dataset digest; it is not certified
for real customer traffic. Test keys must never be installed as owner keys.

Rollback is DISABLED and application restart, or immediate grant revocation,
with the existing rule API available. Stop the isolated AI07 Compose profile
without `--volumes`. Keep model volumes, imported data and all receipts. No
frontend, public model port, Core network connection or cloud fallback is added.

## Bổ sung AI07 v1.9 — đánh giá phân cấp

Xem [phạm vi, cấu hình và rollback](HIERARCHICAL_ASSESSMENT.md),
[đánh giá Gold](HIERARCHICAL_GOLD_EVAL.md) và [kết quả kiểm chứng](VERIFICATION_V1_9.md).
Taxonomy/risk còn DRAFT; Gold độc lập, hosted CI, hiệu năng phục vụ thật và ký duyệt
production vẫn là gate riêng. `selectedModel=null`, production `NO_GO`.
