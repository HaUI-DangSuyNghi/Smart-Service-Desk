# Tier 1 team execution plan

## Baseline and capacity

Relative days N1-N4 are the initial Core target inside the existing 14-day schedule, NOT four days added from today. Actual contest start/deadline must be confirmed by the team. Budget: 6 hours/person/day = 4 task hours + 1 shared review/integration hour + 1 contingency hour. N1-N4 totals 72 person-hours: 48 implementation, 12 review/integration, 12 contingency. No business UI or Intelligence work during this gate.

The code written by the coding assistant is a starting artifact, not 48 hours of completed team work. Tasks below are remaining human validation/hardening/extension, with baseline review included. Do not mark them done solely because a file exists.

BE = Backend member; FE = Frontend member (infra/identity now); AI = AI member (data/file/audit now). These are team members, not spawned agents.

## Shared file ownership

| File/boundary | Owner | Required reviewer |
| --- | --- | --- |
| `backend/pom.xml`, `backend/Dockerfile`, assembly, workflow definitions | BE | FE |
| `compose.yaml`, `infra/**` (including Keycloak realm template), `scripts/setup.ps1`, `scripts/start.ps1`, README | FE | BE |
| `contracts/openapi/core.yaml` | BE integrates changes | FE + AI for their endpoints |
| `data`, `audit`, migration SQL, future `file` package | AI | BE |
| `scripts/smoke.ps1` | FE | AI |
| Requirements mapping, version/license inventory | FE aggregates | Every owner signs their entries |

Only owner edits shared manifest during a work block; others hand over a patch/PR. API changes require consumer review before merge. Append new Flyway migrations after shared DB initialization, never rewrite an applied migration.

Java module paths now live under `backend/src/main/java/vn/haui/dx/core/`; migration paths under `backend/src/main/resources/db/migration/`. FE's `frontend/` and AI's `intelligence/` contain scope README files only; they do not replace current Core-phase responsibilities. Original source documents are preserved under `docs/references/`.

## Assignable work packages

Every row is four focused hours. Daily review and contingency are separately budgeted above.

| ID/day, objective | Lead / review | Depends on | Owned files | Contract / output | Done and verification |
| --- | --- | --- | --- | --- | --- |
| BE-01 N1: ratify Core boundaries and API | BE / AI | Official brief + baseline | `docs/core-architecture.md`, `contracts/openapi/core.yaml`, `backend/pom.xml` | Versioned API, error/status and ownership agreement | All 3 review; no app imports; required request/response fields match code |
| FE-01 N1: reproducible local environment | FE / BE | Baseline | Compose, infra, setup/start, README | Loopback ports, secret generation, IdP issuer/audience | Run start on FE machine; all 4 healthy; no secrets tracked; confirm contest deadline |
| AI-01 N1: data/provenance scope | AI / BE | BE-01 contract discussion | data module, SQL, `docs/requirements-mapping.md` | Immutable metadata, owner/workspace/source/rights/version | Create/read + invalid input + foreign workspace tests; confirm checksum semantics |
| BE-02 N2: engine and transaction verification | BE / AI | BE-01, FE-01 | workflow, BPMN, workflow tests | Two keys, unique business key, atomic audit | Start/complete/history; duplicate start leaves one scope and one engine instance; rollback test |
| FE-02 N2: identity and gateway negative cases | FE / BE | FE-01, BE-01 | identity, realm setup, smoke | Issuer/audience/expiry, deny by default | Missing/wrong/expired JWT 401; direct backend request still checked; reader write 403 |
| AI-02 N2: audit and File contract | AI / FE | AI-01, BE-02 | audit, File contract, new migration if needed | Audit query design + resource authorization contract | Compare committed audit against actions; specify cross-workspace link denial and failed-write cleanup |
| BE-03 N3: task actor/claim policy | BE / FE | BE-02, FE-02 | workflow + tests + OpenAPI | Independent reviewer policy; atomic claim API if required by agreed scope | Concurrent claim/complete has one winner; non-assignee denied; preserve baseline probes |
| FE-03 N3: reproducibility/security upgrade spike | FE / BE | FE-02 | inventory, Compose/Dockerfile proposal, smoke | CVE report, fixed image digests, tested supported version proposal | Timebox to 4h; validate upgraded combination in isolated branch; no untested mass upgrade |
| AI-03 N3: minimal private File implementation | AI / BE | AI-02 authorization contract | future file package, tests | <=5 MiB upload/download, generated storage key, metadata | Traversal/type/size/foreign resource tests; DB/filesystem failure cleanup; do not enable if incomplete |
| BE-04 N4: contract and Core acceptance | BE / AI | BE-03, AI-03 | OpenAPI, workflow tests, baseline status | Headless + reuse evidence, backlog classification | Run both probes without apps; no double transition; mark actual findings, not mock success |
| FE-04 N4: independent clean run | FE / AI | FE-03, BE-04 | README, smoke, verification evidence | Fresh environment instructions and startup timings | Build from source with empty project volumes under a separate Compose project; keep existing data; capture commands/results |
| AI-04 N4: data/file/audit acceptance | AI / BE | AI-03, BE-04 | fixtures, data/file/audit tests, status | Two-workspace security report and data provenance | No leaked metadata/content; DB audit actor/time/request IDs correct; declare residual backlog |

## Daily integration and critical path

- N1: 10-minute blocker check + 50-minute contract/demo. Gate: exact API, ownership and all services start. FE owns environment, BE owns assembly, AI owns schemas.
- N2: run `/me`, metadata create/read and first engine history through gateway. Gate: 401/403/404 work and no orphan process on failure.
- N3: integrate File only if authorization and failure behavior are complete; demonstrate second process and concurrent transition rejection. No frontend required.
- N4: independent clean build, complete smoke, sign baseline status with all three members. Gate is evidence, not percentage of files written.

Critical path: API/permissions agreement -> IdP/DB running -> JWT issuer/audience -> engine deploy + scoped persistence -> real gateway probes -> independent reproduction. AI data/audit and FE environment run alongside BE workflow, not as BE's extra tasks.

Blocker >60 minutes: report to all three, pair owner with reviewer, spend that day's reserve. If N2 slips: freeze optional work; prioritize identity + gateway + data + engine. If N4 slips: keep File contract/schema as explicit backlog, defer claim/delegate UI and polish, use reserve before touching N5 scope. Do not remove authorization, replace engine history with fake data, or extend the contest deadline silently. At 2-3 hours/person/day this plan is infeasible: reduce scope and re-estimate before commitment.

## Relationship to days 5-14

The official roadmap retains N5-7 H/P/D, N8-10 Intelligence and one app, N11 integration, N12 PoF, N13 release, N14 demo/submission. These remain future phases, NOT implemented here. At N4, evaluate File/security backlog before accepting upper-tier commitments. One owner per remaining Core issue; do not continue dumping all work on BE. Public repository, license decision and release/tag require team approval; this initialization does not publish or commit anything.

## Delivered now versus next

Assistant-created now: runnable source/config, two BPMN probes, metadata migration/API, JWT guards, transactional audit, File schema/contract, setup/start/smoke scripts, focused tests, OpenAPI and documentation. Actual execution outcomes are only in `core-baseline-status.md`.

Next for BE: richer task permissions/claim with concurrency tests, atomic rollback evidence, version upgrade compatibility. Next for FE: independent clean build, security inventory, supported images, Authorization Code + PKCE client when UI begins. Next for AI: real File I/O and resource ACLs, metadata version evolution and audit read/retention. No chatbot or model training is assigned in this phase.
