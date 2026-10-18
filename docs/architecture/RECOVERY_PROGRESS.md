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
