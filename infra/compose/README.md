# Intelligence Compose overlays

Run commands from the repository root, with `-f compose.yaml` first. Docker Compose
resolves all relative build/mount paths against that first file's directory.

| Overlay | Profile | Purpose |
| --- | --- | --- |
| `infra/compose/compose.triage.yaml` | `intelligence` | Existing private AI02 triage |
| `infra/compose/compose.generation.yaml` | `assistance-reference` | Deterministic synthetic reference |
| `infra/compose/compose.generation.yaml` + `infra/compose/compose.cloud.yaml` | `assistance-cloud-dev` | Explicit approved synthetic Gemini/Groq |

Example reference command:

```powershell
docker compose -f compose.yaml -f infra/compose/compose.generation.yaml --profile assistance-reference up -d --build --wait
```

For cloud activation, environment/approval files and rollback, follow
[the operator runbook](../../intelligence/docs/task/AI06/AI06_API_FIRST_OPERATIONS.md).
Cloud credentials and receipts are never committed here.
