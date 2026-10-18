# Three-tier refactor validation

Date: 2026-10-18. Baseline: `c16598d` (AI-03 closeout).
Branch: `refactor/dxos-three-tier-boundary`. Implementation follows ARC-00 through
ARC-09, with a separate TypeScript conversion commit requested during execution.

## Local results

| Check | Result | Evidence scope |
| --- | --- | --- |
| Full backend PostgreSQL 16.6 regression | 100 passed; zero failures/errors/skips | real Core, H-P-D-I, Service Desk and Room Request; transactions, auth, lifecycle, statistics, migration and ArchUnit |
| Standalone Core (`core-only`) | 68 passed; zero skips | HPDI/apps excluded from compilation; Core runtime starts with test-only probes; final jar inspected without upper-tier classes |
| Second domain (`second-domain`) | 13 passed; zero skips | Service Desk classes/resources/table absent; H/P/D/I contract tests and Room Request flow use existing implementations |
| Python source suite | 193 passed | triage, retrieval, contracts, auth, provenance, citations, private endpoint and external second profile |
| Installed wheel suite | 193 passed with isolated Python | generic wheel contains schemas without app profiles; source imports disabled |
| Foundation/immutable profile validation | passed, including 145 negative cases | original authored fixtures and historical profile hashes preserved |
| Source boundary mutation tests | 8 passed | forbidden Core imports/SQL, private adapters, Python domain branch and frontend static/dynamic reverse imports rejected |
| TypeScript frontend | strict typecheck, ESLint, 3 tests, full build and standalone platform build passed | all frontend source/tests use `.ts`; type-only app imports are rejected from platform |
| OpenAPI | Core/HPDI/Service Desk/Room Request checks passed | shared Tier-2 references resolve locally; concrete route matching and missing-field rejection covered |
| Historical migration/profile integrity | passed | V1-V5 unchanged; original Service Desk locked digests unchanged |
| BPMN ownership-path compatibility | passed | unchanged content keeps definition ID; existing instance can claim/complete after registry restart |

## Real HTTP integration

`scripts/test-tier-reuse.ps1` builds an isolated `dx-tier-reuse` Compose project
with real Keycloak, APISIX, PostgreSQL, Core/HPDI/apps and the private Python
runtime. It creates only independent synthetic data and stops its services after
the checks. Local execution passed:

- Knowledge authorization through H-P-D-I with exact Core metadata/file/checksum provenance.
- Human actor derivation, app-owned Room Request persistence and process correlation.
- Real Java -> authenticated private Python -> rules triage with the distinct Room Request profile.
- Shared inbox, claim, complete and timeline against the real workflow engine.
- Unauthenticated and cross-workspace denial.
- Standard Core smoke, including file/metadata links, audit and both sample approval workflows.
- Existing Service Desk HTTP acceptance: Keycloak -> gateway -> ticket -> workflow -> audit, rework, confirmation and concurrent duplicate rejection.
- Captured Core, HPDI, Room Request and Service Desk responses validated against their OpenAPI contracts.

The normal local Compose project started healthy. Its persisted Keycloak realm
does not accept the current `.env` demo password, so the default smoke login
failed before business operations. No password, realm or existing data was reset.
The full smoke and application acceptance were instead run on the isolated
project with matching generated identity configuration.

Safe summaries and synthetic captures are under ignored `.local/`; no credentials,
JWTs, private keys, logs or generated build artifacts are committed.

## Reproduce

```powershell
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
./scripts/test-file.ps1
./scripts/test-file.ps1 -MavenProfile core-only
./scripts/test-file.ps1 -MavenProfile second-domain -Tests 'RoomRequestIntegrationTest,TierBoundaryTest,CoreProcessCapabilitiesTest,CoreKnowledgeCatalogTest,TrustedIntelligenceCapabilityTest,WorkspaceActivityJournalTest'
./scripts/test-tier-reuse.ps1 -Python <python-with-scripts/acceptance/requirements.txt>
```

Use Intelligence's locked environment for `python -m pytest intelligence/tests -q`,
foundation validation and wheel checks. Inside `frontend/`, run `npm ci`,
`npm run typecheck`, `npm run lint`, `npm test`, `npm run build` and
`npm run build:platform` with Node 24. Proof Maven profiles use disposable databases;
they are not migration/upgrade configurations for an existing application database.

## Review/CI limits

GitHub workflows now run strict source/ArchUnit/frontend checks, Core and second
domain isolation proofs, installed Python regressions and real three-tier HTTP
integration. These are committed CI definitions, not a claim that new remote CI
has executed. No branch was pushed and no PR/team approval was manufactured.
ADR-005 records the repository owner's implementation decision; team review
remains an integration gate.

ARC-00 documentation, ARC-01 guards, ARC-02 Process, ARC-03 Human, ARC-04 Data,
ARC-05 Intelligence, ARC-06 Service Desk, ARC-07 frontend, ARC-08 reuse proof,
the requested TypeScript conversion, and ARC-09 final cleanup are separate commits.
All boundary debt records are closed and the migration whitelist/loader are removed.
