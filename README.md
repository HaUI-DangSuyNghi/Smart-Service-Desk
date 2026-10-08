# DX-Lab HaUI: Headless Core Baseline

Tier 1 foundation for DX-OS Open-Core. No frontend, ticket application, chatbot or model training is included. This is not the complete competition submission.

Development baseline: `develop`. Start task branches from `develop`, then open reviewed pull requests back into it. `main` is reserved for approved releases. Read `CONTRIBUTING.md` for the team's Git Flow, branch names and merge rules.

## Run locally (Windows PowerShell)

Prerequisites: Docker Desktop running Linux containers, Docker Compose v2 with `--wait`, PowerShell 5.1+. Internet is needed for the first image/dependency download. Java/Maven are provided by the build container; host Maven is not required.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
docker compose ps
```

Gateway: `http://localhost:9080`; health: `http://localhost:9080/health`; Keycloak: `http://localhost:8081`. Backend/database are not exposed on host ports. All published ports bind loopback. Keep ports 8081 and 9080 free; changing the IdP address also requires changing the JWT issuer, realm config and probes consistently.

`setup.ps1` generates random secrets in ignored `.env`, and an ignored `.local/dx-lab-realm.json`. It preserves existing `.env`; copying the blank `.env.example` requires filling all three values first. Never commit or share those generated files. Keycloak hashes imported passwords internally. Demo users: `alice` (reader/writer/reviewer in lab-a), `bob` (reader in lab-a), `mallory` (reader/writer/reviewer in lab-b). Their locally generated password is `DEMO_PASSWORD` in `.env`; admin uses `KC_ADMIN_PASSWORD`. No password is embedded in source. Use only synthetic data.

The probe client uses password grant strictly for local automated checks. It is not the future UI login design. Browser integration must use Authorization Code + PKCE. Keycloak `start-dev`, HTTP, shared DB role and older pinned compatibility versions are not production-ready. See the security/release gates in the architecture and inventory.

## Implemented

- Keycloak identity, protected `/api/core/v1/me`, backend JWT issuer/audience validation, role and workspace guards.
- APISIX standalone routing and rate limits, no public Admin API.
- PostgreSQL metadata create/read: source, usage rights, version, checksum, workspace, owner and timestamp.
- Real embedded Flowable start/tasks/complete/history; two distinct process keys: `document-review` and `equipment-approval`.
- Transactional audit for writes/transitions; server-generated request IDs; unique scoped business keys; replay protection.
- File schema/link contract and volume only. Upload/download, audit read/export and richer task assignment remain backlog.

## API and structure

```text
backend/                  Spring Boot build, Dockerfile and src/
  src/main/java/vn/haui/dx/
    CoreApplication.java  Assembly
    core/                 Identity, Workflow, Data, Audit
    apps/                 Future app boundary (README only)
frontend/                 FE's future UI boundary (README only)
intelligence/             Domain profiles and Intelligence documentation (no runtime)
contracts/intelligence/   Shared proposed triage/search contracts
fixtures/intelligence/    Profile-scoped synthetic/test fixtures
infra/                    Keycloak template, APISIX and PostgreSQL config
scripts/                  Setup, startup and integration checks
contracts/openapi/        Implemented Core API contract
docs/references/          Unmodified source PDFs and workbook
docs/                     Architecture, team plan and verification
compose.yaml              Run all services from the repository root
```

Backend owns assembly/workflow and API contracts. FE owns infrastructure and startup/smoke scripts during the Core phase. AI owns Core data/audit and future File I/O. The `core/file/` package is created only when that implementation begins. No business UI, Intelligence runtime service or Service Desk application has been initialized. The proposed Intelligence foundation contains shared contracts, a Service Desk profile and synthetic fixtures; see [Intelligence navigation](intelligence/README.md).

Keycloak's secret-free source template is `infra/keycloak/realm-template.json`. Setup injects local credentials into ignored `.local/dx-lab-realm.json`; do not edit generated files. Compose builds with `backend/` as its Docker context. For a manual Maven build use `mvn -f backend/pom.xml verify`; the Docker builder remains the default with no host Maven requirement.

`contracts/openapi/core.yaml` is the implemented API contract. Paths are prefixed `/api/core/v1`; resource routes include `/workspaces/{workspace}`. Get tasks, send their `key` as `expectedTaskKey`, then read engine history. The smoke script is the executable example and seeds synthetic metadata/processes through APIs; each run uses new business keys and retains its evidence in PostgreSQL.

`backend/src/main/java/vn/haui/dx` contains assembly and the `core/identity`, `core/data`, `core/workflow`, `core/audit` packages. `backend/src/main/resources/db/migration` contains owned schemas; `backend/src/main/resources/processes` contains BPMN. Tests mirror Core packages under `backend/src/test/java`. Future `apps/` code may depend on Core, never the reverse.

## Tests and operations

```powershell
docker compose config --quiet
docker compose build core
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
Get-Content scripts/check-db.sql -Raw | docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1
docker compose logs --tail 100 core keycloak gateway
docker compose stop
docker compose up -d --wait --wait-timeout 300
```

Build runs `mvn -B verify` (including focused unit tests); smoke calls real Keycloak, APISIX, PostgreSQL and Flowable. Do not confuse unit-test results with integration evidence. Stop/up preserves volumes. No destructive reset is automated. Realm imports do not overwrite an existing realm: after identity changes, migrate via Keycloak Admin API/console or use an explicitly separate test environment. Do not delete a shared volume to fix an import mismatch.

If Docker cannot connect, start Docker Desktop (`docker desktop start`). For 401, verify issuer `http://localhost:8081/realms/dx-lab`, audience `dx-core`, token expiry and internal JWKS connectivity. For 403, check both group and role. For startup failure inspect Compose logs and healthchecks; do not disable authentication. Source builds need Internet unless images and Maven cache have already been populated; offline reproduction is a separate acceptance test.

## Handover

- `docs/requirements-mapping.md`: exact source documents, official requirements versus choices, missing documents.
- `docs/core-architecture.md`: tiers, boundaries, authorization, File/Audit contracts and known limits.
- `docs/core-team-plan.md`: N1-N4 task assignments for all three members within the existing 14-day plan.
- `docs/core-baseline-status.md`: commands actually run, pass/fail and unverified items.
- `docs/dependency-licenses.md`: pinned dependencies, provenance, release/security gates.
- [Intelligence architecture](intelligence/docs/architecture/overview.md) and [N1 review brief](intelligence/docs/task/N1/BE_REVIEW_BRIEF.md): proposed contracts/profiles, ownership and remaining review gates.

Project license requires team approval before publication; upstream licenses do not license this team's code automatically. No repository publication, commit or release/tag is performed by setup.
