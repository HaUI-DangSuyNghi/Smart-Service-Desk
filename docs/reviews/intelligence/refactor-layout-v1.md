# Intelligence foundation layout refactor

Date: **08/10/2026, Asia/Saigon**. Branch: `refactor/intelligence-layout-v1`.
Baseline: `430416cf0accedb8f34e94a613583e14e8ad3fea`.
Status: structural refactor verified; N1 remains **READY FOR BE REVIEW — PROPOSED**.

## Result

Shared [capability contracts](../../../contracts/intelligence/v1/contract.md) now
live at repository level. The unchanged wire contract version remains `0.1.0`.
Intelligence owns the [Service Desk profile](../../../intelligence/profiles/service-desk/profile.yaml),
split into taxonomy, rules, priority policy and normalization. Business ownership
and policy semantics remain unchanged. The review rule references the sole policy
fallback instead of repeating it.

[Fixtures](../../../fixtures/intelligence/service-desk/tickets/manifest.json),
[architecture](../../architecture/intelligence/overview.md), decisions, governance,
historical [N1 review evidence](N1/FOUNDATION_REPORT.md) and executable utilities
are separated by ownership. Local planning material is archived under ignored
`.local/ai-n1/plans/`. The historical reports retain their original review/runtime
conclusions; moved paths and the original package commit reference are corrected.

## Verification

Local validator run: **2026-10-08T04:35:45+00:00** (**11:35:45 +07:00**).
Tools: Python `3.12.7`, PyYAML `6.0.1`, jsonschema `4.23.0`; no packages installed.

| Check | Result |
| --- | --- |
| `python scripts/intelligence/validate-foundation.py` | PASS: 4 Draft 2020-12 schemas, profile file/version pins, 5 categories, 16 priority cells, 12 annotated tickets, DEGRADED examples, exact-byte digests and 12 retrieval-gold associations |
| Invalid data | PASS: 145 cases rejected, including domain enums/constants/defaults, profile file/version drift, mixed taxonomy concerns, invalid rule references, duplicate IDs, policy/fallback changes, manifest/binding errors and duplicate JSON/YAML keys |
| Reuse | PASS: Room Request values, classifier triage and replacement search engine fit generic schemas; no engine executed |
| Baseline profile equivalence | PASS: reconstructing the previous mixed YAML from the split files yields equal categories/definitions/signals/exclusions/routing, every rule ID, conflict/fallback, versions/owner and all 16 matrix cells |
| Exact-byte preservation | PASS: SHA-256 and byte size equal baseline for all 4 schemas, normalization, 5 knowledge sources, ticket JSONL, retrieval gold and degraded fixture |
| Manifests | PASS: knowledge records differ only in `path`; ticket manifest differs only in `artifact_path`; rights/checksums/sizes/identity/source/version/bindings/ingestion state retained |
| Git snapshots | PASS: validator runs from the committed artifact snapshots for the preceding five refactor commits, independent of working-tree files |
| Markdown links and stale paths | PASS: repository local Markdown targets exist; no tracked references to the old artifact directories |
| Git whitespace | `git diff --check` and staged diff checks PASS for every refactor commit |
| Core acceptance utility | PowerShell parser and new repository-root resolution PASS; audit SQL bytes unchanged |
| Core scope | Baseline diff empty for backend, frontend, infra, Compose, Core OpenAPI, startup/smoke/setup/DB checks, Core architecture and baseline status |

The profile/move equivalence assertions and commit-snapshot checks use local
bookkeeping under ignored `.local/`; the artifact validator is the checked-in
repeatable command. Root `.gitattributes` retains LF for moved checksum-sensitive
assets so fresh checkouts preserve their bytes.

Core services, Maven, API/SQL acceptance and Intelligence engines were not rerun.
The refactor changes no Core behavior, migration or runtime dependency. Historic
Core acceptance evidence remains historic. BE/business ratification, separately
reviewed Core alignment and regression evidence remain the N1 completion gate.
