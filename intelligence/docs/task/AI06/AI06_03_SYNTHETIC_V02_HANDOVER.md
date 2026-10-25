# AI06 synthetic v0.2 handover

Use the existing `feature/ai06-02-governed-real-model-provider` branch and PR #37.
No FE, Core authorization or database migration is part of this expansion.
The dataset and tools are ready for engineering review; cloud models remain
NOT_VERIFIED and official release remains BLOCKED.

## Offline reproduction

Run from the repository root with CPython 3.12 and `intelligence/requirements.lock`.
Use a full Git checkout: baseline and release-history checks require history.

```text
python scripts/apps/servicedesk/ai06/prepare-ai06-synthetic.py
python scripts/apps/servicedesk/ai06/compare-ai06-synthetic.py --output .local/ai06-synthetic-v02/reproducibility.json
python scripts/apps/servicedesk/ai06/validate-ai06-synthetic.py --output .local/ai06-synthetic-v02/data-quality.json
python scripts/apps/servicedesk/ai06/select-ai06-cloud-smoke.py
python scripts/apps/servicedesk/ai06/run-ai06-synthetic-v02.py --output .local/ai06-synthetic-v02/source.json
python -m pytest intelligence/tests/apps/servicedesk/generation -q
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python -m build --no-isolation --outdir .local/ai06-v02-dist intelligence
python scripts/apps/servicedesk/ai06/test-ai06-v02-semantic-ownership.py --wheel .local/ai06-v02-dist/smart_service_intelligence-0.3.0-py3-none-any.whl --output .local/ai06-synthetic-v02/ownership.json
python scripts/intelligence/install-built-wheel.py --dist .local/ai06-v02-dist
python -m build --no-isolation --outdir .local/ai06-v02-cloud-dist intelligence/integrations
python scripts/intelligence/install-cloud-wheel.py --dist .local/ai06-v02-cloud-dist
python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests/apps/servicedesk/generation -q
python -I scripts/apps/servicedesk/ai06/run-ai06-synthetic-v02.py --installed --output .local/ai06-synthetic-v02/wheel.json
python -I scripts/apps/servicedesk/ai06/verify-ai06-synthetic-v02.py --installed --compare-source-wheel .local/ai06-synthetic-v02/source.json .local/ai06-synthetic-v02/wheel.json
```

For installed execution set `HDPI_PROFILE_ROOT` to the absolute external directory
`intelligence/src/apps/servicedesk/intelligence/profiles`. The optional cloud wheel
is separate; app fixtures and profiles are never embedded in the generic wheel.
The generator accepts `--baseline`, `--plan`, `--facts`, `--templates`, `--seed`,
`--output`, `--curation`, `--manifest` and `--smoke` for reviewed proposals. Input
overwrite and conflicting output destinations are rejected. A changed seed or
quota cannot silently replace this release. Inspect `--help` for exact arguments.

The new workflow runs ownership before Ubuntu/Windows validation, then independently
replays both OS reports. Existing runtime and boundary gates remain enabled.
Reports bind HEAD: regenerate them after the last commit. Only sanitized JSON
summaries are retained for seven days in CI. Local XML/logs stay ignored.

## Optional cloud run

No keys or genuine dataset egress approvals were provided during implementation.
Keep API secrets in private external files using existing unprefixed environment
names (`GEMINI_API_KEY`, `GROQ_API_KEY`, etc.). Never commit keys or approvals.
Copy `cloud-release-approval.pending.example.json` outside the checkout and obtain
an actual owner's decision; replacing placeholders is not authorization. The
existing provider registry's route-specific approval is independently required.
Set `CLOUD_ENVIRONMENT=isolated-synthetic` only in the approved isolated
environment. Use the exact required variable names documented by the existing
API-first operations guide and runtime configuration.

```text
python scripts/apps/servicedesk/ai06/run-ai06-cloud-v02.py --provider gemini --output .local/ai06-synthetic-v02/gemini-blocked.json
```

That command intentionally exits 2 with zero provider attempts. Only after valid
approval and keys, add `--allow-external-egress --data-class SYNTHETIC_DEV_ONLY`,
`--approval-file <external-path> --approval-sha256 <raw-sha256>`, and explicit
`--max-requests`, `--max-input-tokens`, `--max-output-tokens`, `--max-cost 0` within
approved budgets. Default `--scope smoke` plans 30 cases; `--scope full` plans 400
and needs separate matching consent. Run each provider independently, then:

```text
python scripts/apps/servicedesk/ai06/compare-ai06-cloud-runs.py .local/ai06-synthetic-v02/gemini.json .local/ai06-synthetic-v02/groq.json --output .local/ai06-synthetic-v02/cloud-matrix.json
```

The matrix is untrusted operator receipt consistency evidence, not remote
attestation, provider selection or official quality approval. Keep partial quota
and timeout outcomes, unknown token usage and unreconciled cost visible.

## Review and promotion gates

DE/AIE/QA must review coverage, symbolic facts, provisional labels, duplicate
relationships and unsafe selections. Architecture review must independently
inspect Core → HPDI → application boundaries and Java/Python inventory changes.
Security/Data Owner must approve rights and provider-specific egress. Independent
reviewers must supply genuine labels, thresholds, locked HOLDOUT and GO/NO-GO.
No generated PENDING stamp counts as a signature. Do not merge or deploy
automatically. See the design document for immutability and rollback rules.
