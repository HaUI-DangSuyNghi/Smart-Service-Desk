# Boundary debt register

Created 2026-10-18 at baseline c16598d. Owners denote repository roles, not
claims of team approval. No unregistered exceptions may enter production code.

| ID | From -> To | Why temporary | Replacement seam | Owner | Removal | Status |
| --- | --- | --- | --- | --- | --- | --- |
| BD-01 | TicketService -> workflow/identity/audit | baseline integration | ProcessCapability/HumanContext/ActivityJournal | Backend | ARC-06 | CLOSED |
| BD-02 | TicketRepository -> engine/Core SQL | baseline statistics | ProcessSnapshot | Backend | ARC-04 | CLOSED |
| BD-03 | Ticket -> Core ProcessRef | baseline DTO | ProcessHandle | Process | ARC-02 | CLOSED |
| BD-04 | Core -> approval business policies | baseline probes | sample app contributions | Process | ARC-02 | CLOSED |
| BD-05 | shared BPMN -> app processes | default deployment path | explicit resource ownership | Process | ARC-02 | CLOSED |
| BD-06 | V5 -> global migration stream | applied history immutable | documented ownership; future scoped paths | Backend | ARC-09 | OPEN |
| BD-07 | T3 -> missing T2 | capabilities not extracted | hpdi public APIs | Backend | ARC-06 | CLOSED |
| BD-08 | frontend -> absent boundaries | framework not initialized | platform/apps + import guard | Frontend | ARC-07 | OPEN |
| BD-09 | generic runtime -> app package name | AI-03 baseline | hdpi_intelligence | Intelligence | ARC-05 | CLOSED |
| BD-10 | generic wheel -> app profile | bundled config | external ProfileRegistry root | Intelligence | ARC-05 | CLOSED |
| BD-11 | generic evaluator -> app pins | authored regression | explicit evaluation configuration | Intelligence | ARC-05 | CLOSED |
| BD-12 | CI -> missing architecture tests | baseline | executable cross-language guards | Backend | ARC-01 | CLOSED |
| BD-13 | reuse -> no second app | baseline | executable second-domain proof | Backend | ARC-08 | OPEN |
| BD-14 | docs -> mixed dependency arrows | historical baseline | ADR-005 + current boundary map | Architecture | ARC-09 | OPEN |
| BD-15 | raw Core APIs -> capability terminology | historical descriptions | semantic T2 contracts | Architecture | ARC-09 | OPEN |

Migration whitelist is an exact set of source-to-Core imports captured by the
architecture guard. It may only shrink. No Core-internal imports are permitted.
Historical migration/fixture paths are compatibility ownership records, not
permission for apps to access internal tables.
