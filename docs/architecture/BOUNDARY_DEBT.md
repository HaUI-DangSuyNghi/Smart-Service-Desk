# Strict boundary debt: closed

Recovery status: BND-00 through BND-07 implemented. The authoritative
[boundary-debt.json](boundary-debt.json) is in `strict` mode with **zero source
exceptions and zero Java dependency exceptions**. Strict mode rejects new waivers.
The original baseline inventoried 51 source violations and 35 compiled edges;
[RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) records their removal by phase.

## Enforced ownership

- Applications call public HPDI capabilities and own their REST projections,
  persistence, BPMN, profile/taxonomy and business policy.
- HPDI public APIs are Core-independent. Implementations consume Core public
  contracts; private Core implementations and application imports are forbidden.
- Core has no upper-tier dependency. Only workflow internals depend on Flowable.
  No lower tier imports assembly. HPDI and Python runtime contain no app literals.
- Production app SQL reads app-owned tables. Historical V5's cross-owner FK is
  removed by V6; historical V1-V5 remain unchanged. Room Request V7 has no cross-owner FK.
- Generic wheels carry runtime/contracts, with explicit external profile roots.
  Public app OpenAPI schemas do not reference Core DTOs.

The source SQL scanner checks literal SQL, CTEs, qualified tables and effective
forward FK changes; it is not a general dynamic-SQL evaluator. App table prefixes
are part of the ownership convention and are discovered from application source,
resource and migration directories. Reserved platform/engine prefixes cannot be
claimed by naming an application after them. Compiled Java checks also catch fully
qualified and signature dependencies that bypass source imports.

## Gates

```sh
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
mvn -B -f backend/pom.xml clean verify
mvn -B -f backend/pom.xml -Pcore-only clean verify
mvn -B -f backend/pom.xml -Psecond-domain -Dtest=RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest clean verify
```

Database-backed tests require disposable `file_storage` and `workflow_approval`
PostgreSQL databases, through `FILE_TEST_*` and `WORKFLOW_TEST_*` settings. Use
Linux/JDK 17 for filesystem permission/symlink regression. `scripts/test-file.ps1`
provisions isolated databases and rejects skipped tests; it accepts `-MavenProfile`
and `-Tests`. Build its `dx-platform-build:0.1.0` image from the repository root.

CI's Tier boundaries workflow enforces source/bytecode rules, Core-only and
second-domain isolation, and real second-domain Java/Python reuse. Existing
functional and security acceptance workflows remain active. Repository branch
protection is configured externally; a local run does not assert remote CI status.

The diagnostic `--inventory` and `-Dboundary.inventory=true` options never update
or waive the strict baseline. Do not change enforcement to recovery mode to hide
new dependencies. Define a public capability or fix ownership instead.

## Reuse proof

Room Request is a small architecture proof using Human, Process, Data and
Intelligence. It contributes its own process, migration, public representations
and profile. Its addition changes **zero Core production files, zero HPDI
production files and zero hdpi runtime files** relative to the completed
composition baseline. The second-domain profile excludes Service Desk code,
resources and migrations; its real Python test supplies only the Room Request
profile. The default Service Desk deployment keeps the Service Desk profile root.
This proof does not represent a new Room Request product roadmap.

See [STRICT_BOUNDARY_OPERATIONS.md](STRICT_BOUNDARY_OPERATIONS.md) for runtime
configuration and [REFACTOR_VALIDATION.md](REFACTOR_VALIDATION.md) for evidence.
