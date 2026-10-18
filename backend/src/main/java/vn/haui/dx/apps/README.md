# Application boundary

`servicedesk` implements ticket persistence, scoped APIs and an application-owned
process through H-P-D-I public capability contracts. See `docs/servicedesk-ticket-foundation.md`
and `contracts/openapi/servicedesk.yaml` at repository root. UI and AI integration
are outside the ticket foundation scope.

Applications own business entities, rules, BPMN, reporting and AI profiles. They
consume `vn.haui.dx.hpdi.*.api` only. Core never imports applications or H-P-D-I;
H-P-D-I never imports applications. Application SQL accesses app-owned tables
only. Architecture tests and the source guard enforce these rules without waivers.
