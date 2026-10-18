# BND-00 and hdpi rename: local evidence

Date: 2026-10-18. Base: `develop@728cdc3`.
Branch: `refactor/bnd-00-architecture-baseline`.

## Implemented

- Exact source-debt and compiled dependency inventories with owner, phase,
  occurrence count and expiry. No wildcard exceptions or bulk legacy merge.
- Source and ArchUnit gates in the dedicated Tier boundaries workflow.
- ADR, recovery ownership/debt guide and PR boundary checklist.
- Runtime import package renamed from `smart_service_intelligence` to `hdpi`,
  including test imports, mock targets, packaging resource destination, Docker
  entrypoint, CLI documentation, CI and acceptance scripts. Python runtime
  source files otherwise remain byte-identical. Distribution metadata stays
  `smart-service-intelligence==0.3.0`.

## Verification

| Check | Local result |
| --- | --- |
| Source boundary enforcement | PASS: 51 exact inventory entries |
| Source mutation/ratchet suite | PASS: 24 tests |
| Java compiled dependency enforcement | PASS: 35 exact class dependency edges |
| Manual Java mutation with fully qualified Core field and no import | Rejected as new debt; temporary source removed and final clean build rerun |
| Maven verify | PASS: 138 tests discovered, 62 executed, 76 skipped |
| Python source suite using locked dependencies | PASS: 265 tests |
| Build sdist and wheel with locked build tools | PASS |
| Installed wheel verification under `python -I` | PASS: triage, resources, retrieval, scope, provenance and citations |
| Full suite against installed wheel under `python -I` | PASS: 265 tests |
| Foundation/profile validation | PASS, including 145 negative cases and immutable history |
| Whitespace / historical migrations / public contracts | Checked before commit |

The local Java runtime is JDK 22 compiling for release 17. Architecture CI uses
JDK 17. Docker Desktop's Linux engine was unavailable, and the 76 database-backed
Java tests require explicit PostgreSQL test configuration. Real Core HTTP,
Keycloak/gateway E2E and Docker image builds were not run locally. Existing
functional CI retains those acceptance checks; local unit results do not claim
they passed.

BND-01 through BND-07 are not implemented by this branch. No runtime transport
move, SQL refactor, profile extraction, historical migration rewrite, frontend
restructure or second-domain product work is included. No branch, tag or PR has
been published remotely by this task.
