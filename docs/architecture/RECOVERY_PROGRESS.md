# Strict boundary recovery progress

Continuation branch: `refactor/bnd-01-07-strict-boundary-recovery`, based on the
committed BND-00 baseline. The repository owner requested completion of all
remaining phases. Local phase commits are kept separate for review and selective
integration; develop and the legacy reference branch are not merged or rewritten.

## BND-01: workflow data isolation

- TicketRepository reads only `servicedesk_*` tables. TicketStatisticsService
  maps authorized HPDI ProcessObservations to the existing ticket counts.
- Core owns bounded workflow snapshots. Application-contributed observer roles
  preserve statistics visibility without embedding Service Desk role semantics
  in Core. Workspace and owner/business-key checks remain enforced.
- V6 removes the cross-owner FK, retaining the opaque process identifier.
  V1-V5 remain unchanged. The source gate evaluates forward constraint removal.
- Engine corruption/atomicity test probes now live under Core workflow test
  support, preserving the Service Desk characterization assertions.
- Validation: 139 Java tests passed on Linux/JDK 17 with PostgreSQL 16.6;
  failures=0, errors=0, skipped=0. Source guard/ratchet suite: 25 tests passed.

## BND-02: Process capability and application representations

- Service Desk uses HPDI ProcessCapability, contributions and observations.
  TicketProcessView and TicketProgress own the existing public wire shape;
  application OpenAPI process/page schemas no longer point at Core DTOs.
- Apps own all three existing BPMN resources and their process policies.
  Core reader/observer authorization is driven by contributed role sets.
- Content-aware deployment retains identical process definition IDs when only
  resource ownership paths change. A running-instance/restart test proves it.
- Added the reusable WorkInbox and ProcessTimeline contracts and adapters.
- Validation: 142 Java tests passed with PostgreSQL on Linux/JDK 17,
  failures=0, errors=0, skipped=0; compiled debt was reduced, without new edges.

## BND-03: Java Intelligence capability

- Service Desk calls IntelligenceCapability using persisted ticket input and an
  app-owned immutable profile identity. It owns the public TicketTriageView.
- HPDI owns HTTP transport/configuration, bearer credentials, bounded request and
  response handling, absolute deadlines, strict JSON parsing and contract checks.
  The existing error/status and DEGRADED behavior remains intact.
- HumanContext derives the verified actor/workspace. The capability constructs
  correlation and independently trusted context before invoking the transport.
  Only the current flat private wire protocol remains until BND-04 updates both
  Java and Python together to use the versioned trusted envelope.
- Existing 142 Java regression tests passed with PostgreSQL, without skips;
  additional arbitrary-profile and denied/mismatched-actor tests cover the new
  trusted adapter. Transport and schema negative tests moved with their owner.

## BND-04: external application profiles and generic Python runtime

- Service Desk owns its registry, immutable profiles, taxonomy and policies under
  `apps/servicedesk/intelligence`. Profile bytes and historical checks are retained.
- The `hdpi` wheel contains generic runtime/contracts only. Explicit profile root
  configuration is required. Build logic rejects stale bundled profile content.
- Java and Python use the same private envelope: request, immutable profile
  version, and independently trusted workspace/subject. HTTP hardening is retained.
- CLI/evaluation inputs and gates are explicit. Compose mounts profiles read-only;
  CI verifies source, installed wheel, image configuration and immutable history.
- Validation: 272 source tests and 272 installed-wheel tests passed, including
  external `fake-domain@1.0`; foundation validation and wheel isolation passed.

## BND-05: Human/Data alignment and remaining Core dependencies

- Service Desk uses HumanContext and a transaction-bound ActivityJournal for
  identity, workspace context and safe audit references. Public pagination/errors
  are HPDI contracts. Apps now have zero Core imports or bytecode dependencies.
- KnowledgeCatalog composes authorized metadata/file access and verifies workspace,
  immutable version, content length, checksum and supported media types before
  returning provenance-bearing knowledge. Existing retrieval auth-before-scoring
  and citation validation remain intact.
- All migration exceptions are closed: source inventory=0, Java edges=0.
- Validation: 147 Java tests passed with PostgreSQL on Linux/JDK 17;
  failures=0, errors=0, skipped=0, including trusted-context and provenance tests.

## BND-06: explicit assembly and standalone Core

- DxOsApplication owns bootstrap under `vn.haui.dx.assembly`; workflow readiness
  validation lives with Core's workflow runtime. Production artifact/image/service
  naming is `dx-platform` / `platform`; public API paths and JWT audience remain
  compatible. Docker, gateway routing, acceptance scripts and CI use that name.
- The Core-only Maven profile excludes application/HPDI/assembly classes, app
  migrations and app resources. Test-only process contributions exercise the
  standalone engine without bringing application production code into Core.
- The isolation test checks absent upper-tier classes, Service Desk table and
  Service Desk BPMN. Resource exclusions are applied once in the base resource
  declaration to avoid Maven profile list-merging duplicates.
- Assembly dependency and application OpenAPI DTO guards extend the source and
  bytecode checks. The generic Python image verifies externally mounted profiles.
- Validation: assembled Java regression passed; standalone Core passed 69 tests
  with PostgreSQL, failures=0, errors=0, skipped=0. No migration exceptions remain.

## BND-07: second-domain proof and strict closure

- Room Request owns a minimal request model, REST projections, process contribution,
  BPMN, V7 migration and external Intelligence profile. It uses the existing
  Human, Process, Data and Intelligence public capabilities without modifying
  Core production, HPDI production or generic Python runtime source.
- The `second-domain` profile excludes Service Desk classes/resources/migrations.
  Tests assert absent Service Desk classes/table, exact knowledge provenance,
  shared inbox/process completion, trusted identity and visibility enforcement.
- The real second-domain HTTP test uses the unchanged generic Python image with
  only the Room Request profile; it returns BOOKING/NORMAL through the actual
  application -> HPDI -> Python flow while Service Desk is absent.
- Strict mode rejects every migration exception. CI runs isolated Core, isolated
  second-domain and real second-domain Python gates. Application/HPDI OpenAPI
  contracts and cross-document local validation cover the public projections.
- Validation: 151 assembled Java/PostgreSQL tests, 11 isolated second-domain tests,
  1 real second-domain Java/Python test and 29 source mutation tests passed, all
  without failures/errors/skips. Real Core retrieval acceptance also passed all
  eight authorization/provenance scenarios through Keycloak/APISIX.
