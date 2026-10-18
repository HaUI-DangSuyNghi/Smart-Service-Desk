# Application boundary

`servicedesk` implements ticket persistence, scoped APIs and an application-owned
Flowable process through Core service contracts. See `docs/servicedesk-ticket-foundation.md`
and `contracts/openapi/servicedesk.yaml` at repository root. UI and AI integration
are outside the ticket foundation scope.

Applications own business entities and rules. The strict target is Applications
-> H-P-D-I public APIs -> Core public contracts. Existing direct Core calls are
temporary BND-00 debt, recorded exactly in `docs/architecture/boundary-debt.json`;
new direct calls are blocked. See `docs/architecture/ADR-005-dxos-three-tier-boundary.md`.
Application production SQL must not access Flowable, Keycloak or Core private
tables. Existing statistics reads are scheduled for BND-01 removal.
