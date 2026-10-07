# Intelligence (reserved)

Owner: AI member. Future tier 2 Intelligence code; no model, framework, runtime or separate service is initialized.

During the Core phase, work in `backend/src/main/java/vn/haui/dx/core/data/`, `audit/`, migrations and Core tests. Create the `core/file/` package when real File I/O is implemented.

Intelligence must consume authorized Core data without taking ownership of metadata, files or audit. Decide whether a separate service is justified when its implementation begins.

## AI-01 / N1

The current owner instruction requires team review before direct DX core changes.
N1 therefore delivers a [provenance contract](docs/data-provenance-contract.md),
external acceptance probes and an [overall report](docs/AI_N1_ACCEPTANCE_REPORT.md).
The contract is a draft; team review and the remaining Core hardening are pending.
No Intelligence runtime or framework is initialized.

Run from the repository root with the existing local baseline running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File intelligence/tests/metadata-provenance.ps1
$proof = Get-Content .local/ai-n1/metadata-provenance.json -Raw | ConvertFrom-Json
Get-Content intelligence/tests/check-metadata-audit.sql -Raw |
  docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1 `
    -v "metadata_id=$($proof.record.id)" -v "owner=$($proof.record.owner)" `
    -v "workspace=$($proof.record.workspace)" -v "request_id=$($proof.record.requestId)"
if ($LASTEXITCODE -ne 0) { throw 'AI-N1 audit check failed' }
```

The API probe uses the existing synthetic demo accounts and `.env` password without
printing credentials. It creates one synthetic metadata record via the public API
per successful run and preserves it for audit inspection; it does not delete data
or change Core source, schema, realm or API contracts. Evidence is saved under the
Git-ignored `.local/ai-n1/` directory without tokens/passwords. The SQL probe is a
read-only acceptance inspection, not an Intelligence data-access implementation.
